package yaoshu.token.config.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Claude 模型设置 POJO  */
@Data
public class ClaudeModelConfig {

    /** 全局单例*/
    private static final ClaudeModelConfig INSTANCE = new ClaudeModelConfig();

    /** 获取全局单例*/
    public static ClaudeModelConfig getInstance() { return INSTANCE; }

    /** 请求头设置（原始模型名→{头名→[值列表]}） */
    private Map<String, Map<String, String[]>> headersSettings = new LinkedHashMap<>();
    /** 默认最大 Token 数（模型名→token数），必须含 default 键 */
    private Map<String, Integer> defaultMaxTokens = new LinkedHashMap<>();
    /** 是否启用 Thinking Adapter */
    private boolean thinkingAdapterEnabled = true;
    /** Thinking Adapter 预算 token 百分比（默认 0.8） */
    private double thinkingAdapterBudgetTokensPercentage = 0.8;

    /**
     * 获取模型的最大 Token 默认值      */
    public int getDefaultMaxTokens(String model) {
        if (defaultMaxTokens.containsKey(model)) {
            return defaultMaxTokens.get(model);
        }
        return defaultMaxTokens.getOrDefault("default", 8192);
    }

    /**
     * 注入模型对应的 Claude 请求头设置      * <p>
     * 从 headersSettings 中匹配模型前缀，将配置的请求头写入目标 Map。
     *
     * @param modelName 上游模型名
     * @param targetHeaders 目标请求头 Map（会被原地修改）
     */
    public void writeHeaders(String modelName, java.util.Map<String, String> targetHeaders) {
        if (headersSettings == null || headersSettings.isEmpty() || modelName == null) return;
        for (Map.Entry<String, Map<String, String[]>> entry : headersSettings.entrySet()) {
            String prefix = entry.getKey();
            if (modelName.startsWith(prefix)) {
                Map<String, String[]> headers = entry.getValue();
                if (headers != null) {
                    headers.forEach((name, values) -> {
                        if (values != null && values.length > 0) {
                            // 合并多个值，以逗号分隔（Go: http.Header.Set 覆盖，Add 追加）
                            targetHeaders.put(name, String.join(", ", values));
                        }
                    });
                }
                break;
            }
        }
    }
}
