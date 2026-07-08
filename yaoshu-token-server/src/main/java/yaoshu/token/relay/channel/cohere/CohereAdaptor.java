package yaoshu.token.relay.channel.cohere;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.RerankRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.util.*;

/**
 * Cohere 渠道适配器  */
@Slf4j
public class CohereAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        if (info.getRelayMode() == RelayModeEnum.RERANK) {
            return info.getChannelBaseUrl() + "/v1/rerank";
        }
        return info.getChannelBaseUrl() + "/v1/chat";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    /**
     * OpenAI→Cohere Chat 格式转换      * <p>
     * Cohere /v1/chat 格式：{@code {"model":"xxx","message":"last","chat_history":[...],"stream":false}}
     * OpenAI messages 的最后一个作为 message，其余作为 chat_history，
     * role 映射：user→USER, assistant→CHATBOT, system→SYSTEM
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;
        openAIRequest = (GeneralOpenAIRequest) super.convertOpenAIRequest(info, openAIRequest);

        List<GeneralOpenAIRequest.Message> messages = openAIRequest.getMessages();
        Map<String, Object> cohereReq = new LinkedHashMap<>();
        cohereReq.put("model", info.getUpstreamModelName());
        cohereReq.put("stream", Boolean.TRUE.equals(openAIRequest.getStream()));

        if (messages != null && !messages.isEmpty()) {
            // 除最后一条外为 chat_history
            List<Map<String, String>> chatHistory = new ArrayList<>();
            for (int i = 0; i < messages.size() - 1; i++) {
                GeneralOpenAIRequest.Message msg = messages.get(i);
                chatHistory.add(Map.of(
                        "role", mapRoleToCohere(msg.getRole()),
                        "message", msg.getContent() instanceof String ? (String) msg.getContent() : ""));
            }
            cohereReq.put("chat_history", chatHistory);

            // 最后一条为 message
            GeneralOpenAIRequest.Message lastMsg = messages.get(messages.size() - 1);
            cohereReq.put("message", lastMsg.getContent() instanceof String ? (String) lastMsg.getContent() : "");
        }

        return cohereReq;
    }

    /** OpenAI role → Cohere role 映射 */
    private String mapRoleToCohere(String role) {
        if (role == null) return "USER";
        return switch (role) {
            case "assistant" -> "CHATBOT";
            case "system" -> "SYSTEM";
            default -> "USER";
        };
    }

    /**
     * Rerank→Cohere 格式转换      * <p>
     * Cohere /v1/rerank 格式：{@code {"model":"xxx","query":"...","documents":[...]}}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object convertRerankRequest(RelayInfo info, int relayMode, RerankRequest rerankReq) throws Exception {
        Map<String, Object> cohereReq = new LinkedHashMap<>();
        cohereReq.put("model", info.getUpstreamModelName());
        cohereReq.put("query", rerankReq.getQuery());
        cohereReq.put("documents", rerankReq.getDocuments() != null ? rerankReq.getDocuments() : List.of());
        if (rerankReq.getTopN() > 0) cohereReq.put("top_n", rerankReq.getTopN());
        return cohereReq;
    }

    @Override
    public List<String> getModelList() { return CohereConstant.MODEL_LIST; }

    @Override
    public String getChannelName() { return CohereConstant.CHANNEL_NAME; }
}
