package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

/**
 * Usage 后处理（cached_tokens 提取）  * <p>
 * 不同渠道 upstream 返回的 cached_tokens 位置不同（标准/DeepSeek/智谱/Moonshot/llama.cpp），
 * 此工具统一提取到 usage.promptTokensDetails.cachedTokens。
 */
@Slf4j
public final class UsagePostProcessor {

    private UsagePostProcessor() {
    }

    /**
     * 根据渠道类型后处理 usage      */
    public static void applyUsagePostProcessing(RelayInfo info, Usage usage, byte[] responseBody) {
        if (info == null || usage == null) return;

        switch (info.getChannelType()) {
            case ChannelConstants.CHANNEL_TYPE_DEEPSEEK:
                if (usage.getPromptTokensDetails().getCachedTokens() == 0
                        && usage.getPromptCacheHitTokens() != 0) {
                    usage.getPromptTokensDetails().setCachedTokens(usage.getPromptCacheHitTokens());
                }
                break;

            case ChannelConstants.CHANNEL_TYPE_ZHIPU_V4:
                if (usage.getPromptTokensDetails().getCachedTokens() == 0) {
                    // 智谱的 cached_tokens 在标准位置
                    if (usage.getInputTokensDetails() != null
                            && usage.getInputTokensDetails().getCachedTokens() > 0) {
                        usage.getPromptTokensDetails().setCachedTokens(
                                usage.getInputTokensDetails().getCachedTokens());
                    } else {
                        int cached = extractCachedTokensFromBody(responseBody);
                        if (cached > 0) {
                            usage.getPromptTokensDetails().setCachedTokens(cached);
                        } else if (usage.getPromptCacheHitTokens() > 0) {
                            usage.getPromptTokensDetails().setCachedTokens(usage.getPromptCacheHitTokens());
                        }
                    }
                }
                break;

            case ChannelConstants.CHANNEL_TYPE_MOONSHOT:
                if (usage.getPromptTokensDetails().getCachedTokens() == 0) {
                    // Moonshot 的 cached_tokens 在非标准位置: choices[].usage.cached_tokens
                    if (usage.getInputTokensDetails() != null
                            && usage.getInputTokensDetails().getCachedTokens() > 0) {
                        usage.getPromptTokensDetails().setCachedTokens(
                                usage.getInputTokensDetails().getCachedTokens());
                    } else {
                        int cached = extractMoonshotCachedTokensFromBody(responseBody);
                        if (cached > 0) {
                            usage.getPromptTokensDetails().setCachedTokens(cached);
                        } else {
                            cached = extractCachedTokensFromBody(responseBody);
                            if (cached > 0) {
                                usage.getPromptTokensDetails().setCachedTokens(cached);
                            } else if (usage.getPromptCacheHitTokens() > 0) {
                                usage.getPromptTokensDetails().setCachedTokens(usage.getPromptCacheHitTokens());
                            }
                        }
                    }
                }
                break;

            case ChannelConstants.CHANNEL_TYPE_OPENAI:
                if (usage.getPromptTokensDetails().getCachedTokens() == 0) {
                    int cached = extractLlamaCachedTokensFromBody(responseBody);
                    if (cached > 0) {
                        usage.getPromptTokensDetails().setCachedTokens(cached);
                    }
                }
                break;

            default:
                // 通用渠道：尝试从标准位置提取 cached_tokens（覆盖非 DEEPSEEK/ZHIPU/MOONSHOT 类型
                // 但上游实际支持 prompt cache 的渠道，如 OpenAI 兼容代理转发 DeepSeek）
                if (usage.getPromptTokensDetails().getCachedTokens() == 0) {
                    int cached = extractCachedTokensFromBody(responseBody);
                    if (cached > 0) {
                        usage.getPromptTokensDetails().setCachedTokens(cached);
                    }
                }
                break;
        }
    }

    // ======================== 缓存 token 提取 ========================

    /**
     * 从标准位置提取 cached_tokens      */
    @SuppressWarnings("unchecked")
    static int extractCachedTokensFromBody(byte[] body) {
        if (body == null || body.length == 0) return 0;
        try {
            Map<String, Object> root = Convert.toJSONObject(new String(body));
            Object usage = root.get("usage");
            if (!(usage instanceof Map)) return 0;
            Map<String, Object> usageMap = (Map<String, Object>) usage;

            // prompt_tokens_details.cached_tokens
            Object details = usageMap.get("prompt_tokens_details");
            if (details instanceof Map) {
                Map<String, Object> detailsMap = (Map<String, Object>) details;
                if (detailsMap.containsKey("cached_tokens")) {
                    int v = toInt(detailsMap.get("cached_tokens"), 0);
                    if (v > 0) return v;
                }
            }
            // cached_tokens (backwards-compat)
            if (usageMap.containsKey("cached_tokens")) {
                int v = toInt(usageMap.get("cached_tokens"), 0);
                if (v > 0) return v;
            }
            // prompt_cache_hit_tokens
            if (usageMap.containsKey("prompt_cache_hit_tokens")) {
                int v = toInt(usageMap.get("prompt_cache_hit_tokens"), 0);
                if (v > 0) return v;
            }
        } catch (Exception e) {
            log.debug("Failed to extract cached tokens: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 从 Moonshot 的非标准位置提取 cached_tokens（choices[].usage.cached_tokens），
     */
    @SuppressWarnings("unchecked")
    static int extractMoonshotCachedTokensFromBody(byte[] body) {
        if (body == null || body.length == 0) return 0;
        try {
            Map<String, Object> root = Convert.toJSONObject(new String(body));
            Object choices = root.get("choices");
            if (!(choices instanceof List)) return 0;

            for (Object choice : (List<?>) choices) {
                if (!(choice instanceof Map)) continue;
                Map<String, Object> choiceMap = (Map<String, Object>) choice;
                Object usage = choiceMap.get("usage");
                if (usage instanceof Map) {
                    Map<String, Object> usageMap = (Map<String, Object>) usage;
                    if (usageMap.containsKey("cached_tokens")) {
                        int v = toInt(usageMap.get("cached_tokens"), 0);
                        if (v > 0) return v;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract Moonshot cached tokens: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 从 llama.cpp 的非标准位置提取 cache_n      */
    @SuppressWarnings("unchecked")
    static int extractLlamaCachedTokensFromBody(byte[] body) {
        if (body == null || body.length == 0) return 0;
        try {
            Map<String, Object> root = Convert.toJSONObject(new String(body));
            Object timings = root.get("timings");
            if (timings instanceof Map) {
                Map<String, Object> timingsMap = (Map<String, Object>) timings;
                if (timingsMap.containsKey("cache_n")) {
                    return toInt(timingsMap.get("cache_n"), 0);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract llama cached tokens: {}", e.getMessage());
        }
        return 0;
    }

    /** 安全地将 Object 转为 int */
    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
