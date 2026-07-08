package yaoshu.token.constant;

import java.util.List;
import java.util.Map;

/**
 * 渠道类型、默认 URL 与名称映射  */
public final class ChannelConstants {

    private ChannelConstants() {
    }

    /* 渠道类型常量 */
    public static final int CHANNEL_TYPE_UNKNOWN = 0;
    public static final int CHANNEL_TYPE_OPENAI = 1;
    public static final int CHANNEL_TYPE_MIDJOURNEY = 2;
    public static final int CHANNEL_TYPE_AZURE = 3;
    public static final int CHANNEL_TYPE_OLLAMA = 4;
    public static final int CHANNEL_TYPE_MIDJOURNEY_PLUS = 5;
    public static final int CHANNEL_TYPE_OPENAI_MAX = 6;
    public static final int CHANNEL_TYPE_OH_MY_GPT = 7;
    public static final int CHANNEL_TYPE_CUSTOM = 8;
    public static final int CHANNEL_TYPE_AILS = 9;
    public static final int CHANNEL_TYPE_AI_PROXY = 10;
    public static final int CHANNEL_TYPE_PALM = 11;
    public static final int CHANNEL_TYPE_API2GPT = 12;
    public static final int CHANNEL_TYPE_AIGC2D = 13;
    public static final int CHANNEL_TYPE_ANTHROPIC = 14;
    public static final int CHANNEL_TYPE_BAIDU = 15;
    public static final int CHANNEL_TYPE_ZHIPU = 16;
    public static final int CHANNEL_TYPE_ALI = 17;
    public static final int CHANNEL_TYPE_XUNFEI = 18;
    public static final int CHANNEL_TYPE_360 = 19;
    public static final int CHANNEL_TYPE_OPEN_ROUTER = 20;
    /** @deprecated 别名，使用 CHANNEL_TYPE_OPEN_ROUTER */
    public static final int CHANNEL_TYPE_OPENROUTER = 20;
    public static final int CHANNEL_TYPE_AI_PROXY_LIBRARY = 21;
    public static final int CHANNEL_TYPE_FAST_GPT = 22;
    public static final int CHANNEL_TYPE_TENCENT = 23;
    public static final int CHANNEL_TYPE_GEMINI = 24;
    public static final int CHANNEL_TYPE_MOONSHOT = 25;
    public static final int CHANNEL_TYPE_ZHIPU_V4 = 26;
    public static final int CHANNEL_TYPE_PERPLEXITY = 27;
    public static final int CHANNEL_TYPE_LING_YI_WAN_WU = 31;
    public static final int CHANNEL_TYPE_AWS = 33;
    public static final int CHANNEL_TYPE_COHERE = 34;
    public static final int CHANNEL_TYPE_MINI_MAX = 35;
    public static final int CHANNEL_TYPE_SUNO_API = 36;
    public static final int CHANNEL_TYPE_DIFY = 37;
    public static final int CHANNEL_TYPE_JINA = 38;
    public static final int CHANNEL_CLOUDFLARE = 39;
    public static final int CHANNEL_TYPE_SILICON_FLOW = 40;
    public static final int CHANNEL_TYPE_VERTEX_AI = 41;
    public static final int CHANNEL_TYPE_MISTRAL = 42;
    public static final int CHANNEL_TYPE_DEEP_SEEK = 43;
    /** @deprecated 别名，使用 CHANNEL_TYPE_DEEP_SEEK */
    public static final int CHANNEL_TYPE_DEEPSEEK = 43;
    public static final int CHANNEL_TYPE_MOKA_AI = 44;
    public static final int CHANNEL_TYPE_VOLC_ENGINE = 45;
    public static final int CHANNEL_TYPE_BAIDU_V2 = 46;
    public static final int CHANNEL_TYPE_XINFERENCE = 47;
    public static final int CHANNEL_TYPE_XAI = 48;
    public static final int CHANNEL_TYPE_COZE = 49;
    public static final int CHANNEL_TYPE_KLING = 50;
    public static final int CHANNEL_TYPE_JIMENG = 51;
    public static final int CHANNEL_TYPE_VIDU = 52;
    public static final int CHANNEL_TYPE_SUBMODEL = 53;
    public static final int CHANNEL_TYPE_DOUBAO_VIDEO = 54;
    public static final int CHANNEL_TYPE_SORA = 55;
    public static final int CHANNEL_TYPE_REPLICATE = 56;
    public static final int CHANNEL_TYPE_CODEX = 57;
    /** 仅用于计数，不可在此之后添加新渠道 */
    public static final int CHANNEL_TYPE_DUMMY = 58;

    // ======================== Azure 相关常量 ========================

