package yaoshu.token.config;

import java.util.List;

/**
 * 推理后缀配置  * <p>
 * 提供推理力度后缀（-high/-low/-medium/-max/-xhigh 等）的解析与修剪方法。
 */
public final class ReasoningSuffixConfig {

    private ReasoningSuffixConfig() {}

    // ======================== 后缀常量 ========================

    /** 通用推理力度后缀 */
    private static final List<String> EFFORT_SUFFIXES = List.of("-max", "-xhigh", "-high", "-medium", "-low", "-minimal");

    /** OpenAI 推理力度后缀 */
    private static final List<String> OPENAI_EFFORT_SUFFIXES = List.of("-high", "-minimal", "-low", "-medium", "-none", "-xhigh");

    /** DeepSeek V4 推理后缀 */
    private static final List<String> DEEPSEEK_V4_EFFORT_SUFFIXES = List.of("-none", "-max");

    // ======================== 通用后缀修剪 ========================

    /**
     * 修剪推理力度后缀      * @return [baseModel, effortLevel, found]
     */
    public static EffortTrim trimEffortSuffix(String modelName) {
        return trimEffortSuffixWithSuffixes(modelName, EFFORT_SUFFIXES);
    }

    /**
     * 按指定后缀列表修剪      */
    public static EffortTrim trimEffortSuffixWithSuffixes(String modelName, List<String> suffixes) {
        if (modelName == null) return new EffortTrim(modelName, "", false);
        for (String suffix : suffixes) {
            if (modelName.endsWith(suffix)) {
                String baseModel = modelName.substring(0, modelName.length() - suffix.length());
                String effort = suffix.substring(1); // 去掉前导 "-"
                return new EffortTrim(baseModel, effort, true);
            }
        }
        return new EffortTrim(modelName, "", false);
    }

    /**
     * 解析 OpenAI 推理力度后缀      * @return [effort, baseModel] — effort 为空表示未匹配
     */
    public static String[] parseOpenAIReasoningEffortFromModelSuffix(String modelName) {
        EffortTrim result = trimEffortSuffixWithSuffixes(modelName, OPENAI_EFFORT_SUFFIXES);
        if (!result.found) return new String[]{"", modelName};
        return new String[]{result.effort, result.baseModel};
    }

    /**
     * 解析 DeepSeek V4 思维后缀      * @return DeepSeekThink 结果（null 表示未匹配）
     */
    public static DeepSeekThink parseDeepSeekV4ThinkingSuffix(String modelName) {
        EffortTrim result = trimEffortSuffixWithSuffixes(modelName, DEEPSEEK_V4_EFFORT_SUFFIXES);
        if (!result.found || result.baseModel == null || !result.baseModel.startsWith("deepseek-v4-")) {
            return null;
        }
        switch (result.effort) {
            case "none": return new DeepSeekThink(result.baseModel, "disabled", "");
            case "max": return new DeepSeekThink(result.baseModel, "enabled", "max");
            default: return null;
        }
    }

    // ======================== 结果类型 ========================

    /** 推理后缀修剪结果 */
    public static class EffortTrim {
        public final String baseModel;
        public final String effort;
        public final boolean found;

        public EffortTrim(String baseModel, String effort, boolean found) {
            this.baseModel = baseModel;
            this.effort = effort;
            this.found = found;
        }
    }

    /** DeepSeek V4 思维后缀解析结果 */
    public static class DeepSeekThink {
        public final String baseModel;
        public final String thinkingType; // "enabled" / "disabled"
        public final String effort;       // "max" / ""

        public DeepSeekThink(String baseModel, String thinkingType, String effort) {
            this.baseModel = baseModel;
            this.thinkingType = thinkingType;
            this.effort = effort;
        }
    }
}
