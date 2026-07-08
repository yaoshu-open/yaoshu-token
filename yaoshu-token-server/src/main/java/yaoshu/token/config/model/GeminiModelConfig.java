package yaoshu.token.config.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini 模型设置 POJO  */
@Data
public class GeminiModelConfig {

    /** 全局单例*/
    private static final GeminiModelConfig INSTANCE = new GeminiModelConfig();

    /** 获取全局单例*/
    public static GeminiModelConfig getInstance() { return INSTANCE; }

    /** 安全过滤设置（模型名→过滤等级），默认 default→OFF */
    private Map<String, String> safetySettings = new LinkedHashMap<>();
    /** API 版本设置（模型名→版本前缀），默认 default→v1beta */
    private Map<String, String> versionSettings = new LinkedHashMap<>();
    /** 支持 Imagen 图像生成的模型列表 */
    private List<String> supportedImagineModels = List.of(
            "gemini-2.0-flash-exp-image-generation", "gemini-2.0-flash-exp",
            "gemini-3-pro-image-preview", "gemini-2.5-flash-image",
            "gemini-3.1-flash-image-preview");
    /** 是否启用 Thinking Adapter */
    private boolean thinkingAdapterEnabled;
    /** Thinking Adapter 预算 token 百分比（默认 0.6） */
    private double thinkingAdapterBudgetTokensPercentage = 0.6;
    /** 是否启用 Function Call 思想签名 */
    private boolean functionCallThoughtSignatureEnabled = true;
    /** 是否启用移除 Function Response ID */
    private boolean removeFunctionResponseIdEnabled = true;

    /**
     * 获取安全设置      */
    public String getGeminiSafetySetting(String key) {
        if (safetySettings.containsKey(key)) return safetySettings.get(key);
        return safetySettings.getOrDefault("default", "OFF");
    }

    /**
     * 获取版本设置      */
    public String getGeminiVersionSetting(String key) {
        if (versionSettings.containsKey(key)) return versionSettings.get(key);
        return versionSettings.getOrDefault("default", "v1beta");
    }

    /**
     * 判断是否 Gemini 模型支持 Imagen 图像生成      */
    public boolean isGeminiModelSupportImagine(String model) {
        return supportedImagineModels.contains(model);
    }
}
