package yaoshu.token.relay.common;

import ai.yue.library.base.util.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.ChannelOtherSettingsDTO;

import java.util.*;

/**
 * Relay 通用工具方法  */
@Slf4j
public final class RelayUtils {

    private RelayUtils() {
    }

    private static ObjectMapper MAPPER;

    private static ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = SpringUtils.getBean(ObjectMapper.class);
        }
        return MAPPER;
    }

    /** 已知 Task 字段（不归入 metadata） */
    private static final Set<String> KNOWN_TASK_FIELDS = Set.of(
            "prompt", "model", "mode", "image", "images", "size", "duration", "input_reference", "seconds"
    );

    /**
     * 拼接完整请求 URL      */
    public static String getFullRequestURL(String baseURL, String requestURL, int channelType) {
        if (baseURL == null) baseURL = "";
        if (requestURL == null) requestURL = "";

        String fullURL = baseURL + requestURL;

        if (baseURL.startsWith("https://gateway.ai.cloudflare.com")) {
            // Cloudflare 网关 — 去掉 /v1 前缀
            if (channelType == yaoshu.token.constant.ChannelConstants.CHANNEL_TYPE_OPENAI) {
                fullURL = baseURL + requestURL.replaceFirst("^/v1", "");
            } else if (channelType == yaoshu.token.constant.ChannelConstants.CHANNEL_TYPE_AZURE) {
                fullURL = baseURL + requestURL.replaceFirst("^/openai/deployments", "");
            }
        }
        return fullURL;
    }

    /**
     * 获取 API 版本（Azure）      */
    public static String getAPIVersion(String queryApiVersion, String contextApiVersion) {
        if (queryApiVersion != null && !queryApiVersion.isEmpty()) {
            return queryApiVersion;
        }
        return contextApiVersion;
    }

    /**
     * 检查是否为已知 Task 字段      */
    public static boolean isKnownTaskField(String field) {
        return KNOWN_TASK_FIELDS.contains(field);
    }

    /**
     * 校验 prompt 非空      */
    public static Object validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return createTaskError("prompt is required", "invalid_request", 400, true);
        }
        return null;
    }

    /**
     * 校验 Sora 模型参数（size、seconds）      */
    public static Object validateSoraParams(String model, String size, int seconds) {
        Set<String> validSizes;
        if ("sora-2".equals(model)) {
            if (size == null || size.isEmpty()) size = "720x1280";
            if (seconds <= 0) seconds = 4;
            validSizes = Set.of("720x1280", "1280x720");
        } else if ("sora-2-pro".equals(model)) {
            if (size == null || size.isEmpty()) size = "720x1280";
            if (seconds <= 0) seconds = 4;
            validSizes = Set.of("720x1280", "1280x720", "1792x1024", "1024x1792");
        } else {
            return null; // 非 Sora 模型不校验
        }

        if (!validSizes.contains(size)) {
            return createTaskError("sora-2 size is invalid: " + size, "invalid_size", 400, true);
        }
        return null;
    }

    // ======================== 内部工具 ========================

    /**
     * 创建 Task 错误对象（Map 格式）      */
    private static Map<String, Object> createTaskError(String code, String message, int statusCode, boolean localError) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("statusCode", statusCode);
        error.put("localError", localError);
        return error;
    }

    // ======================== DeepCopy ========================

    /**
     * 通过 Jackson 序列化/反序列化实现深拷贝      *
     * @param obj  待拷贝对象
     * @param type 目标类型
     * @return 深拷贝后的新对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(Object obj, Class<T> type) {
        if (obj == null) return null;
        try {
            byte[] bytes = getMapper().writeValueAsBytes(obj);
            return getMapper().readValue(bytes, type);
        } catch (Exception e) {
            log.error("DeepCopy failed for type {}: {}", type.getSimpleName(), e.getMessage());
            throw new RuntimeException("DeepCopy failed: " + e.getMessage(), e);
        }
    }

    // ======================== RemoveDisabledFields ========================

    /**
     * 移除上游请求体中被禁用的字段      * <p>
     * 根据 ChannelOtherSettings 移除 service_tier / inference_geo / speed /
     * store / safety_identifier / stream_options.include_obfuscation 等字段。
     * PassThrough 模式下跳过移除。
     *
     * @param jsonData             原始 JSON 字节
     * @param channelOtherSettings 渠道其他设置
     * @param channelPassThrough   渠道 PassThrough 是否启用
     * @return 移除后的 JSON 字节
     */
    public static byte[] removeDisabledFields(byte[] jsonData,
                                               ChannelOtherSettingsDTO channelOtherSettings,
                                               boolean channelPassThrough) {
        // PassThrough 模式：跳过移除
        boolean passThroughGlobal = yaoshu.token.config.model.GlobalModelSettingConfig.getInstance().isPassThroughRequestEnabled();
        if (passThroughGlobal || channelPassThrough) {
            return jsonData;
        }

        // 快速检查：是否有可移除字段
        if (!hasRemovableDisabledField(jsonData, channelOtherSettings)) {
            return jsonData;
        }

        try {
            JsonNode root = getMapper().readTree(jsonData);
            if (!(root instanceof ObjectNode)) {
                return jsonData;
            }
            ObjectNode data = (ObjectNode) root;

            // 默认移除 service_tier，除非明确允许（避免额外计费风险）
            if (!Boolean.TRUE.equals(channelOtherSettings.getAllowServiceTier())) {
                data.remove("service_tier");
            }

            // 默认移除 inference_geo，除非明确允许
            if (!Boolean.TRUE.equals(channelOtherSettings.getAllowInferenceGeo())) {
                data.remove("inference_geo");
            }

            // 默认移除 speed，除非明确允许
            if (!Boolean.TRUE.equals(channelOtherSettings.getAllowSpeed())) {
                data.remove("speed");
            }

            // 默认允许 store 透传，除非明确禁用
            if (Boolean.TRUE.equals(channelOtherSettings.getDisableStore())) {
                data.remove("store");
            }

            // 默认移除 safety_identifier，除非明确允许（保护用户隐私）
            if (!Boolean.TRUE.equals(channelOtherSettings.getAllowSafetyIdentifier())) {
                data.remove("safety_identifier");
            }

            // 默认移除 stream_options.include_obfuscation，除非明确允许
            if (!Boolean.TRUE.equals(channelOtherSettings.getAllowIncludeObfuscation())) {
                JsonNode streamOptions = data.get("stream_options");
                if (streamOptions instanceof ObjectNode) {
                    ObjectNode so = (ObjectNode) streamOptions;
                    so.remove("include_obfuscation");
                    if (so.isEmpty()) {
                        data.remove("stream_options");
                    }
                }
            }

            return getMapper().writeValueAsBytes(data);
        } catch (Exception e) {
            log.error("RemoveDisabledFields error: {}", e.getMessage());
            return jsonData;
        }
    }

    /**
     * 快速检查 JSON 中是否包含可被禁用的字段      */
    private static boolean hasRemovableDisabledField(byte[] jsonData,
                                                      ChannelOtherSettingsDTO settings) {
        try {
            JsonNode root = getMapper().readTree(jsonData);
            if (settings == null) return false;

            return (!Boolean.TRUE.equals(settings.getAllowServiceTier()) && root.has("service_tier"))
                    || (!Boolean.TRUE.equals(settings.getAllowInferenceGeo()) && root.has("inference_geo"))
                    || (!Boolean.TRUE.equals(settings.getAllowSpeed()) && root.has("speed"))
                    || (Boolean.TRUE.equals(settings.getDisableStore()) && root.has("store"))
                    || (!Boolean.TRUE.equals(settings.getAllowSafetyIdentifier()) && root.has("safety_identifier"))
                    || (!Boolean.TRUE.equals(settings.getAllowIncludeObfuscation())
                        && root.has("stream_options")
                        && root.get("stream_options").has("include_obfuscation"));
        } catch (Exception e) {
            return false;
        }
    }
}
