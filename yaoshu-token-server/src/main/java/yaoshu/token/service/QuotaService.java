package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

/**
 * 配额计算与消耗服务  * <p>
 * 文本计费公式（缓存感知，委托 {@link TextQuotaService#calculateSummary}）：
 * <pre>
 *   baseTokens = promptTokens - cacheTokens - cacheCreationTokens - imageTokens
 *   quota = (baseTokens + cacheTokens*cacheRatio + cacheCreationTokens*cacheCreationRatio
 *           + imageTokens*imageRatio + completionTokens*completionRatio) * modelRatio * groupRatio
 * </pre>
 * 简化版（不区分缓存，用于无 Usage 明细的场景）：
 * <pre>
 *   quota = (promptTokens + completionTokens * completionRatio) * modelRatio * groupRatio
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final PreConsumeQuotaService preConsumeQuotaService;
    private final TextQuotaService textQuotaService;

    /**
     * 预扣费（旧路径）      */
    public String preConsumeQuota(RelayInfo relayInfo, int preConsumedQuota) {
        return preConsumeQuotaService.preConsumeQuota(relayInfo, preConsumedQuota);
    }

    /**
     * 后结算配额      */
    public void postConsumeQuota(RelayInfo relayInfo, int quotaDelta, int preConsumedQuota) {
        preConsumeQuotaService.postConsumeQuota(relayInfo, quotaDelta, preConsumedQuota);
    }

    /**
     * 返还预扣费      */
    public void returnPreConsumedQuota(RelayInfo relayInfo) {
        preConsumeQuotaService.returnPreConsumedQuota(relayInfo);
    }

    // ======================== 比率计算 ========================

    /**
     * 缓存感知文本计费——从 Usage 提取缓存/图片/音频 token 明细，委托 TextQuotaService 精确计价。
     * <p>
     * 缓存命中部分按 cacheRatio 折扣计价，缓存创建按 cacheCreationRatio 计价。
     *
     * @param info   中转上下文（含 PriceData）
     * @param usage  上游返回的 Usage（含 promptTokensDetails.cachedTokens 等）
     * @return 配额值
     */
    public int calculateTextQuotaWithCache(RelayInfo info, Usage usage) {
        if (usage == null) {
            return 0;
        }
        int promptTokens = usage.getPromptTokens();
        int completionTokens = usage.getCompletionTokens();
        int cacheTokens = 0;
        int cacheCreationTokens = 0;
        int imageTokens = 0;
        int audioTokens = 0;

        if (usage.getPromptTokensDetails() != null) {
            cacheTokens = usage.getPromptTokensDetails().getCachedTokens();
            cacheCreationTokens = usage.getPromptTokensDetails().getCachedCreationTokens();
            imageTokens = usage.getPromptTokensDetails().getImageTokens();
            audioTokens = usage.getPromptTokensDetails().getAudioTokens();
        }

        // OpenRouter + Claude 语义：上游不返回 cacheCreationTokens 明细，通过 usage.cost 反推
        PriceData priceData = info.getPriceData();
        if (priceData != null
                && cacheCreationTokens == 0
                && priceData.getCacheCreationRatio() != 1
                && usage.getCost() != null
                && info.getChannelType() == ChannelConstants.CHANNEL_TYPE_OPEN_ROUTER
                && "anthropic".equals(usage.getUsageSemantic())) {
            int maybe = calcOpenRouterCacheCreateTokens(usage, priceData);
            if (maybe >= 0 && promptTokens >= maybe) {
                cacheCreationTokens = maybe;
            }
        }

        TextQuotaService.TextQuotaSummary summary = textQuotaService.calculateSummary(
                info, promptTokens, completionTokens,
                cacheTokens, cacheCreationTokens, imageTokens, audioTokens);
        return summary.quota;
    }

    /**
     * 反推 OpenRouter Claude 场景的 cacheCreationTokens      * <p>
     * OpenRouter 对 Claude 模型不返回 cacheCreationTokens 明细，但返回总 cost。
     * 通过 cost 减去其他已知部分的费用，除以缓存创建单价差，反推 cacheCreationTokens。
     *
     * @param usage     上游 usage（含 cost）
     * @param priceData 价格数据
     * @return 反推的 cacheCreationTokens
     */
    private int calcOpenRouterCacheCreateTokens(Usage usage, PriceData priceData) {
        if (priceData.getCacheCreationRatio() == 1) {
            return 0;
        }
        double quotaPrice = priceData.getModelRatio() / CommonConstants.quotaPerUnit;
        double promptCacheCreatePrice = quotaPrice * priceData.getCacheCreationRatio();
        double promptCacheReadPrice = quotaPrice * priceData.getCacheRatio();
        double completionPrice = quotaPrice * priceData.getCompletionRatio();

        double cost = 0;
        if (usage.getCost() instanceof Number n) {
            cost = n.doubleValue();
        }
        double totalPromptTokens = usage.getPromptTokens();
        double completionTokens = usage.getCompletionTokens();
        double promptCacheReadTokens = usage.getPromptTokensDetails() != null
                ? usage.getPromptTokensDetails().getCachedTokens() : 0;

        double denominator = promptCacheCreatePrice - quotaPrice;
        if (denominator == 0) {
            return 0;
        }
        return (int) Math.round((cost
                - totalPromptTokens * quotaPrice
                + promptCacheReadTokens * (quotaPrice - promptCacheReadPrice)
                - completionTokens * completionPrice) / denominator);
    }

    /**
     * 计算文本配额（简化版，不区分缓存）      * <p>
     * 使用 PriceData 中的比率计算实际配额。
     *
     * @param inputTokens    输入 tokens
     * @param outputTokens   输出 tokens
     * @param priceData      价格数据（含 modelRatio/completionRatio/groupRatio 等）
     */
    public int calculateTextQuota(int inputTokens, int outputTokens, PriceData priceData) {
        if (priceData == null) {
            // 无价格数据时使用默认倍率
            return (int) Math.round((inputTokens + outputTokens * 2.0) * 1.0);
        }
        double modelRatio = priceData.getModelRatio();
        double completionRatio = priceData.getCompletionRatio();
        double groupRatio = priceData.getGroupRatioInfo() != null
                ? priceData.getGroupRatioInfo().getGroupRatio() : 1.0;

        double ratio = groupRatio * modelRatio;
        if (ratio == 0) ratio = 1.0;

        return (int) Math.round((inputTokens + outputTokens * completionRatio) * ratio);
    }

    /**
     * 计算音频配额      */
    public int calculateAudioQuota(int textInputTokens, int textOutputTokens,
                                    int audioInputTokens, int audioOutputTokens,
                                    PriceData priceData) {
        if (priceData == null) {
            return (int) Math.round((textInputTokens + textOutputTokens * 2.0
                    + audioInputTokens * 2.0 + audioOutputTokens * 4.0) * 1.0);
        }
        double modelRatio = priceData.getModelRatio();
        double completionRatio = priceData.getCompletionRatio();
        double audioRatio = priceData.getAudioRatio() > 0 ? priceData.getAudioRatio() : 2.0;
        double audioCompletionRatio = priceData.getAudioCompletionRatio() > 0
                ? priceData.getAudioCompletionRatio() : 2.0;
        double groupRatio = priceData.getGroupRatioInfo() != null
                ? priceData.getGroupRatioInfo().getGroupRatio() : 1.0;

        double ratio = groupRatio * modelRatio;
        if (ratio == 0) ratio = 1.0;

        double quota = textInputTokens
                + textOutputTokens * completionRatio
                + audioInputTokens * audioRatio
                + audioOutputTokens * audioRatio * audioCompletionRatio;
        return (int) Math.round(quota * ratio);
    }
}
