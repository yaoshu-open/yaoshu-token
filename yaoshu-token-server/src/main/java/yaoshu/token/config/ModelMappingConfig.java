package yaoshu.token.config;

import java.util.List;

/**
 * 模型匹配判断配置  * <p>
 * 用于判断模型名称属于哪个类别（OpenAI 文本/图像生成/Response Only 等）
 */
public final class ModelMappingConfig {

    private ModelMappingConfig() {
    }

    /** 仅支持 OpenAI Response 端点的模型（子串匹配） */
    private static final List<String> OPENAI_RESPONSE_ONLY_MODELS = List.of(
            "o3-pro",
            "o3-deep-research",
            "o4-mini-deep-research"
    );

    /** 图像生成模型（子串匹配 + "prefix:" 前缀匹配） */
    private static final List<String> IMAGE_GENERATION_MODELS = List.of(
            "dall-e-3",
            "dall-e-2",
            "gpt-image-1",
            "prefix:imagen-",
            "flux-",
            "flux.1-"
    );

    /** OpenAI 文本模型（子串匹配） */
    private static final List<String> OPENAI_TEXT_MODELS = List.of(
            "gpt-",
            "o1",
            "o3",
            "o4",
            "chatgpt"
    );

    /**
     * 判断模型是否仅支持 OpenAI Response 端点
     */
    public static boolean isOpenAIResponseOnlyModel(String modelName) {
        for (String m : OPENAI_RESPONSE_ONLY_MODELS) {
            if (modelName.contains(m)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型是否为图像生成模型
     * <p>
     * 支持 "prefix:" 前缀匹配——如 "prefix:imagen-" 匹配所有以 "imagen-" 开头的模型名
     */
    public static boolean isImageGenerationModel(String modelName) {
        String lower = modelName.toLowerCase();
        for (String m : IMAGE_GENERATION_MODELS) {
            if (m.startsWith("prefix:")) {
                String prefix = m.substring("prefix:".length());
                if (lower.startsWith(prefix)) {
                    return true;
                }
            } else if (lower.contains(m)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型是否为 OpenAI 文本模型
     */
    public static boolean isOpenAITextModel(String modelName) {
        String lower = modelName.toLowerCase();
        for (String m : OPENAI_TEXT_MODELS) {
            if (lower.contains(m)) {
                return true;
            }
        }
        return false;
    }
}
