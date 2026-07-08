package yaoshu.token.config.operation;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具定价设置 POJO  */
@Data
@Component
public class ToolsSettingConfig {

    /** 工具名称 → 定价（$） */
    private Map<String, Double> toolPrices;
    /** Web 搜索定价 */
    private double webSearchPrice = 0.005;
    /** 文件搜索定价 */
    private double fileSearchPrice = 0.005;
    /** 图片生成定价 */
    private double imageGenerationPrice = 0.04;
    /** 音频输入定价 */
    private double audioInputPrice = 0.006;

    /** 查询工具定价 */
    public double getToolPriceForModel(String toolName, String modelName) {
        if (toolPrices != null && toolPrices.containsKey(toolName)) {
            return toolPrices.get(toolName);
        }
        switch (toolName) {
            case "web_search_preview": return webSearchPrice;
            case "file_search": return fileSearchPrice;
            case "image_generation": return imageGenerationPrice;
            case "audio_input": return audioInputPrice;
            default: return 0;
        }
    }
}
