package yaoshu.token.relay.channel.moonshot;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.ChannelConstants.ChannelSpecialBase;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

/**
 * Moonshot (Kimi) 渠道适配器  * <p>
 * 在 OpenAI 适配器基础上覆盖：URL 构造（anthropic 前缀 + 特殊 Plan URL）、温度强制 1.0。
 */
@Slf4j
public class MoonshotAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String baseURL = info.getChannelBaseUrl();

        // 特殊 Plan URL 映射
        ChannelSpecialBase specialPlan = ChannelConstants.CHANNEL_SPECIAL_BASES.get(baseURL);
        if (specialPlan != null) {
            if ("claude".equals(info.getRelayFormat())) {
                return specialPlan.claudeBaseURL() + "/v1/messages";
            }
            if ("openai".equals(info.getRelayFormat())) {
                return specialPlan.openAIBaseURL() + "/chat/completions";
            }
        }

        if ("claude".equals(info.getRelayFormat())) {
            return baseURL + "/anthropic/v1/messages";
        }

        int relayMode = info.getRelayMode();
        if (relayMode == yaoshu.token.relay.constant.RelayModeEnum.RERANK) {
            return baseURL + "/v1/rerank";
        } else if (relayMode == yaoshu.token.relay.constant.RelayModeEnum.EMBEDDINGS) {
            return baseURL + "/v1/embeddings";
        } else if (relayMode == yaoshu.token.relay.constant.RelayModeEnum.COMPLETIONS) {
            return baseURL + "/v1/completions";
        } else if (relayMode == yaoshu.token.relay.constant.RelayModeEnum.CHAT_COMPLETIONS) {
            return baseURL + "/v1/chat/completions";
        }
        return baseURL + "/v1/chat/completions";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request instanceof GeneralOpenAIRequest) {
            GeneralOpenAIRequest req = (GeneralOpenAIRequest) request;
            // kimi-k2.6 模型强制温度 1.0
            String model = info.getUpstreamModelName();
            if (model == null || model.isEmpty()) {
                model = req.getModel();
            }
            if ("kimi-k2.6".equalsIgnoreCase(model)) {
                Double temp = req.getTemperature();
                if (temp != null && temp != 1.0) {
                    req.setTemperature(1.0);
                }
            }
        }
        return request;
    }

    @Override
    public List<String> getModelList() {
        return MoonshotConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return MoonshotConstant.CHANNEL_NAME;
    }
}
