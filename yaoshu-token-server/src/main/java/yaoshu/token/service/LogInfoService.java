package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志信息生成服务  * <p>
 * 核心方法：GenerateTextOtherInfo、GenerateRequestLog
 * 生成中转请求的日志元信息（other 字段），含模型倍率、渠道使用、计费等。
 */
@Slf4j
public class LogInfoService {

    /**
     * 生成文本中转日志的 other 信息      */
    public static Map<String, Object> generateTextOtherInfo(RelayInfo info,
                                                             double modelRatio, double groupRatio,
                                                             double completionRatio, int cacheTokens,
                                                             double cacheRatio, double modelPrice,
                                                             double userGroupRatio) {
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("model_ratio", modelRatio);
        other.put("group_ratio", groupRatio);
        other.put("completion_ratio", completionRatio);
        other.put("cache_tokens", cacheTokens);
        other.put("cache_ratio", cacheRatio);
        other.put("model_price", modelPrice);
        other.put("user_group_ratio", userGroupRatio);

        if (info != null) {
            // 首响应时间（毫秒）
            if (info.getStartTime() != null && info.getFirstResponseTime() != null) {
                long frtMs = Duration.between(info.getStartTime(), info.getFirstResponseTime()).toMillis();
                other.put("frt", (double) frtMs);
            }

            if (info.getReasoningEffort() != null && !info.getReasoningEffort().isEmpty()) {
                other.put("reasoning_effort", info.getReasoningEffort());
            }
            if (info.isModelMapped()) {
                other.put("is_model_mapped", true);
                other.put("upstream_model_name", info.getUpstreamModelName());
            }

            // 请求路径
            if (info.getRequestURLPath() != null && !info.getRequestURLPath().isEmpty()) {
                String path = info.getRequestURLPath();
                int qIdx = path.indexOf("?");
                if (qIdx != -1) path = path.substring(0, qIdx);
                other.put("request_path", path);
            }

            // 请求转换链
            if (info.getRequestConversionChain() != null && !info.getRequestConversionChain().isEmpty()) {
                other.put("request_conversion_chain", info.getRequestConversionChain());
            }

            // 参数覆写信息
            if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
                other.put("param_override", info.getParamOverride());
            }
        }

        // 管理员信息
        Map<String, Object> adminInfo = new LinkedHashMap<>();
        if (info != null) {
            adminInfo.put("channel_id", info.getChannelId());
            if (info.isChannelIsMultiKey()) {
                adminInfo.put("is_multi_key", true);
                adminInfo.put("multi_key_index", info.getChannelMultiKeyIndex());
            }
        }
        other.put("admin_info", adminInfo);

        return other;
    }

    /**
     * 生成请求日志（简化版）      */
    public static Map<String, Object> generateRequestLog(RelayInfo info) {
        Map<String, Object> log = new LinkedHashMap<>();
        if (info != null) {
            log.put("model_name", info.getUpstreamModelName());
            log.put("channel_id", info.getChannelId());
            log.put("relay_mode", info.getRelayMode());
        }
        return log;
    }
}
