package yaoshu.token.relay.channel.dify;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 渠道适配器  * <p>
 * 支持 ChatFlow / Agent / WorkFlow / Completion 四种 BotType。
 */
@Slf4j
public class DifyAdaptor extends OpenAIAdaptor {

    public static final int BOT_TYPE_CHAT_FLOW = 1;
    public static final int BOT_TYPE_AGENT = 2;
    public static final int BOT_TYPE_WORK_FLOW = 3;
    public static final int BOT_TYPE_COMPLETION = 4;

    private int botType = BOT_TYPE_CHAT_FLOW;

    @Override
    public void init(RelayInfo info) {
        super.init(info);
        // 根据 upstreamModelName 前缀判断 BotType
        String modelName = info.getUpstreamModelName();
        if (modelName != null) {
            if (modelName.startsWith("agent_")) {
                this.botType = BOT_TYPE_AGENT;
            } else if (modelName.startsWith("workflow_")) {
                this.botType = BOT_TYPE_WORK_FLOW;
            } else if (modelName.startsWith("completion_")) {
                this.botType = BOT_TYPE_COMPLETION;
            }
        }
    }

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        return switch (this.botType) {
            case BOT_TYPE_WORK_FLOW -> info.getChannelBaseUrl() + "/v1/workflows/run";
            case BOT_TYPE_COMPLETION -> info.getChannelBaseUrl() + "/v1/completion-messages";
            default -> info.getChannelBaseUrl() + "/v1/chat-messages";
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    /**
     * OpenAI→Dify 格式转换      * <p>
     * Dify /v1/chat-messages 格式：{@code {"inputs":{},"query":"...","response_mode":"streaming/blocking","user":"..."}}
     * OpenAI messages 的最后一个 content 作为 query
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;
        openAIRequest = (GeneralOpenAIRequest) super.convertOpenAIRequest(info, openAIRequest);

        Map<String, Object> difyReq = new LinkedHashMap<>();
        difyReq.put("inputs", new LinkedHashMap<>());

        // 提取最后一条消息作为 query
        List<GeneralOpenAIRequest.Message> messages = openAIRequest.getMessages();
        if (messages != null && !messages.isEmpty()) {
            GeneralOpenAIRequest.Message lastMsg = messages.get(messages.size() - 1);
            difyReq.put("query", lastMsg.getContent() instanceof String ? lastMsg.getContent() : "");
        } else {
            difyReq.put("query", "");
        }

        // response_mode：streaming / blocking
        boolean isStream = Boolean.TRUE.equals(openAIRequest.getStream());
        difyReq.put("response_mode", isStream ? "streaming" : "blocking");

        // user 标识
        if (info.getUserId() > 0) {
            difyReq.put("user", String.valueOf(info.getUserId()));
        }

        return difyReq;
    }

    @Override
    public List<String> getModelList() { return DifyConstant.MODEL_LIST; }
    @Override
    public String getChannelName() { return DifyConstant.CHANNEL_NAME; }
}
