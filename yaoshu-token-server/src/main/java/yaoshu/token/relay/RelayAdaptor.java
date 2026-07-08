package yaoshu.token.relay;

import yaoshu.token.constant.ApiTypeEnum;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.ali.AliAdaptor;
import yaoshu.token.relay.channel.aws.AWSAdaptor;
import yaoshu.token.relay.channel.baidu.BaiduAdaptor;
import yaoshu.token.relay.channel.baiduv2.BaiduV2Adaptor;
import yaoshu.token.relay.channel.claude.ClaudeAdaptor;
import yaoshu.token.relay.channel.cloudflare.CloudflareAdaptor;
import yaoshu.token.relay.channel.codex.CodexAdaptor;
import yaoshu.token.relay.channel.cohere.CohereAdaptor;
import yaoshu.token.relay.channel.coze.CozeAdaptor;
import yaoshu.token.relay.channel.deepseek.DeepSeekAdaptor;
import yaoshu.token.relay.channel.dify.DifyAdaptor;
import yaoshu.token.relay.channel.gemini.GeminiAdaptor;
import yaoshu.token.relay.channel.jimeng.JimengAdaptor;
import yaoshu.token.relay.channel.jina.JinaAdaptor;
import yaoshu.token.relay.channel.minimax.MiniMaxAdaptor;
import yaoshu.token.relay.channel.mistral.MistralAdaptor;
import yaoshu.token.relay.channel.mokaai.MokaAIAdaptor;
import yaoshu.token.relay.channel.moonshot.MoonshotAdaptor;
import yaoshu.token.relay.channel.ollama.OllamaAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.channel.palm.PalmAdaptor;
import yaoshu.token.relay.channel.perplexity.PerplexityAdaptor;
import yaoshu.token.relay.channel.replicate.ReplicateAdaptor;
import yaoshu.token.relay.channel.siliconflow.SiliconFlowAdaptor;
import yaoshu.token.relay.channel.submodel.SubmodelAdaptor;
import yaoshu.token.relay.channel.tencent.TencentAdaptor;
import yaoshu.token.relay.channel.vertex.VertexAdaptor;
import yaoshu.token.relay.channel.volcengine.VolcEngineAdaptor;
import yaoshu.token.relay.channel.xai.XAIAdaptor;
import yaoshu.token.relay.channel.xinference.XinferenceAdaptor;
import yaoshu.token.relay.channel.xunfei.XunfeiAdaptor;
import yaoshu.token.relay.channel.zhipu.ZhipuAdaptor;
import yaoshu.token.relay.channel.zhipu4v.Zhipu4VAdaptor;
import yaoshu.token.relay.channel.task.ali.TaskAliAdaptor;
import yaoshu.token.relay.channel.task.doubao.TaskDoubaoAdaptor;
import yaoshu.token.relay.channel.task.gemini.TaskGeminiAdaptor;
import yaoshu.token.relay.channel.task.hailuo.TaskHailuoAdaptor;
import yaoshu.token.relay.channel.task.jimeng.TaskJimengAdaptor;
import yaoshu.token.relay.channel.task.kling.TaskKlingAdaptor;
import yaoshu.token.relay.channel.task.sora.TaskSoraAdaptor;
import yaoshu.token.relay.channel.task.suno.TaskSunoAdaptor;
import yaoshu.token.relay.channel.task.vertex.TaskVertexAdaptor;
import yaoshu.token.relay.channel.task.vidu.TaskViduAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.Locale;

/**
 * 渠道适配器工厂  * <p>
 * 根据 API 类型 / Task 平台 创建对应的适配器实例。
 * 33 个同步适配器与当前已翻译的异步 Task 适配器均在此统一注册。
 */
public final class RelayAdaptor {

    private RelayAdaptor() {
    }