    /** Azure 默认 API 版本*/
    public static final String AZURE_DEFAULT_API_VERSION = "2025-04-01-preview";
    /** Azure 移除 dot 时间戳（2025-05-10 UTC）*/
    public static final long AZURE_NO_REMOVE_DOT_TIME = 1746835200L;

    /** 渠道默认 BaseURL（按渠道类型索引，与 Go ChannelBaseURLs 完全一致） */
    public static final List<String> CHANNEL_BASE_URLS = List.of(
            "",                                          // 0
            "https://api.openai.com",                    // 1
            "https://oa.api2d.net",                      // 2
            "",                                          // 3
            "http://localhost:11434",                    // 4
            "https://api.openai-sb.com",                 // 5
            "https://api.openaimax.com",                 // 6
            "https://api.ohmygpt.com",                   // 7
            "",                                          // 8
            "https://api.caipacity.com",                 // 9
            "https://api.aiproxy.io",                    // 10
            "",                                          // 11
            "https://api.api2gpt.com",                   // 12
            "https://api.aigc2d.com",                    // 13
            "https://api.anthropic.com",                 // 14
            "https://aip.baidubce.com",                  // 15
            "https://open.bigmodel.cn",                  // 16
            "https://dashscope.aliyuncs.com",            // 17
            "",                                          // 18
            "https://api.360.cn",                        // 19
            "https://openrouter.ai/api",                 // 20
            "https://api.aiproxy.io",                    // 21
            "https://fastgpt.run/api/openapi",           // 22
            "https://hunyuan.tencentcloudapi.com",       // 23
            "https://generativelanguage.googleapis.com", // 24
            "https://api.moonshot.cn",                   // 25
            "https://open.bigmodel.cn",                  // 26
            "https://api.perplexity.ai",                 // 27
            "",                                          // 28
            "",                                          // 29
            "",                                          // 30
            "https://api.lingyiwanwu.com",               // 31
            "",                                          // 32
            "",                                          // 33
            "https://api.cohere.ai",                     // 34
            "https://api.minimax.chat",                  // 35
            "",                                          // 36
            "https://api.dify.ai",                       // 37
            "https://api.jina.ai",                       // 38
            "https://api.cloudflare.com",                // 39
            "https://api.siliconflow.cn",                // 40
            "",                                          // 41
            "https://api.mistral.ai",                    // 42
            "https://api.deepseek.com",                  // 43
            "https://api.moka.ai",                       // 44
            "https://ark.cn-beijing.volces.com",         // 45
            "https://qianfan.baidubce.com",              // 46
            "",                                          // 47
            "https://api.x.ai",                          // 48
            "https://api.coze.cn",                       // 49
            "https://api.klingai.com",                   // 50
            "https://visual.volcengineapi.com",          // 51
            "https://api.vidu.cn",                       // 52
            "https://llm.submodel.ai",                   // 53
            "https://ark.cn-beijing.volces.com",         // 54
            "https://api.openai.com",                    // 55
            "https://api.replicate.com",                 // 56
            "https://chatgpt.com"                        // 57
    );

