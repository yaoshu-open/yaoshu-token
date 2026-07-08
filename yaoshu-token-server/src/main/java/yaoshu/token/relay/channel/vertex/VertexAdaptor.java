package yaoshu.token.relay.channel.vertex;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.claude.ClaudeAdaptor;
import yaoshu.token.relay.channel.gemini.GeminiAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.channel.vertex.VertexServiceAccountHelper.Credentials;
import yaoshu.token.relay.common.RelayInfo;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Vertex AI 渠道适配器  * <p>
 * 支持三种请求模式：Claude（Anthropic Publisher）、Gemini（Google Publisher）、OpenSource（开源模型）。
 * 认证方式：Service Account JWT 换 access_token，或 API Key 直连。
 */
@SuppressWarnings("unchecked")
@Slf4j
public class VertexAdaptor extends OpenAIAdaptor {    private static final String ANTHROPIC_VERSION = "vertex-2023-10-16";

    /** Claude 模型名 → Vertex 模型名映射 */
    private static final Map<String, String> CLAUDE_MODEL_MAP = new LinkedHashMap<>();
    static {
        CLAUDE_MODEL_MAP.put("claude-3-sonnet-20240229", "claude-3-sonnet@20240229");
        CLAUDE_MODEL_MAP.put("claude-3-opus-20240229", "claude-3-opus@20240229");
        CLAUDE_MODEL_MAP.put("claude-3-haiku-20240307", "claude-3-haiku@20240307");
        CLAUDE_MODEL_MAP.put("claude-3-5-sonnet-20240620", "claude-3-5-sonnet@20240620");
        CLAUDE_MODEL_MAP.put("claude-3-5-sonnet-20241022", "claude-3-5-sonnet-v2@20241022");
        CLAUDE_MODEL_MAP.put("claude-3-7-sonnet-20250219", "claude-3-7-sonnet@20250219");
        CLAUDE_MODEL_MAP.put("claude-sonnet-4-20250514", "claude-sonnet-4@20250514");
        CLAUDE_MODEL_MAP.put("claude-opus-4-20250514", "claude-opus-4@20250514");
        CLAUDE_MODEL_MAP.put("claude-opus-4-1-20250805", "claude-opus-4-1@20250805");
        CLAUDE_MODEL_MAP.put("claude-sonnet-4-5-20250929", "claude-sonnet-4-5@20250929");
        CLAUDE_MODEL_MAP.put("claude-haiku-4-5-20251001", "claude-haiku-4-5@20251001");
        CLAUDE_MODEL_MAP.put("claude-opus-4-5-20251101", "claude-opus-4-5@20251101");
    }

    private int requestMode = VertexRelayHandler.REQUEST_MODE_GEMINI;
    private Credentials accountCredentials;

    @Override
    public void init(RelayInfo info) {
        super.init(info);
        String upstreamModel = info.getUpstreamModelName();
        if (upstreamModel != null && upstreamModel.startsWith("claude")) {
            requestMode = VertexRelayHandler.REQUEST_MODE_CLAUDE;
        } else if (upstreamModel != null && (upstreamModel.contains("llama") || upstreamModel.contains("-maas"))) {
            requestMode = VertexRelayHandler.REQUEST_MODE_OPEN_SOURCE;
        } else {
            requestMode = VertexRelayHandler.REQUEST_MODE_GEMINI;
        }
    }

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String suffix = "";
        String model = info.getUpstreamModelName();

        if (requestMode == VertexRelayHandler.REQUEST_MODE_GEMINI) {
            if (info.isStream()) {
                suffix = "streamGenerateContent?alt=sse";
            } else {
                suffix = "generateContent";
            }
            if (model != null && model.startsWith("imagen")) {
                suffix = "predict";
            }
        } else if (requestMode == VertexRelayHandler.REQUEST_MODE_CLAUDE) {
            if (info.isStream()) {
                suffix = "streamRawPredict?alt=sse";
            } else {
                suffix = "rawPredict";
            }
            model = CLAUDE_MODEL_MAP.getOrDefault(info.getUpstreamModelName(), info.getUpstreamModelName());
        }