    /**
     * 根据渠道类型获取同步渠道适配器      * <p>
     * apiType 直接使用 {@link ChannelConstants} 的 CHANNEL_TYPE_* 常量值（如 CHANNEL_TYPE_OPENAI=1）。
     * 注意：{@link ApiTypeEnum} 的 code 字段是 Go iota 原始值（从 0 开始），与渠道表的 type 字段编号不同。
     */
    public static IAdaptor getAdaptor(int apiType) {
        return switch (apiType) {
            case ChannelConstants.CHANNEL_TYPE_OPENAI       -> new OpenAIAdaptor();
            case ChannelConstants.CHANNEL_TYPE_ALI          -> new AliAdaptor();
            case ChannelConstants.CHANNEL_TYPE_ANTHROPIC    -> new ClaudeAdaptor();
            case ChannelConstants.CHANNEL_TYPE_AWS          -> new AWSAdaptor();
            case ChannelConstants.CHANNEL_TYPE_BAIDU        -> new BaiduAdaptor();
            case ChannelConstants.CHANNEL_TYPE_BAIDU_V2     -> new BaiduV2Adaptor();
            case ChannelConstants.CHANNEL_CLOUDFLARE        -> new CloudflareAdaptor();
            case ChannelConstants.CHANNEL_TYPE_CODEX        -> new CodexAdaptor();
            case ChannelConstants.CHANNEL_TYPE_COHERE       -> new CohereAdaptor();
            case ChannelConstants.CHANNEL_TYPE_COZE         -> new CozeAdaptor();
            case ChannelConstants.CHANNEL_TYPE_DEEP_SEEK    -> new DeepSeekAdaptor();
            case ChannelConstants.CHANNEL_TYPE_DIFY         -> new DifyAdaptor();
            case ChannelConstants.CHANNEL_TYPE_GEMINI       -> new GeminiAdaptor();
            case ChannelConstants.CHANNEL_TYPE_JIMENG       -> new JimengAdaptor();
            case ChannelConstants.CHANNEL_TYPE_JINA         -> new JinaAdaptor();
            case ChannelConstants.CHANNEL_TYPE_MINI_MAX     -> new MiniMaxAdaptor();
            case ChannelConstants.CHANNEL_TYPE_MISTRAL      -> new MistralAdaptor();
            case ChannelConstants.CHANNEL_TYPE_MOKA_AI      -> new MokaAIAdaptor();
            case ChannelConstants.CHANNEL_TYPE_MOONSHOT     -> new MoonshotAdaptor();
            case ChannelConstants.CHANNEL_TYPE_OLLAMA       -> new OllamaAdaptor();
            case ChannelConstants.CHANNEL_TYPE_OPEN_ROUTER  -> new OpenAIAdaptor(); // 复用 OpenAI
            case ChannelConstants.CHANNEL_TYPE_PALM         -> new PalmAdaptor();
            case ChannelConstants.CHANNEL_TYPE_PERPLEXITY   -> new PerplexityAdaptor();
            case ChannelConstants.CHANNEL_TYPE_REPLICATE    -> new ReplicateAdaptor();
            case ChannelConstants.CHANNEL_TYPE_SILICON_FLOW -> new SiliconFlowAdaptor();
            case ChannelConstants.CHANNEL_TYPE_SUBMODEL     -> new SubmodelAdaptor();
            case ChannelConstants.CHANNEL_TYPE_TENCENT      -> new TencentAdaptor();
            case ChannelConstants.CHANNEL_TYPE_VERTEX_AI    -> new VertexAdaptor();
            case ChannelConstants.CHANNEL_TYPE_VOLC_ENGINE  -> new VolcEngineAdaptor();
            case ChannelConstants.CHANNEL_TYPE_XAI          -> new XAIAdaptor();
            case ChannelConstants.CHANNEL_TYPE_XINFERENCE   -> new XinferenceAdaptor(); // 复用 OpenAI
            case ChannelConstants.CHANNEL_TYPE_XUNFEI       -> new XunfeiAdaptor();
            case ChannelConstants.CHANNEL_TYPE_ZHIPU        -> new ZhipuAdaptor();
            case ChannelConstants.CHANNEL_TYPE_ZHIPU_V4     -> new Zhipu4VAdaptor();
            // CHANNEL_TYPE_AI_PROXY_LIBRARY / CHANNEL_TYPE_DUMMY 不注册适配器
            default                                         -> null;
        };
    }

    /**
     * 根据平台获取异步 Task 适配器      */
    public static IAdaptor.ITaskAdaptor getTaskAdaptor(String platform) {
        if (platform == null) {
            return null;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        return switch (normalized) {
            case "suno" -> new TaskSunoAdaptor();
            case "ali" -> new TaskAliAdaptor();
            case "kling" -> new TaskKlingAdaptor();
            case "jimeng" -> new TaskJimengAdaptor();
            case "vertex" -> new TaskVertexAdaptor();
            case "vidu" -> new TaskViduAdaptor();
            case "doubao", "volc", "volcengine" -> new TaskDoubaoAdaptor();
            case "sora", "openai" -> new TaskSoraAdaptor();
            case "gemini", "gemini_task" -> new TaskGeminiAdaptor();
            case "hailuo", "minimax" -> new TaskHailuoAdaptor();
            default -> getTaskAdaptorByChannelType(normalized);
        };
    }

    private static IAdaptor.ITaskAdaptor getTaskAdaptorByChannelType(String platform) {
        try {
            int channelType = Integer.parseInt(platform);
            return switch (channelType) {
                case ChannelConstants.CHANNEL_TYPE_OPENAI, ChannelConstants.CHANNEL_TYPE_SORA -> new TaskSoraAdaptor();
                case ChannelConstants.CHANNEL_TYPE_ALI -> new TaskAliAdaptor();
                case ChannelConstants.CHANNEL_TYPE_GEMINI -> new TaskGeminiAdaptor();
                case ChannelConstants.CHANNEL_TYPE_MINI_MAX -> new TaskHailuoAdaptor();
                case ChannelConstants.CHANNEL_TYPE_VERTEX_AI -> new TaskVertexAdaptor();
                case ChannelConstants.CHANNEL_TYPE_VOLC_ENGINE, ChannelConstants.CHANNEL_TYPE_DOUBAO_VIDEO -> new TaskDoubaoAdaptor();
                case ChannelConstants.CHANNEL_TYPE_KLING -> new TaskKlingAdaptor();
                case ChannelConstants.CHANNEL_TYPE_JIMENG -> new TaskJimengAdaptor();
                case ChannelConstants.CHANNEL_TYPE_VIDU -> new TaskViduAdaptor();
                default -> null;
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
