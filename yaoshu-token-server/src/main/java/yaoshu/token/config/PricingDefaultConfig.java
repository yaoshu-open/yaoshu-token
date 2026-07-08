package yaoshu.token.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认定价与供应商映射配置  * <p>
 * 提供模型名称到供应商的模糊匹配规则，以及供应商默认图标映射。
 *
 * @author yaoshu
 */
public final class PricingDefaultConfig {

    private PricingDefaultConfig() {
    }

    /**
     * 模型名称关键词 → 供应商名称 的简化映射规则。
     * key 使用小写匹配。
     */
    public static final Map<String, String> DEFAULT_VENDOR_RULES = new LinkedHashMap<>();

    /**
     * 供应商名称 → 默认图标名称
     */
    public static final Map<String, String> DEFAULT_VENDOR_ICONS = new LinkedHashMap<>();

    static {
        // 模型名称关键词 → 供应商
        DEFAULT_VENDOR_RULES.put("gpt", "OpenAI");
        DEFAULT_VENDOR_RULES.put("dall-e", "OpenAI");
        DEFAULT_VENDOR_RULES.put("whisper", "OpenAI");
        DEFAULT_VENDOR_RULES.put("o1", "OpenAI");
        DEFAULT_VENDOR_RULES.put("o3", "OpenAI");
        DEFAULT_VENDOR_RULES.put("claude", "Anthropic");
        DEFAULT_VENDOR_RULES.put("gemini", "Gemini");
        DEFAULT_VENDOR_RULES.put("moonshot", "Moonshot");
        DEFAULT_VENDOR_RULES.put("kimi", "Moonshot");
        DEFAULT_VENDOR_RULES.put("chatglm", "智谱");
        DEFAULT_VENDOR_RULES.put("glm-", "智谱");
        DEFAULT_VENDOR_RULES.put("qwen", "通义千问");
        DEFAULT_VENDOR_RULES.put("deepseek", "DeepSeek");
        DEFAULT_VENDOR_RULES.put("abab", "MiniMax");
        DEFAULT_VENDOR_RULES.put("ernie", "文心一言");
        DEFAULT_VENDOR_RULES.put("spark", "星火");
        DEFAULT_VENDOR_RULES.put("hunyuan", "混元");
        DEFAULT_VENDOR_RULES.put("command", "Cohere");
        DEFAULT_VENDOR_RULES.put("@cf/", "Cloudflare");
        DEFAULT_VENDOR_RULES.put("360", "360");
        DEFAULT_VENDOR_RULES.put("yi", "Yi");
        DEFAULT_VENDOR_RULES.put("jina", "Jina");
        DEFAULT_VENDOR_RULES.put("mistral", "Mistral");
        DEFAULT_VENDOR_RULES.put("grok", "xAI");
        DEFAULT_VENDOR_RULES.put("llama", "Meta");
        DEFAULT_VENDOR_RULES.put("doubao", "豆包");
        DEFAULT_VENDOR_RULES.put("kling", "可灵");
        DEFAULT_VENDOR_RULES.put("jimeng", "即梦");
        DEFAULT_VENDOR_RULES.put("vidu", "Vidu");

        // 供应商 → 默认图标
        DEFAULT_VENDOR_ICONS.put("OpenAI", "OpenAI");
        DEFAULT_VENDOR_ICONS.put("Anthropic", "Claude.Color");
        DEFAULT_VENDOR_ICONS.put("Gemini", "Gemini.Color");
        DEFAULT_VENDOR_ICONS.put("Moonshot", "Moonshot");
        DEFAULT_VENDOR_ICONS.put("智谱", "Zhipu.Color");
        DEFAULT_VENDOR_ICONS.put("通义千问", "Qwen.Color");
        DEFAULT_VENDOR_ICONS.put("DeepSeek", "DeepSeek.Color");
        DEFAULT_VENDOR_ICONS.put("MiniMax", "Minimax.Color");
        DEFAULT_VENDOR_ICONS.put("文心一言", "Wenxin.Color");
        DEFAULT_VENDOR_ICONS.put("星火", "Spark.Color");
        DEFAULT_VENDOR_ICONS.put("混元", "Hunyuan.Color");
        DEFAULT_VENDOR_ICONS.put("Cohere", "Cohere.Color");
        DEFAULT_VENDOR_ICONS.put("Cloudflare", "Cloudflare.Color");
        DEFAULT_VENDOR_ICONS.put("360", "Ai360.Color");
        DEFAULT_VENDOR_ICONS.put("Yi", "Yi.Color");
        DEFAULT_VENDOR_ICONS.put("Jina", "Jina");
        DEFAULT_VENDOR_ICONS.put("Mistral", "Mistral.Color");
        DEFAULT_VENDOR_ICONS.put("xAI", "XAI");
        DEFAULT_VENDOR_ICONS.put("Meta", "Ollama");
        DEFAULT_VENDOR_ICONS.put("豆包", "Doubao.Color");
        DEFAULT_VENDOR_ICONS.put("可灵", "Kling.Color");
        DEFAULT_VENDOR_ICONS.put("即梦", "Jimeng.Color");
        DEFAULT_VENDOR_ICONS.put("Vidu", "Vidu");
        DEFAULT_VENDOR_ICONS.put("微软", "AzureAI");
        DEFAULT_VENDOR_ICONS.put("Microsoft", "AzureAI");
        DEFAULT_VENDOR_ICONS.put("Azure", "AzureAI");
    }
}
