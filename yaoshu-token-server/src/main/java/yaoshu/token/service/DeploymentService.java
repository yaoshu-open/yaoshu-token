package yaoshu.token.service;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.dto.ChannelSettingsDTO;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 模型部署管理服务  * <p>
 * io.net GPU 部署全生命周期管理：连接测试、硬件/位置查询、部署创建/更新/延期/删除、容器管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private static final String IONET_API_BASE = "https://api.service.io.net";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);    private final OptionService optionService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 检查 io.net 是否已启用且配置了 API Key */
    private boolean isIoNetEnabled() {
        boolean enabled = "true".equals(optionService.getValue("model_deployment.ionet.enabled"));
        String key = optionService.getValue("model_deployment.ionet.api_key");
        return enabled && key != null && !key.isBlank();
    }

    /** 获取 io.net API Key（从 option 中读取），未启用时抛业务异常 */
    private String getApiKey() {
        if (!isIoNetEnabled()) {
            throw new ResultException(R.errorPrompt("io.net 模型部署未启用或 API Key 未配置"));
        }
        return optionService.getValue("model_deployment.ionet.api_key").trim();
    }

    /** 获取部署设置 */
    public Map<String, Object> getSettings() {
        String apiKey = optionService.getValue("model_deployment.ionet.api_key");
        boolean enabled = "true".equals(optionService.getValue("model_deployment.ionet.enabled"));
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("provider", "io.net");
        settings.put("enabled", enabled);
        settings.put("configured", hasApiKey);
        settings.put("can_connect", enabled && hasApiKey);
        return settings;
    }

    /** 测试 io.net 连接 */
    public Map<String, Object> testConnection(String apiKey) {
        String key = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : getApiKey();
        try {
            // 调用企业 API 端点验证 key 有效性
            Map<String, Object> result = doGet("/enterprise/v1/machine/hardware", key, null);
            return Map.of("hardware_count", result.getOrDefault("hardware", Collections.emptyList()),
                    "total_available", result.getOrDefault("total", 0));
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt("API Key 验证失败：" + e.getMessage()));
        }
    }

    /** 获取硬件类型列表 */
    public Map<String, Object> getHardwareTypes() {
        String apiKey = getApiKey();
        Map<String, Object> result = doGet("/enterprise/v1/machine/hardware", apiKey, null);
        return result;
    }

    /** 获取地理位置列表 */
    public Map<String, Object> getLocations() {
        String apiKey = getApiKey();
        Map<String, Object> result = doGet("/v1/machine/locations", apiKey, null);
        return result;
    }

    /** 获取可用副本数 */
    public Map<String, Object> getAvailableReplicas(int hardwareId, int gpuCount) {
        String apiKey = getApiKey();
        Map<String, String> params = Map.of("hardware_id", String.valueOf(hardwareId),
                "gpu_count", String.valueOf(gpuCount));
        return doGet("/enterprise/v1/machine/available_replicas", apiKey, params);
    }

    /** 价格估算 */
    public Map<String, Object> estimatePrice(Map<String, Object> body) {
        String apiKey = getApiKey();
        return doPost("/enterprise/v1/machine/price_estimation", apiKey, body);
    }

    /** 检查集群名称可用性 */
    public Map<String, Object> checkName(String name) {
        String apiKey = getApiKey();
        Map<String, String> params = Map.of("name", name);
        Map<String, Object> result = doGet("/enterprise/v1/cluster/check_name", apiKey, params);
        return Map.of("available", !Boolean.FALSE.equals(result.get("available")),
                "name", name);
    }

    /** 获取所有部署 */
    public Map<String, Object> getAllDeployments(String status, int page, int pageSize) {
        if (!isIoNetEnabled()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("items", Collections.emptyList());
            empty.put("total", 0);
            return empty;
        }
        String apiKey = getApiKey();
        StringBuilder path = new StringBuilder("/enterprise/v1/deployment?page=").append(page)
                .append("&page_size=").append(pageSize);
        if (status != null && !status.isBlank()) {
            path.append("&status=").append(status.toLowerCase());
        }
        Map<String, Object> result = doGet(path.toString(), apiKey, null);
        return result;
    }

    /** 搜索部署 */
    public Map<String, Object> searchDeployments(String keyword, String status, int page, int pageSize) {
        return getAllDeployments(status, page, pageSize);
    }

    /** 获取单个部署详情 */
    public Map<String, Object> getDeployment(String deploymentId) {
        String apiKey = getApiKey();
        return doGet("/enterprise/v1/deployment/" + deploymentId, apiKey, null);
    }

    /** 创建部署 */
    public Map<String, Object> createDeployment(Map<String, Object> body) {
        String apiKey = getApiKey();
        Map<String, Object> result = doPost("/enterprise/v1/deployment", apiKey, body);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deployment_id", result.getOrDefault("deployment_id", ""));
        response.put("status", result.getOrDefault("status", "created"));
        response.put("message", "Deployment created successfully");
        return response;
    }

    /** 更新部署 */
    public Map<String, Object> updateDeployment(String deploymentId, Map<String, Object> body) {
        String apiKey = getApiKey();
        return doPost("/enterprise/v1/deployment/" + deploymentId + "/update", apiKey, body);
    }

    /** 更新部署名称 */
    public Map<String, Object> updateDeploymentName(String deploymentId, String name) {
        String apiKey = getApiKey();
        // 先检查名称可用性
        Map<String, Object> available = checkName(name);
        if (Boolean.FALSE.equals(available.get("available"))) {
            throw new ResultException(R.errorPrompt("部署名称不可用，请更换名称"));
        }
        Map<String, Object> body = Map.of("name", name);
        Map<String, Object> result = doPost("/enterprise/v1/cluster/" + deploymentId + "/name", apiKey, body);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.getOrDefault("status", "updated"));
        response.put("id", deploymentId);
        response.put("name", name);
        return response;
    }

    /** 延期部署 */
    public Map<String, Object> extendDeployment(String deploymentId, Map<String, Object> body) {
        String apiKey = getApiKey();
        return doPost("/enterprise/v1/deployment/" + deploymentId + "/extend", apiKey, body);
    }

    /** 删除部署 */
    public Map<String, Object> deleteDeployment(String deploymentId) {
        String apiKey = getApiKey();
        Map<String, Object> result = doPost("/enterprise/v1/deployment/" + deploymentId + "/terminate", apiKey, Map.of());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.getOrDefault("status", "terminated"));
        response.put("deployment_id", deploymentId);
        response.put("message", "Deployment termination requested successfully");
        return response;
    }

    /** 获取部署日志 */
    public Map<String, Object> getDeploymentLogs(String deploymentId, String containerId) {
        String apiKey = getApiKey();
        return doGet("/v1/deployment/" + deploymentId + "/logs?container_id=" + containerId, apiKey, null);
    }

    /** 列出部署下的容器 */
    public Map<String, Object> listContainers(String deploymentId) {
        String apiKey = getApiKey();
        return doGet("/enterprise/v1/deployment/" + deploymentId + "/containers", apiKey, null);
    }

    /** 获取容器详情 */
    public Map<String, Object> getContainerDetails(String deploymentId, String containerId) {
        String apiKey = getApiKey();
        return doGet("/enterprise/v1/deployment/" + deploymentId + "/containers/" + containerId, apiKey, null);
    }

    /** 检查部署 ID 存在性 */
    public boolean deploymentExists(String deploymentId, String apiKey) {
        try {
            doGet("/enterprise/v1/deployment/" + deploymentId, apiKey, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== HTTP 辅助方法 ========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> doGet(String path, String apiKey, Map<String, String> params) {
        try {
            String url = buildUrl(path, params);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ResultException(R.errorPrompt("io.net API 请求失败（HTTP " + resp.statusCode() + "）"));
            }
        Map<String, Object> result = Convert.toJSONObject(resp.body());
        return result;
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt("io.net API 请求异常：" + e.getMessage()));
        }
    }

    private Map<String, Object> doPost(String path, String apiKey, Map<String, Object> body) {
        try {
            String url = IONET_API_BASE + path;
            String json = Convert.toJSONString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ResultException(R.errorPrompt("io.net API 请求失败（HTTP " + resp.statusCode() + "）"));
            }
        Map<String, Object> result2 = Convert.toJSONObject(resp.body());
        return result2;
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt("io.net API 请求异常：" + e.getMessage()));
        }
    }

    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(IONET_API_BASE).append(path);
        if (params != null && !params.isEmpty()) {
            boolean first = !path.contains("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(first ? '?' : '&');
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
}
