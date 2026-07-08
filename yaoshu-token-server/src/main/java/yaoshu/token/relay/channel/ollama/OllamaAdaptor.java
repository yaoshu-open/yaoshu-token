package yaoshu.token.relay.channel.ollama;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.EmbeddingDTO;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 渠道适配器  * <p>
 * OpenAI→Ollama 请求格式转换 + Ollama 响应→OpenAI 格式还原。
 * 注意：Ollama 新版（≥0.1.24）已原生支持 /v1/chat/completions 端点，
 * 若渠道 base URL 配置为 /v1/chat/completions，convertOpenAIRequest 可透传。
 */
@Slf4j
public class OllamaAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        int relayMode = info.getRelayMode();
        if (relayMode == RelayModeEnum.EMBEDDINGS) {
            return info.getChannelBaseUrl() + "/api/embed";
        }
        if (info.getRequestURLPath() != null && info.getRequestURLPath().contains("/v1/completions")
                || relayMode == RelayModeEnum.COMPLETIONS) {
            return info.getChannelBaseUrl() + "/api/generate";
        }
        return info.getChannelBaseUrl() + "/api/chat";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    /**
     * OpenAI→Ollama Chat 格式转换      * <p>
     * 转换规则：
     * <ul>
     * <li>保留：model、messages、stream</li>
     * <li>迁移至 options：temperature、top_p、top_k、num_predict(max_tokens)、seed、stop、frequency_penalty、presence_penalty</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;

        // 1. 先走 OpenAI 标准预处理（o系列/GPT-5 模型参数清理等）
        openAIRequest = (GeneralOpenAIRequest) super.convertOpenAIRequest(info, openAIRequest);

        // 2. 构建 Ollama 请求 Map
        Map<String, Object> ollamaReq = new LinkedHashMap<>();
        ollamaReq.put("model", info.getUpstreamModelName());
        ollamaReq.put("messages", openAIRequest.getMessages());
        ollamaReq.put("stream", Boolean.TRUE.equals(openAIRequest.getStream()));

        // 3. 提取 Ollama options
        Map<String, Object> options = new LinkedHashMap<>();
        if (openAIRequest.getTemperature() != null) options.put("temperature", openAIRequest.getTemperature());
        if (openAIRequest.getTopP() != null) options.put("top_p", openAIRequest.getTopP());
        if (openAIRequest.getMaxTokens() != null && openAIRequest.getMaxTokens() > 0) {
            options.put("num_predict", openAIRequest.getMaxTokens());
        } else if (openAIRequest.getMaxCompletionTokens() != null && openAIRequest.getMaxCompletionTokens() > 0) {
            options.put("num_predict", openAIRequest.getMaxCompletionTokens());
        }
        if (openAIRequest.getStop() != null) options.put("stop", openAIRequest.getStop());
        if (openAIRequest.getFrequencyPenalty() != null) options.put("frequency_penalty", openAIRequest.getFrequencyPenalty());
        if (openAIRequest.getPresencePenalty() != null) options.put("presence_penalty", openAIRequest.getPresencePenalty());
        if (!options.isEmpty()) {
            ollamaReq.put("options", options);
        }

        return ollamaReq;
    }

    /**
     * OpenAI→Ollama Embedding 格式转换      */
    @Override
    @SuppressWarnings("unchecked")
    public Object convertEmbeddingRequest(RelayInfo info, EmbeddingDTO embeddingRequest) throws Exception {
        // Ollama /api/embed 格式：{"model": "xxx", "input": ["text1", "text2"]}
        Map<String, Object> ollamaEmbedReq = new LinkedHashMap<>();
        ollamaEmbedReq.put("model", info.getUpstreamModelName());
        ollamaEmbedReq.put("input", embeddingRequest.getInput());
        return ollamaEmbedReq;
    }

    @Override
    public List<String> getModelList() {
        return OllamaConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return OllamaConstant.CHANNEL_NAME;
    }
}
