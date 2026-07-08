package yaoshu.token.relay.helper;

import yaoshu.token.config.ratio.AudioCompletionRatioConfig;
import yaoshu.token.config.ratio.AudioRatioConfig;
import yaoshu.token.config.ratio.CacheRatioConfig;
import yaoshu.token.config.ratio.CompletionRatioConfig;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.config.ratio.ImageRatioConfig;
import yaoshu.token.config.ratio.ModelPriceConfig;
import yaoshu.token.config.ratio.ModelRatioConfig;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.GroupRatioInfo;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.relay.common.RelayInfo;

/**
 * 价格计算辅助  * <p>
 * 核心流程：分组倍率 → 模型倍率 → 模型价格 → 完成倍率 → 缓存倍率 → 图片/音频倍率 → 预消耗额度。
 */
public final class PriceHelper {

    private PriceHelper() {}

    /** Claude 1h 缓存写入倍数 */
    private static final double CLAUDE_CREATION_1H_MULTIPLIER = 6.0 / 3.75;

    // ======================== 分组倍率 ========================

    /**
     * 处理分组倍率（含 auto_group）      */
    public static GroupRatioInfo handleGroupRatio(RelayInfo info) {
        GroupRatioInfo groupRatioInfo = new GroupRatioInfo(1.0, -1, false);

        // auto_group 赋值到 usingGroup
        String autoGroup = info.getUsingGroup();

        // 检查 userGroup 特殊倍率
        String userGroup = info.getUserGroup();
        String usingGroup = info.getUsingGroup();
        if (userGroup != null && usingGroup != null) {
            double specialRatio = GroupRatioConfig.getGroupGroupRatio(userGroup, usingGroup);
            if (specialRatio >= 0) {
                groupRatioInfo = new GroupRatioInfo(specialRatio, specialRatio, true);
            } else {
                double normalRatio = GroupRatioConfig.getGroupRatio(usingGroup);
                groupRatioInfo = new GroupRatioInfo(normalRatio, -1, false);
            }
        } else if (usingGroup != null) {
            double normalRatio = GroupRatioConfig.getGroupRatio(usingGroup);
            groupRatioInfo = new GroupRatioInfo(normalRatio, -1, false);
        }

        return groupRatioInfo;
    }

    // ======================== 按 Token 计费 ========================

    /**
     * 按 Token 计费价格计算      */
    public static PriceData modelPriceHelper(RelayInfo info, int promptTokens, int maxTokens) {
        String modelName = info.getOriginModelName();
        GroupRatioInfo groupRatioInfo = handleGroupRatio(info);

        // 获取模型价格
        double modelPrice = getModelPrice(modelName);

        double modelRatio;
        double completionRatio = 2.0;
        double cacheRatio = 1.0;
        double imageRatio = 1.0;
        double audioRatio = 1.0;
        double audioCompletionRatio = 1.0;
        double cacheCreationRatio = 1.0;
        double cacheCreation5mRatio = 1.0;
        double cacheCreation1hRatio = 1.0;
        boolean freeModel = false;
        boolean usePrice = modelPrice >= 0;
        int preConsumedQuota;

        if (!usePrice) {
            // 按 Token 倍率计费
            int preConsumedTokens = Math.max(promptTokens, CommonConstants.quotaPerUnit > 0 ? 500 : 500);
            if (maxTokens > 0) {
                preConsumedTokens += maxTokens;
            }

            modelRatio = getModelRatioFailFast(modelName);
            completionRatio = getCompletionRatio(modelName);
            cacheRatio = getCacheRatio(modelName);
            imageRatio = getImageRatio(modelName);
            audioRatio = getAudioRatio(modelName);
            audioCompletionRatio = getAudioCompletionRatio(modelName);
            cacheCreationRatio = getCreateCacheRatio(modelName);
            cacheCreation5mRatio = cacheCreationRatio;
            cacheCreation1hRatio = cacheCreationRatio * CLAUDE_CREATION_1H_MULTIPLIER;

            double ratio = modelRatio * groupRatioInfo.getGroupRatio();
            preConsumedQuota = (int) (preConsumedTokens * ratio);
        } else {
            // 按价格计费
            preConsumedQuota = (int) (modelPrice * CommonConstants.quotaPerUnit * groupRatioInfo.getGroupRatio());
            modelRatio = 0;
        }

        // free model 检测
        if (groupRatioInfo.getGroupRatio() == 0) {
            preConsumedQuota = 0;
            freeModel = true;
        }

        PriceData priceData = new PriceData();
        priceData.setFreeModel(freeModel);
        priceData.setModelPrice(modelPrice);
        priceData.setModelRatio(modelRatio);
        priceData.setCompletionRatio(completionRatio);
        priceData.setCacheRatio(cacheRatio);
        priceData.setImageRatio(imageRatio);
        priceData.setAudioRatio(audioRatio);
        priceData.setAudioCompletionRatio(audioCompletionRatio);
        priceData.setCacheCreationRatio(cacheCreationRatio);
        priceData.setCacheCreation5mRatio(cacheCreation5mRatio);
        priceData.setCacheCreation1hRatio(cacheCreation1hRatio);
        priceData.setUsePrice(usePrice);
        priceData.setQuotaToPreConsume(preConsumedQuota);
        priceData.setGroupRatioInfo(groupRatioInfo);

        info.setPriceData(priceData);
        return priceData;
    }