    /** 渠道类型 → 名称映射 */
    public static final Map<Integer, String> CHANNEL_TYPE_NAMES = Map.ofEntries(
            Map.entry(CHANNEL_TYPE_UNKNOWN, "Unknown"),
            Map.entry(CHANNEL_TYPE_OPENAI, "OpenAI"),
            Map.entry(CHANNEL_TYPE_MIDJOURNEY, "Midjourney"),
            Map.entry(CHANNEL_TYPE_AZURE, "Azure"),
            Map.entry(CHANNEL_TYPE_OLLAMA, "Ollama"),
            Map.entry(CHANNEL_TYPE_MIDJOURNEY_PLUS, "MidjourneyPlus"),
            Map.entry(CHANNEL_TYPE_OPENAI_MAX, "OpenAIMax"),
            Map.entry(CHANNEL_TYPE_OH_MY_GPT, "OhMyGPT"),
            Map.entry(CHANNEL_TYPE_CUSTOM, "Custom"),
            Map.entry(CHANNEL_TYPE_AILS, "AILS"),
            Map.entry(CHANNEL_TYPE_AI_PROXY, "AIProxy"),
            Map.entry(CHANNEL_TYPE_PALM, "PaLM"),
            Map.entry(CHANNEL_TYPE_API2GPT, "API2GPT"),
            Map.entry(CHANNEL_TYPE_AIGC2D, "AIGC2D"),
            Map.entry(CHANNEL_TYPE_ANTHROPIC, "Anthropic"),
            Map.entry(CHANNEL_TYPE_BAIDU, "Baidu"),
            Map.entry(CHANNEL_TYPE_ZHIPU, "Zhipu"),
            Map.entry(CHANNEL_TYPE_ALI, "Ali"),
            Map.entry(CHANNEL_TYPE_XUNFEI, "Xunfei"),
            Map.entry(CHANNEL_TYPE_360, "360"),
            Map.entry(CHANNEL_TYPE_OPEN_ROUTER, "OpenRouter"),
            Map.entry(CHANNEL_TYPE_AI_PROXY_LIBRARY, "AIProxyLibrary"),
            Map.entry(CHANNEL_TYPE_FAST_GPT, "FastGPT"),
            Map.entry(CHANNEL_TYPE_TENCENT, "Tencent"),
            Map.entry(CHANNEL_TYPE_GEMINI, "Gemini"),
            Map.entry(CHANNEL_TYPE_MOONSHOT, "Moonshot"),
            Map.entry(CHANNEL_TYPE_ZHIPU_V4, "ZhipuV4"),
            Map.entry(CHANNEL_TYPE_PERPLEXITY, "Perplexity"),
            Map.entry(CHANNEL_TYPE_LING_YI_WAN_WU, "LingYiWanWu"),
            Map.entry(CHANNEL_TYPE_AWS, "AWS"),
            Map.entry(CHANNEL_TYPE_COHERE, "Cohere"),
            Map.entry(CHANNEL_TYPE_MINI_MAX, "MiniMax"),
            Map.entry(CHANNEL_TYPE_SUNO_API, "SunoAPI"),
            Map.entry(CHANNEL_TYPE_DIFY, "Dify"),
            Map.entry(CHANNEL_TYPE_JINA, "Jina"),
            Map.entry(CHANNEL_CLOUDFLARE, "Cloudflare"),
            Map.entry(CHANNEL_TYPE_SILICON_FLOW, "SiliconFlow"),
            Map.entry(CHANNEL_TYPE_VERTEX_AI, "VertexAI"),
            Map.entry(CHANNEL_TYPE_MISTRAL, "Mistral"),
            Map.entry(CHANNEL_TYPE_DEEP_SEEK, "DeepSeek"),
            Map.entry(CHANNEL_TYPE_MOKA_AI, "MokaAI"),
            Map.entry(CHANNEL_TYPE_VOLC_ENGINE, "VolcEngine"),
            Map.entry(CHANNEL_TYPE_BAIDU_V2, "BaiduV2"),
            Map.entry(CHANNEL_TYPE_XINFERENCE, "Xinference"),
            Map.entry(CHANNEL_TYPE_XAI, "xAI"),
            Map.entry(CHANNEL_TYPE_COZE, "Coze"),
            Map.entry(CHANNEL_TYPE_KLING, "Kling"),
            Map.entry(CHANNEL_TYPE_JIMENG, "Jimeng"),
            Map.entry(CHANNEL_TYPE_VIDU, "Vidu"),
            Map.entry(CHANNEL_TYPE_SUBMODEL, "Submodel"),
            Map.entry(CHANNEL_TYPE_DOUBAO_VIDEO, "DoubaoVideo"),
            Map.entry(CHANNEL_TYPE_SORA, "Sora"),
            Map.entry(CHANNEL_TYPE_REPLICATE, "Replicate"),
            Map.entry(CHANNEL_TYPE_CODEX, "Codex")
    );

    /** 渠道特殊 BaseURL */
    public static Map.Entry<String, ChannelSpecialBase> entry(String k, String claude, String openai) {
        return Map.entry(k, new ChannelSpecialBase(claude, openai));
    }

    public static final Map<String, ChannelSpecialBase> CHANNEL_SPECIAL_BASES = Map.ofEntries(
            entry("glm-coding-plan", "https://open.bigmodel.cn/api/anthropic", "https://open.bigmodel.cn/api/coding/paas/v4"),
            entry("glm-coding-plan-international", "https://api.z.ai/api/anthropic", "https://api.z.ai/api/coding/paas/v4"),
            entry("kimi-coding-plan", "https://api.kimi.com/coding", "https://api.kimi.com/coding/v1"),
            entry("doubao-coding-plan", "https://ark.cn-beijing.volces.com/api/coding", "https://ark.cn-beijing.volces.com/api/coding/v3")
    );

    /** 获取渠道类型名称 */
    public static String getChannelTypeName(int channelType) {
        return CHANNEL_TYPE_NAMES.getOrDefault(channelType, "Unknown");
    }

    /**
     * 渠道特殊 BaseURL（Claude / OpenAI）
     */
    public record ChannelSpecialBase(String claudeBaseURL, String openAIBaseURL) {
    }
}
