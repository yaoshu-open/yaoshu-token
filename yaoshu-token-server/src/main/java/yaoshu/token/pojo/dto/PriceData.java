package yaoshu.token.pojo.dto;

import lombok.Data;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 价格数据（计费核心）  */
@Data
public class PriceData {

    private boolean freeModel;
    private double modelPrice;
    private double modelRatio;
    private double completionRatio;
    private double cacheRatio;
    private double cacheCreationRatio;
    private double cacheCreation5mRatio;
    private double cacheCreation1hRatio;
    private double imageRatio;
    private double audioRatio;
    private double audioCompletionRatio;
    private Map<String, Double> otherRatios;
    private boolean usePrice;
    /** 按次计费的最终额度（MJ / Task） */
    private int quota;
    /** 按量计费的预消耗额度 */
    private int quotaToPreConsume;
    private GroupRatioInfo groupRatioInfo;

    public void addOtherRatio(String key, double ratio) {
        if (otherRatios == null) {
            otherRatios = new LinkedHashMap<>();
        }
        if (ratio <= 0) {
            return;
        }
        otherRatios.put(key, ratio);
    }

    public String toSetting() {
        return String.format(
                "ModelPrice: %f, ModelRatio: %f, CompletionRatio: %f, CacheRatio: %f, GroupRatio: %f, UsePrice: %s, CacheCreationRatio: %f, CacheCreation5mRatio: %f, CacheCreation1hRatio: %f, QuotaToPreConsume: %d, ImageRatio: %f, AudioRatio: %f, AudioCompletionRatio: %f",
                modelPrice, modelRatio, completionRatio, cacheRatio,
                groupRatioInfo != null ? groupRatioInfo.getGroupRatio() : 0.0,
                usePrice, cacheCreationRatio, cacheCreation5mRatio, cacheCreation1hRatio,
                quotaToPreConsume, imageRatio, audioRatio, audioCompletionRatio);
    }
}
