package yaoshu.token.service.openaicompat;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * OpenAI 兼容正则工具  * <p>
 * 提供模型名正则匹配，用于判断是否为 OpenAI 兼容模型。
 */
@Slf4j
public class CompatRegexUtils {

    // OpenAI 模型名正则
    private static final Pattern GPT_PATTERN = Pattern.compile("^gpt-[\\d.-]+");
    private static final Pattern O_SERIES_PATTERN = Pattern.compile("^o[1-4]-?");
    private static final Pattern CHATGPT_PATTERN = Pattern.compile("^chatgpt-");

    /**
     * 检查模型名是否为 GPT 系列
     */
    public static boolean isGPTModel(String modelName) {
        return modelName != null && GPT_PATTERN.matcher(modelName).find();
    }

    /**
     * 检查模型名是否为 O 系列（o1/o3/o4）
     */
    public static boolean isOSeriesModel(String modelName) {
        return modelName != null && O_SERIES_PATTERN.matcher(modelName).find();
    }

    /**
     * 检查模型名是否为 ChatGPT 系列
     */
    public static boolean isChatGPTModel(String modelName) {
        return modelName != null && CHATGPT_PATTERN.matcher(modelName).find();
    }

    /**
     * 检查是否为任意 OpenAI 兼容模型
     */
    public static boolean isOpenAICompatibleModel(String modelName) {
        return isGPTModel(modelName) || isOSeriesModel(modelName) || isChatGPTModel(modelName);
    }
}
