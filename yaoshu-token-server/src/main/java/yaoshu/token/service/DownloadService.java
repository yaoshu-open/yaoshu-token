package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.util.SpringUtils;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.SSRFProtectionCheck;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 下载服务  * <p>
 * 提供 HTTP 下载能力，含超时控制和 SSRF 防护。
 * Worker 模式需要配置 WorkerUrl，当前直接走源站下载 + SSRF 校验。
 */
@Slf4j
public class DownloadService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 执行下载请求      * <p>
     * 先做 SSRF 防护校验，再发送 GET 请求。
     *
     * @param url     下载 URL
     * @param reasons 下载原因（用于日志追踪）
     * @return HTTP 响应（byte[] body）
     */
    public static HttpResponse<byte[]> doDownloadRequest(String url, String... reasons) throws Exception {
        log.debug("downloading from url: {}, reasons: {}", url, String.join(", ", reasons));

        // SSRF 防护校验
        validateURL(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .header("User-Agent", "yaoshu-token/1.0")
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("download failed: HTTP " + response.statusCode() + " from " + url);
        }

        return response;
    }

    /**
     * 下载文件并返回字节数据，便捷方法
     */
    public static byte[] downloadBytes(String url) throws Exception {
        return doDownloadRequest(url).body();
    }

    /**
     * SSRF 防护校验      * <p>
     * 从 options 表读取 fetch_setting.* 配置，调用 SSRFProtectionCheck 校验。
     * 未配置时使用 Go 默认值（启用 SSRF 防护 + 白名单端口 80/443/8080/8443）。
     * 公开供其它需要代理上游 URL 的模块复用（如视频代理）。
     */
    public static void validateURL(String url) {
        FetchSetting fetchSetting = getFetchSetting();
        // enableSSRFProtection=false 时 SSRFProtectionCheck 内部直接放行
        SSRFProtectionCheck.validateURLWithFetchSetting(
                url,
                fetchSetting.enableSSRFProtection,
                fetchSetting.allowPrivateIp,
                fetchSetting.domainFilterMode,
                fetchSetting.ipFilterMode,
                fetchSetting.domainList,
                fetchSetting.ipList,
                fetchSetting.allowedPorts,
                fetchSetting.applyIPFilterForDomain
        );
    }

    /**
     * 获取 FetchSetting。      * <p>
     * Go 将 fetch_setting 各字段以 "fetch_setting.{json字段名}" 为 key 扁平存储于 options 表。
     * 此处逐项读取，未配置时回退 Go 的默认值（默认开启 SSRF 防护）。
     */
    private static FetchSetting getFetchSetting() {
        OptionService optionService = SpringUtils.getBean(OptionService.class);
        FetchSetting s = new FetchSetting();
        // Go 默认值：默认开启 SSRF 防护，禁止私有 IP，黑名单模式，对域名启用 IP 过滤
        s.enableSSRFProtection = readBool(optionService, "fetch_setting.enable_ssrf_protection", true);
        s.allowPrivateIp = readBool(optionService, "fetch_setting.allow_private_ip", false);
        s.domainFilterMode = readBool(optionService, "fetch_setting.domain_filter_mode", false);
        s.ipFilterMode = readBool(optionService, "fetch_setting.ip_filter_mode", false);
        s.applyIPFilterForDomain = readBool(optionService, "fetch_setting.apply_ip_filter_for_domain", true);
        s.domainList = readStringList(optionService, "fetch_setting.domain_list");
        s.ipList = readStringList(optionService, "fetch_setting.ip_list");
        List<String> ports = readStringList(optionService, "fetch_setting.allowed_ports");
        s.allowedPorts = ports.isEmpty() ? List.of("80", "443", "8080", "8443") : ports;
        return s;
    }

    /** 读取布尔配置，缺省返回默认值 */
    private static boolean readBool(OptionService optionService, String key, boolean defaultValue) {
        String value = optionService.getValue(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    /** 读取 JSON 数组字符串配置为 String 列表，缺省返回空列表 */
    private static List<String> readStringList(OptionService optionService, String key) {
        String value = optionService.getValue(key);
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        JSONArray array = Convert.toJSONArray(value);
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                String item = array.getString(i);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /** FetchSetting 配置载体（域名/IP 过滤模式 boolean：true=白名单） */
    private static final class FetchSetting {
        boolean enableSSRFProtection;
        boolean allowPrivateIp;
        boolean domainFilterMode;
        boolean ipFilterMode;
        List<String> domainList;
        List<String> ipList;
        List<String> allowedPorts;
        boolean applyIPFilterForDomain;
    }
}