    // ======================== 按次计费 ========================

    /**
     * 按次计费价格计算      */
    public static PriceData modelPriceHelperPerCall(RelayInfo info) {
        String modelName = info.getOriginModelName();
        GroupRatioInfo groupRatioInfo = handleGroupRatio(info);

        double modelPrice = getModelPrice(modelName);
        boolean usePrice = modelPrice >= 0;
        double modelRatio = 0;
        int quota;
        boolean freeModel = false;

        if (usePrice) {
            quota = (int) (modelPrice * CommonConstants.quotaPerUnit * groupRatioInfo.getGroupRatio());
            if (groupRatioInfo.getGroupRatio() == 0 || modelPrice == 0) {
                quota = 0;
                freeModel = true;
            }
        } else {
            modelRatio = getModelRatioFailFast(modelName);
            quota = (int) (modelRatio / 2 * CommonConstants.quotaPerUnit * groupRatioInfo.getGroupRatio());
            modelPrice = -1;
            if (groupRatioInfo.getGroupRatio() == 0 || modelRatio == 0) {
                quota = 0;
                freeModel = true;
            }
        }

        PriceData priceData = new PriceData();
        priceData.setFreeModel(freeModel);
        priceData.setModelPrice(modelPrice);
        priceData.setModelRatio(modelRatio);
        priceData.setUsePrice(usePrice);
        priceData.setQuota(quota);
        priceData.setGroupRatioInfo(groupRatioInfo);

        info.setPriceData(priceData);
        return priceData;
    }

    // ======================== 比率获取方法 ========================

    /**
     * 获取模型倍率（fail-fast 版本）— 计费扣费点专用。
     * <p>
     * 未配置模型倍率时直接抛 RelayException 拒绝请求，避免按 37.5 兜底倍率（$75/M）静默多扣费。
     * 管理员需在管理后台 ModelRatio 配置中为该模型设置倍率后方可使用。
     *
     * @param modelName 模型名
     * @return 模型倍率
     * @throws RelayException 模型未配置倍率时抛出（errorCode=MODEL_NOT_FOUND）
     */
    private static double getModelRatioFailFast(String modelName) {
        Double ratio = ModelRatioConfig.getModelRatioOrNull(modelName);
        if (ratio == null) {
            throw new RelayException(
                    "模型 " + modelName + " 未配置计费倍率，请联系管理员在管理后台配置 ModelRatio",
                    ErrorCode.MODEL_NOT_FOUND);
        }
        return ratio;
    }

    /** 获取模型价格*/
    public static double getModelPrice(String modelName) {
        return ModelPriceConfig.getModelPrice(modelName);
    }

    /** 获取模型倍率*/
    public static double getModelRatio(String modelName) {
        return ModelRatioConfig.getModelRatio(modelName);
    }

    /** 获取完成倍率*/
    public static double getCompletionRatio(String modelName) {
        return CompletionRatioConfig.getCompletionRatio(modelName);
    }

    /** 获取缓存倍率*/
    public static double getCacheRatio(String modelName) {
        Double ratio = CacheRatioConfig.getCacheRatio(modelName);
        return ratio != null ? ratio : 1.0;
    }

    /** 获取缓存写入倍率*/
    public static double getCreateCacheRatio(String modelName) {
        Double ratio = CacheRatioConfig.getCreateCacheRatio(modelName);
        return ratio != null ? ratio : 1.0;
    }

    /** 获取图片倍率*/
    public static double getImageRatio(String modelName) {
        return ImageRatioConfig.getImageRatio(modelName);
    }

    /** 获取音频倍率*/
    public static double getAudioRatio(String modelName) {
        return AudioRatioConfig.getAudioRatio(modelName);
    }

    /** 获取音频完成倍率*/
    public static double getAudioCompletionRatio(String modelName) {
        return AudioCompletionRatioConfig.getAudioCompletionRatio(modelName);
    }
}