        return buildRequestUrl(info, model, suffix);
    }

    /**
     * 构建请求 URL      */
    private String buildRequestUrl(RelayInfo info, String model, String suffix) throws Exception {
        String region = VertexDTOPlaceholder.getModelRegion(info.getApiVersion(), info.getOriginModelName());

        // 判断是否使用 API Key 模式
        boolean useApiKey = false;
        if (info.getChannelOtherSettings() != null && info.getChannelOtherSettings().getVertexKeyType() != null) {
            useApiKey = "api_key".equals(info.getChannelOtherSettings().getVertexKeyType());
        }

        if (!useApiKey) {
            // Service Account 模式：解析凭证
            try {
                accountCredentials = Convert.toJavaBean(info.getApiKey(), Credentials.class);
            } catch (Exception e) {
                throw new RuntimeException("failed to decode credentials file: " + e.getMessage(), e);
            }

            String projectId = accountCredentials.getProjectId();
            if (requestMode == VertexRelayHandler.REQUEST_MODE_GEMINI) {
                return VertexUrlBuilder.buildGoogleModelURL(
                        info.getChannelBaseUrl(), VertexUrlBuilder.DEFAULT_API_VERSION, projectId, region, model, suffix);
            } else if (requestMode == VertexRelayHandler.REQUEST_MODE_CLAUDE) {
                return VertexUrlBuilder.buildAnthropicModelURL(
                        info.getChannelBaseUrl(), VertexUrlBuilder.DEFAULT_API_VERSION, projectId, region, model, suffix);
            } else if (requestMode == VertexRelayHandler.REQUEST_MODE_OPEN_SOURCE) {
                return VertexUrlBuilder.buildOpenSourceChatCompletionsURL(
                        info.getChannelBaseUrl(), projectId, region);
            }
        } else {
            // API Key 模式
            String keyPrefix = suffix.endsWith("?alt=sse") ? "&" : "?";
            if (requestMode == VertexRelayHandler.REQUEST_MODE_GEMINI) {
                return VertexUrlBuilder.buildGoogleModelURL(
                        info.getChannelBaseUrl(), VertexUrlBuilder.DEFAULT_API_VERSION, "", region, model, suffix)
                        + keyPrefix + "key=" + info.getApiKey();
            } else if (requestMode == VertexRelayHandler.REQUEST_MODE_CLAUDE) {
                return VertexUrlBuilder.buildAnthropicModelURL(
                        info.getChannelBaseUrl(), VertexUrlBuilder.DEFAULT_API_VERSION, "", region, model, suffix)
                        + keyPrefix + "key=" + info.getApiKey();
            }
        }

        throw new UnsupportedOperationException("unsupported vertex request mode");
    }

    @Override
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);

        // Service Account 模式需要 JWT 换 token
        boolean useApiKey = false;
        if (info.getChannelOtherSettings() != null && info.getChannelOtherSettings().getVertexKeyType() != null) {
            useApiKey = "api_key".equals(info.getChannelOtherSettings().getVertexKeyType());
        }

        if (!useApiKey && accountCredentials != null) {
            String cacheKey = info.isChannelIsMultiKey()
                    ? "access-token-" + info.getChannelId() + "-" + info.getChannelMultiKeyIndex()
                    : "access-token-" + info.getChannelId();
            String accessToken = VertexServiceAccountHelper.getAccessToken(accountCredentials, cacheKey, null);
            headers.put("Authorization", "Bearer " + accessToken);

            if (accountCredentials.getProjectId() != null && !accountCredentials.getProjectId().isEmpty()) {
                headers.put("x-goog-user-project", accountCredentials.getProjectId());
            }
        }

        return headers;
    }

    @Override
    public Object convertClaudeRequest(RelayInfo info, ClaudeDTO.ClaudeRequest claudeRequest) throws Exception {
        return VertexDTOPlaceholder.copyRequest(claudeRequest, ANTHROPIC_VERSION);
    }

    @Override
    public Object convertGeminiRequest(RelayInfo info, GeminiDTO.GeminiChatRequest geminiRequest) throws Exception {
        return new GeminiAdaptor().convertGeminiRequest(info, geminiRequest);
    }

    @Override
    public Object convertImageRequest(RelayInfo info, OpenAIImageDTO imageRequest) throws Exception {
        return new GeminiAdaptor().convertImageRequest(info, imageRequest);
    }

    @Override
    public IAdaptor.DoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        return VertexRelayHandler.doResponse(info, resp, requestMode);
    }

    @Override
    public List<String> getModelList() {
        return VertexConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return VertexConstant.CHANNEL_NAME;
    }
}
