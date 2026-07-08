package yaoshu.token.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.service.OptionService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Uptime Kuma 状态代理控制器  * <p>
 * 认证：无。从配置项 {@code console_setting.uptime_kuma_groups}（JSON 数组）读取分组，
 * 对每个分组并发请求 Uptime Kuma 的 status-page / heartbeat 接口并聚合监控状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/uptime")
@RequiredArgsConstructor
public class UptimeKumaController {

    private static final String OPTION_KEY = "console_setting.uptime_kuma_groups";
    private static final String UPTIME_KEY_SUFFIX = "_24";
    private static final String API_STATUS_PATH = "/api/status-page/";
    private static final String API_HEARTBEAT_PATH = "/api/status-page/heartbeat/";
    // 单次 HTTP 请求超时
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    // 全部分组聚合的整体超时
    private static final long TOTAL_TIMEOUT_SECONDS = 30;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private final OptionService optionService;

    @GetMapping("/status")
    public Result<?> getStatus() {
        // 读取分组配置（JSON 数组字符串）
        String groupsJson = optionService.getValue(OPTION_KEY);
        List<JSONObject> groups = parseGroups(groupsJson);
        if (groups.isEmpty()) {
            return R.success(new ArrayList<>());
        }

        // 每个分组并发抓取，整体超时 30s
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>(groups.size());
        for (JSONObject group : groups) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchGroupData(group)));
        }

        List<Map<String, Object>> results = new ArrayList<>(groups.size());
        for (CompletableFuture<Map<String, Object>> future : futures) {
            try {
                results.add(future.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(emptyGroupResult(""));
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Uptime Kuma 分组抓取失败: {}", e.getMessage());
                results.add(emptyGroupResult(""));
            }
        }

        return R.success(results);
    }

    /** 解析分组配置 JSON 数组为 JSONObject 列表 */
    private List<JSONObject> parseGroups(String groupsJson) {
        List<JSONObject> groups = new ArrayList<>();
        if (groupsJson == null || groupsJson.isEmpty()) {
            return groups;
        }
        JSONArray array = Convert.toJSONArray(groupsJson);
        if (array == null) {
            return groups;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj != null) {
                groups.add(obj);
            }
        }
        return groups;
    }

    /**
     * 抓取单个分组数据。      * <p>
     * 并发请求 status-page（公开分组 + 监控列表）与 heartbeat（心跳 + 在线率），
     * 关联后聚合为该分组的监控列表。
     */
    private Map<String, Object> fetchGroupData(JSONObject groupConfig) {
        String url = groupConfig.getString("url");
        String slug = groupConfig.getString("slug");
        String categoryName = groupConfig.getString("categoryName");

        Map<String, Object> result = emptyGroupResult(categoryName);
        if (url == null || url.isEmpty() || slug == null || slug.isEmpty()) {
            return result;
        }

        String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

        // 并发请求状态页与心跳数据
        CompletableFuture<JSONObject> statusFuture = CompletableFuture.supplyAsync(
                () -> getAndDecode(baseUrl + API_STATUS_PATH + slug));
        CompletableFuture<JSONObject> heartbeatFuture = CompletableFuture.supplyAsync(
                () -> getAndDecode(baseUrl + API_HEARTBEAT_PATH + slug));

        JSONObject statusData;
        JSONObject heartbeatData;
        try {
            statusData = statusFuture.get();
            heartbeatData = heartbeatFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result;
        } catch (ExecutionException e) {
            return result;
        }
        if (statusData == null || heartbeatData == null) {
            return result;
        }

        // heartbeatList: {monitorId: [{status}]}；uptimeList: {monitorId_24: uptime}
        JSONObject heartbeatList = heartbeatData.getJSONObject("heartbeatList");
        JSONObject uptimeList = heartbeatData.getJSONObject("uptimeList");

        List<Map<String, Object>> monitors = new ArrayList<>();
        JSONArray publicGroupList = statusData.getJSONArray("publicGroupList");
        if (publicGroupList != null) {
            for (int i = 0; i < publicGroupList.size(); i++) {
                JSONObject pg = publicGroupList.getJSONObject(i);
                if (pg == null) {
                    continue;
                }
                JSONArray monitorList = pg.getJSONArray("monitorList");
                if (monitorList == null || monitorList.isEmpty()) {
                    continue;
                }
                String groupName = pg.getString("name");
                for (int j = 0; j < monitorList.size(); j++) {
                    JSONObject m = monitorList.getJSONObject(j);
                    if (m == null) {
                        continue;
                    }
                    Map<String, Object> monitor = new LinkedHashMap<>();
                    monitor.put("name", m.getString("name"));
                    monitor.put("group", groupName);
                    monitor.put("uptime", 0.0);
                    monitor.put("status", 0);

                    String monitorId = String.valueOf(m.getInteger("id"));
                    if (uptimeList != null) {
                        Double uptime = uptimeList.getDouble(monitorId + UPTIME_KEY_SUFFIX);
                        if (uptime != null) {
                            monitor.put("uptime", uptime);
                        }
                    }
                    if (heartbeatList != null) {
                        JSONArray heartbeats = heartbeatList.getJSONArray(monitorId);
                        if (heartbeats != null && !heartbeats.isEmpty()) {
                            JSONObject last = heartbeats.getJSONObject(0);
                            if (last != null && last.getInteger("status") != null) {
                                monitor.put("status", last.getInteger("status"));
                            }
                        }
                    }
                    monitors.add(monitor);
                }
            }
        }

        result.put("monitors", monitors);
        return result;
    }

    /** 发起 GET 请求并解析响应为 JSONObject，失败返回 null */
    private JSONObject getAndDecode(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return Convert.toJSONObject(response.body());
        } catch (Exception e) {
            log.debug("Uptime Kuma 请求失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** 构建空分组结果（保留分类名，监控列表为空） */
    private Map<String, Object> emptyGroupResult(String categoryName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryName", categoryName);
        result.put("monitors", new ArrayList<>());
        return result;
    }
}
