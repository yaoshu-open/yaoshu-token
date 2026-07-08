package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 错误处理服务  * <p>
 * 提供 Relay 错误处理、状态码映射、各渠道错误包装等通用方法。
 */
@Slf4j
public final class ErrorHandlingService {

    private ErrorHandlingService() {
    }

    /** 最大读取 body 的字节数 */
    private static final int MAX_ERROR_BODY_SIZE = 4096;

    // ======================== Relay 错误处理 ========================

    /**
     * 处理上游 API 错误响应，提取错误信息并返回 ApiError
     *
     * @param conn             HTTP 连接
     * @param showBodyWhenFail 失败时是否展示 body
     * @return API 错误对象（ApiError）
     */
    public static ApiError handleRelayError(HttpURLConnection conn, boolean showBodyWhenFail) {
        int statusCode;
        String errorBody;
        try {
            statusCode = conn.getResponseCode();
        } catch (Exception e) {
            return new ApiError(500, "channel_error", "failed to read response status: " + e.getMessage());
        }

        // 成功响应：不需要错误处理
        if (statusCode < 400) {
            return null;
        }

        // 读取错误 body
        errorBody = readErrorBody(conn);

        // 构造 ApiError
        String errorType = mapStatusCodeToType(statusCode);
        String errorMessage = extractErrorMessage(errorBody, statusCode);

        if (showBodyWhenFail && errorBody != null && !errorBody.isEmpty()) {
            // 截断过长的 body
            String displayBody = errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
            errorMessage = "upstream returned " + statusCode + ": " + displayBody;
        }

        // 日志
        if (statusCode >= 500) {
            log.error("Relay upstream 5xx: status={}, body={}", statusCode, truncateBody(errorBody));
        } else {
            log.debug("Relay upstream error: status={}, body={}", statusCode, truncateBody(errorBody));
        }

        return new ApiError(statusCode, errorType, errorMessage);
    }

    // ======================== 状态码映射 ========================

    /**
     * 重置状态码（应用渠道配置的状态码映射）      *
     * @param apiError             API 错误对象
     * @param statusCodeMappingStr 状态码映射 JSON 字符串
     *                             Go 格式：{"400":"200","429":"503"}
     */
    public static void applyStatusCodeMapping(ApiError apiError, String statusCodeMappingStr) {
        if (apiError == null || statusCodeMappingStr == null || statusCodeMappingStr.isEmpty()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapping = Convert.toJSONObject(statusCodeMappingStr);
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                int fromCode = Integer.parseInt(entry.getKey());
                if (apiError.statusCode == fromCode) {
                    int toCode = parseStatusCode(entry.getValue());
                    apiError.statusCode = toCode;
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse status code mapping: {}", e.getMessage());
        }
    }

    /**
     * applyStatusCodeMapping 重载 —— 接受 RelayException（带 Lombok setter 的状态码映射）
     */
    public static void applyStatusCodeMapping(yaoshu.token.pojo.dto.RelayException newApiError, String statusCodeMappingStr) {
        if (newApiError == null || statusCodeMappingStr == null || statusCodeMappingStr.isEmpty()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapping = Convert.toJSONObject(statusCodeMappingStr);
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                int fromCode = Integer.parseInt(entry.getKey());
                if (newApiError.getStatusCode() == fromCode) {
                    int toCode = parseStatusCode(entry.getValue());
                    newApiError.setStatusCode(toCode);
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse status code mapping: {}", e.getMessage());
        }
    }

    // ======================== 渠道错误包装 ========================

    /**
     * Midjourney 错误包装      */
    public static Map<String, Object> midjourneyError(int code, String description) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("description", description);
        return error;
    }

    /**
     * Claude 错误包装      */
    public static Map<String, Object> claudeError(String type, String message, int statusCode) {
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> errDetail = new LinkedHashMap<>();
        errDetail.put("type", "error");
        errDetail.put("error", Map.of(
                "type", type != null ? type : "api_error",
                "message", message != null ? message : "upstream error"
        ));
        error.put("status_code", statusCode);
        error.put("body", errDetail);
        return error;
    }

    // ======================== 辅助方法 ========================

    /**
     * 读取 HTTP 错误响应的 body
     */
    private static String readErrorBody(HttpURLConnection conn) {
        try {
            java.io.InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) {
                // 尝试从 inputStream 读取
                errorStream = conn.getInputStream();
            }
            if (errorStream == null) {
                return "";
            }
            byte[] buf = errorStream.readNBytes(MAX_ERROR_BODY_SIZE);
            String body = new String(buf, StandardCharsets.UTF_8);
            // 读取剩余以释放连接
            errorStream.skip(Long.MAX_VALUE);
            return body;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从错误 body 中提取人类可读的错误消息
     */
    private static String extractErrorMessage(String body, int statusCode) {
        if (body == null || body.isEmpty()) {
            return "upstream returned status " + statusCode;
        }
        // 尝试解析 JSON 错误格式
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = Convert.toJSONObject(body);
            // OpenAI 格式：{"error":{"message":"..."}}
            Object errorObj = json.get("error");
            if (errorObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                Object message = errorMap.get("message");
                if (message != null) return message.toString();
            }
            // 通用格式：{"message":"..."}
            Object message = json.get("message");
            if (message != null) return message.toString();
        } catch (Exception ignored) {
        }
        return "upstream returned status " + statusCode;
    }

    /**
     * 将 HTTP 状态码映射为错误类型字符串
     */
    private static String mapStatusCodeToType(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return "authentication_error";
        if (statusCode == 429) return "rate_limit_error";
        if (statusCode >= 500) return "upstream_error";
        return "api_error";
    }

    /**
     * 解析状态码值（支持数字和字符串）
     */
    private static int parseStatusCode(Object value) {
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return 0;
    }

    /**
     * 截断 body 用于日志
     */
    private static String truncateBody(String body) {
        if (body == null) return "null";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    // ======================== 内部类 ========================

    /**
     * API 错误对象（ApiError）
     */
    public static class ApiError {
        public int statusCode;
        public String errorType;
        public String message;

        public ApiError(int statusCode, String errorType, String message) {
            this.statusCode = statusCode;
            this.errorType = errorType;
            this.message = message;
        }

        public boolean isSkipRetry() {
            // 4xx 错误不重试（除了 429）
            return statusCode >= 400 && statusCode != 429 && statusCode < 500;
        }

        @Override
        public String toString() {
            return "ApiError{code=" + statusCode + ", type=" + errorType + ", msg=" + message + "}";
        }
    }
}
