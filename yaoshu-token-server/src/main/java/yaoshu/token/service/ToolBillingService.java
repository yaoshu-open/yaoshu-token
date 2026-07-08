package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.common.ModelUtils;
import yaoshu.token.config.operation.ToolsSettingConfig;
import yaoshu.token.constant.CommonConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具调用计费服务  * <p>
 * 计算工具调用（web_search / file_search / image_generation）的额外计费。
 * 工具价格通过 ToolsSettingConfig 读取，支持模型前缀覆盖。
 * <p>
 * 计费公式：quota = round(pricePer1K * count / 1000 * QuotaPerUnit * groupRatio)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolBillingService {

    private final ToolsSettingConfig toolsSettingConfig;
    private final ModelUtils modelUtils;

    /**
     * 计算工具调用费用      *
     * @param usage      工具使用统计
     * @param groupRatio 分组倍率
     * @return 工具计费结果
     */
    public ToolCallResult computeToolCallQuota(ToolCallUsage usage, double groupRatio) {
        List<ToolCallItem> items = new ArrayList<>();
        BigDecimal totalQuota = BigDecimal.ZERO;

        // Web 搜索
        if (usage.webSearchCalls > 0 && usage.webSearchToolName != null && !usage.webSearchToolName.isEmpty()) {
            BigDecimal[] result = addItem(items, usage.webSearchToolName, usage.webSearchCalls,
                    usage.modelName, groupRatio);
            totalQuota = totalQuota.add(result[0]);
        }

        // 文件搜索
        if (usage.fileSearchCalls > 0) {
            BigDecimal[] result = addItem(items, "file_search", usage.fileSearchCalls,
                    usage.modelName, groupRatio);
            totalQuota = totalQuota.add(result[0]);
        }

        // 图片生成（按次计费，使用 GPT Image 1 定价表）
        if (usage.imageGenerationCall) {
            double price = getGptImage1PriceOnceCall(usage.imageGenerationQuality, usage.imageGenerationSize);
            BigDecimal quota = BigDecimal.valueOf(price)
                    .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                    .multiply(BigDecimal.valueOf(groupRatio))
                    .setScale(0, RoundingMode.HALF_UP);
            items.add(new ToolCallItem("image_generation", 1, price, price, quota.intValue()));
            totalQuota = totalQuota.add(quota);
        }

        return new ToolCallResult(totalQuota.intValue(), items);
    }

    /**
     * 添加单项工具计费      *
     * @return [quota BigDecimal, pricePer1K BigDecimal]
     */
    private BigDecimal[] addItem(List<ToolCallItem> items, String toolName, int count,
                                  String modelName, double groupRatio) {
        if (count <= 0) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        double pricePer1K = toolsSettingConfig.getToolPriceForModel(toolName, modelName);
        if (pricePer1K <= 0) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }

        // totalPrice = pricePer1K * count / 1000
        BigDecimal totalPrice = BigDecimal.valueOf(pricePer1K)
                .multiply(BigDecimal.valueOf(count))
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);

        // quota = round(totalPrice * QuotaPerUnit * groupRatio)
        BigDecimal quota = totalPrice
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .multiply(BigDecimal.valueOf(groupRatio))
                .setScale(0, RoundingMode.HALF_UP);

        items.add(new ToolCallItem(toolName, count, pricePer1K, totalPrice.doubleValue(), quota.intValue()));
        return new BigDecimal[]{quota, BigDecimal.valueOf(pricePer1K)};
    }

    /**
     * GPT Image 1 单次调用定价      * <p>
     * 根据 quality + size 组合查表，未匹配时返回 high/1024x1024 的价格。
     */
    private double getGptImage1PriceOnceCall(String quality, String size) {
        Map<String, Map<String, Double>> prices = Map.of(
                "low", Map.of(
                        "1024x1024", 0.011,
                        "1024x1536", 0.016,
                        "1536x1024", 0.016
                ),
                "medium", Map.of(
                        "1024x1024", 0.042,
                        "1024x1536", 0.063,
                        "1536x1024", 0.063
                ),
                "high", Map.of(
                        "1024x1024", 0.167,
                        "1024x1536", 0.25,
                        "1536x1024", 0.25
                )
        );

        if (quality != null && size != null) {
            Map<String, Double> qualityMap = prices.get(quality);
            if (qualityMap != null) {
                Double price = qualityMap.get(size);
                if (price != null) {
                    return price;
                }
            }
        }
        // 默认返回 high/1024x1024
        return 0.167;
    }

    // ======================== POJO ========================

    /**
     * 工具调用使用统计      */
    @lombok.Data
    public static class ToolCallUsage {
        private String modelName;
        private int webSearchCalls;
        private String webSearchToolName;
        private int fileSearchCalls;
        private boolean imageGenerationCall;
        private String imageGenerationQuality;
        private String imageGenerationSize;
    }

    /**
     * 单项工具计费明细      */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolCallItem {
        private String name;
        private int callCount;
        private double pricePer1K;
        private double totalPrice;
        private int quota;
    }

    /**
     * 工具计费汇总结果      */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolCallResult {
        private int totalQuota;
        private List<ToolCallItem> items;
    }
}
