package yaoshu.token.relay.channel.coze;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.*;

/**
 * Coze 渠道适配器  * <p>
 * 关键差异：非流式需先 chat → 轮询完成 → 拉取详情两个 HTTP 往返。
 * Coze /v3/chat 使用 bot_id + additional_messages 格式。
 */
@Slf4j
public class CozeAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        return info.getChannelBaseUrl() + "/v3/chat";
    }

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    /**
     * OpenAI→Coze Chat 格式转换      * <p>
     * Coze /v3/chat 格式：{@code {"bot_id":"xxx","user_id":"xxx","stream":true,"additional_messages":[...]}}
     * OpenAI model 映射为 Coze bot_id
     */
    @Override @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;
        openAIRequest = (GeneralOpenAIRequest) super.convertOpenAIRequest(info, openAIRequest);

        Map<String, Object> cozeReq = new LinkedHashMap<>();
        cozeReq.put("bot_id", info.getUpstreamModelName());
        cozeReq.put("stream", Boolean.TRUE.equals(openAIRequest.getStream()));

        // additional_messages：OpenAI messages → Coze format
        List<GeneralOpenAIRequest.Message> messages = openAIRequest.getMessages();
        if (messages != null) {
            List<Map<String, Object>> additionalMessages = new ArrayList<>();
            for (GeneralOpenAIRequest.Message msg : messages) {
                Map<String, Object> cozeMsg = new LinkedHashMap<>();
                cozeMsg.put("role", msg.getRole());
                cozeMsg.put("content", msg.getContent());
                cozeMsg.put("content_type", "text");
                additionalMessages.add(cozeMsg);
            }
            cozeReq.put("additional_messages", additionalMessages);
        }

        // user_id
        if (info.getUserId() > 0) {
            cozeReq.put("user_id", String.valueOf(info.getUserId()));
        }

        return cozeReq;
    }

    @Override public List<String> getModelList() { return CozeConstant.MODEL_LIST; }
    @Override public String getChannelName() { return CozeConstant.CHANNEL_NAME; }
}
