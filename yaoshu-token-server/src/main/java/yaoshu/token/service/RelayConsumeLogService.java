package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.common.ModelUtils;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.relay.common.RelayInfo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayConsumeLogService {

    private static final int LOG_TYPE_CONSUME = 2;

    private final LogMapper logMapper;
    private final ModelUtils modelUtils;

    public void recordConsumeLog(RelayInfo relayInfo, int actualQuota, Usage usage) {
        if (relayInfo == null || usage == null || actualQuota <= 0) {
            return;
        }
        try {
            Log logEntry = new Log();
            logEntry.setUserId(relayInfo.getUserId());
            logEntry.setType(LOG_TYPE_CONSUME);
            logEntry.setChannelId(relayInfo.getChannelId());
            logEntry.setChannelName(extraString(relayInfo, "channelName"));
            logEntry.setModelName(resolveModelName(relayInfo));
            logEntry.setTokenId(relayInfo.getTokenId());
            String tokenName = extraString(relayInfo, "tokenName");
            logEntry.setTokenName(tokenName != null ? tokenName : relayInfo.getTokenKey());
            logEntry.setUsername(extraString(relayInfo, "username"));
            logEntry.setGroup(resolveGroup(relayInfo));
            logEntry.setQuota(actualQuota);
            logEntry.setPromptTokens(usage.getPromptTokens());
            logEntry.setCompletionTokens(usage.getCompletionTokens());
            logEntry.setCachedTokens(resolveCachedTokens(usage));
            logEntry.setKeyIndex(resolveKeyIndex(relayInfo));
            logEntry.setRequestId(relayInfo.getRequestId());
            logEntry.setUpstreamRequestId(extraString(relayInfo, "upstreamRequestId"));
            logEntry.setUseTime(resolveUseTime(relayInfo.getStartTime()));
            logEntry.setIsStream(relayInfo.isStream());
            logEntry.setCreatedAt(System.currentTimeMillis() / 1000);
            logEntry.setOther(buildOther(relayInfo, usage));
            // 仅错误调用填充错误摘要（脱敏后），正常调用留空（前端从 other 字段组装展示）
            if (relayInfo.getLastError() != null) {
                logEntry.setContent(relayInfo.getLastError().maskSensitiveError());
            }
            logMapper.insert(logEntry);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_USED_QUOTA, relayInfo.getUserId(), actualQuota);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_REQUEST_COUNT, relayInfo.getUserId(), 1);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA, relayInfo.getChannelId(), actualQuota);
        } catch (Exception e) {
            log.error("记录主消费日志失败", e);
        }
    }

    private String resolveModelName(RelayInfo relayInfo) {
        if (relayInfo.getUpstreamModelName() != null && relayInfo.getUpstreamModelName().length() > 0) {
            return relayInfo.getUpstreamModelName();
        }
        return relayInfo.getOriginModelName();
    }

    private String resolveGroup(RelayInfo relayInfo) {
        if (relayInfo.getUsingGroup() != null && relayInfo.getUsingGroup().length() > 0) {
            return relayInfo.getUsingGroup();
        }
        return relayInfo.getTokenGroup();
    }

    private Integer resolveKeyIndex(RelayInfo relayInfo) {
        return relayInfo.isChannelIsMultiKey() ? relayInfo.getChannelMultiKeyIndex() : null;
    }

    private int resolveUseTime(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(startTime, LocalDateTime.now()).getSeconds());
    }

    private int resolveCachedTokens(Usage usage) {
        if (usage.getPromptTokensDetails() != null && usage.getPromptTokensDetails().getCachedTokens() > 0) {
            return usage.getPromptTokensDetails().getCachedTokens();
        }
        if (usage.getInputTokensDetails() != null) {
            return usage.getInputTokensDetails().getCachedTokens();
        }
        return usage.getPromptCacheHitTokens();
    }

    private String buildOther(RelayInfo relayInfo, Usage usage) {
        Map<String, Object> other = new LinkedHashMap<>();
        // otherInfo JSON 内部 key 保留 snake_case（前端设计文档 §5.1 命名契约）
        other.put("billing_source", relayInfo.getBillingSource());
        other.put("relayMode", relayInfo.getRelayMode());
        other.put("originModel", relayInfo.getOriginModelName());
        other.put("upstreamModel", relayInfo.getUpstreamModelName());
        other.put("cachedTokens", resolveCachedTokens(usage));
        other.put("cacheCreationTokens", usage.getPromptTokensDetails() != null ? usage.getPromptTokensDetails().getCachedCreationTokens() : 0);
        other.put("usageSemantic", usage.getUsageSemantic());
        other.put("usageSource", usage.getUsageSource());
        // is_stream 冗余写入 other JSON（logs 表已有 is_stream 列，前端从 other 读取更直观）
        other.put("is_stream", relayInfo.isStream());
        // 完整 usage 对象（含 reasoning_tokens/audio_tokens/image_tokens 等扩展字段），供前端展示完整 token 明细
        other.put("usage", usage);

        // 计费明细字段
        PriceData pd = relayInfo.getPriceData();
        if (pd != null) {
            other.put("model_ratio", pd.getModelRatio());
            other.put("group_ratio", pd.getGroupRatioInfo() != null ? pd.getGroupRatioInfo().getGroupRatio() : 0);
            other.put("completion_ratio", pd.getCompletionRatio());
            other.put("cache_ratio", pd.getCacheRatio());
            other.put("model_price", pd.getModelPrice());
            other.put("use_price", pd.isUsePrice());
        }

        // 耗时明细（ms）：首字延迟 frt + 总耗时 total_latency + 生成耗时 completion_latency
        // frt 仅流式请求有值（非流式 firstResponseTime 为 null）；recordConsumeLog 在请求结束后调用，此刻即结束时刻
        Long frt = null;
        if (relayInfo.getFirstResponseTime() != null && relayInfo.getStartTime() != null) {
            frt = java.time.Duration.between(relayInfo.getStartTime(), relayInfo.getFirstResponseTime()).toMillis();
            other.put("frt", frt);
        }
        if (relayInfo.getStartTime() != null) {
            long totalLatency = java.time.Duration.between(relayInfo.getStartTime(), LocalDateTime.now()).toMillis();
            other.put("total_latency", totalLatency);
            // completion_latency = 总耗时 - 首字耗时；非流式（无 frt）时等于 total_latency
            other.put("completion_latency", frt != null ? Math.max(0, totalLatency - frt) : totalLatency);
        }

        // 模型映射标记
        if (relayInfo.isModelMapped()) {
            other.put("is_model_mapped", true);
            other.put("upstream_model_name", relayInfo.getUpstreamModelName());
        }

        // reasoning_effort
        if (relayInfo.getReasoningEffort() != null && !relayInfo.getReasoningEffort().isEmpty()) {
            other.put("reasoning_effort", relayInfo.getReasoningEffort());
        }

        // 订阅计费信息
        appendBillingInfo(relayInfo, other);

        return Convert.toJSONString(other);
    }

    /**
     * 订阅计费信息写入 other JSON
     * <p>
     * 仅订阅计费时写入 subscription 相关字段。Go 原版逻辑：syncRelayInfo 将订阅状态同步到 RelayInfo 后，
     * 日志记录时追加 subscription_id/subscription_plan_id/subscription_plan_title 等字段供前端展示。
     */
    private void appendBillingInfo(RelayInfo relayInfo, Map<String, Object> other) {
        String billingSource = relayInfo.getBillingSource();
        if (!"subscription".equals(billingSource)) {
            return;
        }

        if (relayInfo.getSubscriptionId() != 0) {
            other.put("subscription_id", relayInfo.getSubscriptionId());
        }
        if (relayInfo.getSubscriptionPreConsumed() > 0) {
            other.put("subscription_pre_consumed", relayInfo.getSubscriptionPreConsumed());
        }
        if (relayInfo.getSubscriptionPostDelta() != 0) {
            other.put("subscription_post_delta", relayInfo.getSubscriptionPostDelta());
        }
        if (relayInfo.getSubscriptionPlanId() != 0) {
            other.put("subscription_plan_id", relayInfo.getSubscriptionPlanId());
        }
        if (relayInfo.getSubscriptionPlanTitle() != null && !relayInfo.getSubscriptionPlanTitle().isEmpty()) {
            other.put("subscription_plan_title", relayInfo.getSubscriptionPlanTitle());
        }

        // 计算本次请求订阅消费和最终剩余（负数保护）
        long consumed = relayInfo.getSubscriptionPreConsumed() + relayInfo.getSubscriptionPostDelta();
        long usedFinal = relayInfo.getSubscriptionAmountUsedAfterPreConsume() + relayInfo.getSubscriptionPostDelta();
        if (consumed < 0) consumed = 0;
        if (usedFinal < 0) usedFinal = 0;

        if (relayInfo.getSubscriptionAmountTotal() > 0) {
            long remain = relayInfo.getSubscriptionAmountTotal() - usedFinal;
            if (remain < 0) remain = 0;
            other.put("subscription_total", relayInfo.getSubscriptionAmountTotal());
            other.put("subscription_used", usedFinal);
            other.put("subscription_remain", remain);
        }
        if (consumed > 0) {
            other.put("subscription_consumed", consumed);
        }
        // 订阅计费时钱包不扣
        other.put("wallet_quota_deducted", 0);
    }

    private String extraString(RelayInfo relayInfo, String key) {
        if (relayInfo.getExtraData() == null) {
            return null;
        }
        Object value = relayInfo.getExtraData().get(key);
        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > 0) {
                return str;
            }
        }
        return null;
    }
}
