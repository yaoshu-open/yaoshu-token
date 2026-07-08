package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.relay.common.RelayInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 文本计费服务  * <p>
 * 核心方法 calculateTextQuotaSummary：基于 token 数量和比率计算实际配额。
 * 使用 BigDecimal 保证精度。  * <p>
 * 计费公式（非按次计费模式）：
 * <pre>
 *   quota = (baseTokens + cachedTokens*cacheRatio + imageTokens*imageRatio + cachedCreationTokens*cacheCreationRatio
 *           + completionTokens*completionRatio) * modelRatio * groupRatio + toolSurcharge + audioInputQuota
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextQuotaService {

    /**
     * 文本计费汇总      */
    public static class TextQuotaSummary {
        public int promptTokens;
        public int completionTokens;
        public int totalTokens;
        public int cacheTokens;
        public int cacheCreationTokens;
        public int cacheCreationTokens5m;
        public int cacheCreationTokens1h;
        public int imageTokens;
        public int audioTokens;
        public String modelName;
        public String tokenName;
        public long useTimeSeconds;
        public double completionRatio;
        public double cacheRatio;
        public double imageRatio;
        public double modelRatio;
        public double groupRatio;
        public double modelPrice;
        public double cacheCreationRatio;
        public double cacheCreationRatio5m;
        public double cacheCreationRatio1h;
        public int quota;
        public boolean isClaudeUsageSemantic;
        public String usageSemantic;
        public double webSearchPrice;
        public int webSearchCallCount;
        public double claudeWebSearchPrice;
        public int claudeWebSearchCallCount;
        public double fileSearchPrice;
        public int fileSearchCallCount;
        public double audioInputPrice;
        public double imageGenerationCallPrice;
        public BigDecimal toolCallSurchargeQuota = BigDecimal.ZERO;
    }

    /**
     * 计算文本配额汇总      * <p>
     * 基于 token 数量和 PriceData 中的比率计算实际配额。
     *
     * @param relayInfo 中转信息（含 PriceData）
     * @param promptTokens     输入 tokens
     * @param completionTokens 输出 tokens
     * @param cacheTokens      缓存命中 tokens
     * @param cacheCreationTokens 缓存创建 tokens
     * @param imageTokens      图片 tokens
     * @param audioTokens      音频 tokens
     * @return 计费汇总
     */
    public TextQuotaSummary calculateSummary(RelayInfo relayInfo,
                                              int promptTokens, int completionTokens,
                                              int cacheTokens, int cacheCreationTokens,
                                              int imageTokens, int audioTokens) {
        PriceData priceData = relayInfo.getPriceData();
        TextQuotaSummary summary = new TextQuotaSummary();
        summary.modelName = relayInfo.getOriginModelName();
        summary.promptTokens = promptTokens;
        summary.completionTokens = completionTokens;
        summary.totalTokens = promptTokens + completionTokens;
        summary.cacheTokens = cacheTokens;
        summary.cacheCreationTokens = cacheCreationTokens;
        summary.imageTokens = imageTokens;
        summary.audioTokens = audioTokens;

        if (priceData == null) {
            summary.quota = 0;
            return summary;
        }

        summary.completionRatio = priceData.getCompletionRatio();
        summary.cacheRatio = priceData.getCacheRatio();
        summary.imageRatio = priceData.getImageRatio();
        summary.modelRatio = priceData.getModelRatio();
        summary.groupRatio = priceData.getGroupRatioInfo() != null
                ? priceData.getGroupRatioInfo().getGroupRatio() : 1.0;
        summary.modelPrice = priceData.getModelPrice();
        summary.cacheCreationRatio = priceData.getCacheCreationRatio();
        summary.cacheCreationRatio5m = priceData.getCacheCreation5mRatio();
        summary.cacheCreationRatio1h = priceData.getCacheCreation1hRatio();
        summary.isClaudeUsageSemantic = "anthropic".equals(summary.usageSemantic);

        BigDecimal dPromptTokens = BigDecimal.valueOf(promptTokens);
        BigDecimal dCacheTokens = BigDecimal.valueOf(cacheTokens);
        BigDecimal dImageTokens = BigDecimal.valueOf(imageTokens);
        BigDecimal dCompletionTokens = BigDecimal.valueOf(completionTokens);
        BigDecimal dCachedCreationTokens = BigDecimal.valueOf(cacheCreationTokens);
        BigDecimal dCompletionRatio = BigDecimal.valueOf(summary.completionRatio);
        BigDecimal dCacheRatio = BigDecimal.valueOf(summary.cacheRatio);
        BigDecimal dImageRatio = BigDecimal.valueOf(summary.imageRatio);
        BigDecimal dModelRatio = BigDecimal.valueOf(summary.modelRatio);
        BigDecimal dGroupRatio = BigDecimal.valueOf(summary.groupRatio);
        BigDecimal dModelPrice = BigDecimal.valueOf(summary.modelPrice);
        BigDecimal dCacheCreationRatio = BigDecimal.valueOf(summary.cacheCreationRatio);
        BigDecimal dQuotaPerUnit = BigDecimal.valueOf(CommonConstants.quotaPerUnit);

        BigDecimal ratio = dModelRatio.multiply(dGroupRatio);
        BigDecimal quotaPerUnit = dQuotaPerUnit;

        if (!priceData.isUsePrice()) {
            // 按 Token 倍率计费
            BigDecimal baseTokens = dPromptTokens;

            // 缓存命中 tokens
            BigDecimal cachedTokensWithRatio = BigDecimal.ZERO;
            if (cacheTokens > 0 && !summary.isClaudeUsageSemantic) {
                baseTokens = baseTokens.subtract(dCacheTokens);
                cachedTokensWithRatio = dCacheTokens.multiply(dCacheRatio);
            }

            // 缓存创建 tokens
            BigDecimal cachedCreationTokensWithRatio = BigDecimal.ZERO;
            if (cacheCreationTokens > 0 && !summary.isClaudeUsageSemantic) {
                baseTokens = baseTokens.subtract(dCachedCreationTokens);
                cachedCreationTokensWithRatio = dCachedCreationTokens.multiply(dCacheCreationRatio);
            }

            // 图片 tokens
            BigDecimal imageTokensWithRatio = BigDecimal.ZERO;
            if (imageTokens > 0) {
                baseTokens = baseTokens.subtract(dImageTokens);
                imageTokensWithRatio = dImageTokens.multiply(dImageRatio);
            }

            BigDecimal promptQuota = baseTokens
                    .add(cachedTokensWithRatio)
                    .add(imageTokensWithRatio)
                    .add(cachedCreationTokensWithRatio);
            BigDecimal completionQuota = dCompletionTokens.multiply(dCompletionRatio);
            BigDecimal quotaDecimal = promptQuota.add(completionQuota).multiply(ratio);
            quotaDecimal = quotaDecimal.add(summary.toolCallSurchargeQuota);

            // 兜底：ratio 非零但 quota <= 0 时设为 1
            if (ratio.compareTo(BigDecimal.ZERO) != 0 && quotaDecimal.compareTo(BigDecimal.ZERO) <= 0) {
                quotaDecimal = BigDecimal.ONE;
            }
            summary.quota = quotaDecimal.setScale(0, RoundingMode.HALF_UP).intValue();
        } else {
            // 按价格计费
            BigDecimal quotaDecimal = dModelPrice.multiply(dQuotaPerUnit).multiply(dGroupRatio);
            quotaDecimal = quotaDecimal.add(summary.toolCallSurchargeQuota);
            summary.quota = quotaDecimal.setScale(0, RoundingMode.HALF_UP).intValue();
        }

        // 总 token 数为 0 时不扣费
        if (summary.totalTokens == 0) {
            summary.quota = 0;
        } else if (ratio.compareTo(BigDecimal.ZERO) != 0 && summary.quota == 0) {
            summary.quota = 1;
        }

        return summary;
    }

    /**
     * 简化版文本配额计算（不涉及缓存/图片/音频），委托给 QuotaService
     */
    public int calculateSimpleQuota(int inputTokens, int outputTokens, PriceData priceData) {
        if (priceData == null) {
            return (int) Math.round((inputTokens + outputTokens * 2.0));
        }
        double modelRatio = priceData.getModelRatio();
        double completionRatio = priceData.getCompletionRatio();
        double groupRatio = priceData.getGroupRatioInfo() != null
                ? priceData.getGroupRatioInfo().getGroupRatio() : 1.0;
        double ratio = groupRatio * modelRatio;
        if (ratio == 0) ratio = 1.0;
        return (int) Math.round((inputTokens + outputTokens * completionRatio) * ratio);
    }
}
