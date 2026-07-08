package yaoshu.token.relay.channel.deepseek;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.ReasoningSuffixConfig;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 渠道适配器  * <p>
 * 在 OpenAI 适配器基础上覆盖：请求 URL 构造 + DeepSeek V4 Think 后缀处理。
 */
@Slf4j
public class DeepSeekAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        if ("claude".equals(info.getRelayFormat())) {
            return info.getChannelBaseUrl() + "/anthropic/v1/messages";
        }

        String baseUrl = info.getChannelBaseUrl();
        if (!baseUrl.endsWith("/beta")) {
            baseUrl += "/beta";
        }

        int relayMode = info.getRelayMode();
        if (relayMode == yaoshu.token.relay.constant.RelayModeEnum.COMPLETIONS) {
            return baseUrl + "/completions";
        }
        return info.getChannelBaseUrl() + "/v1/chat/completions";
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
        if (request == null) throw new IllegalArgumentException("request is nil");
        // DeepSeek V4 思维后缀解析（Go: reasoning.ParseDeepSeekV4ThinkingSuffix）
        String originModel = info.getOriginModelName();
        if (originModel != null && request instanceof GeneralOpenAIRequest req) {
            ReasoningSuffixConfig.DeepSeekThink think = ReasoningSuffixConfig.parseDeepSeekV4ThinkingSuffix(originModel);
            if (think != null) {
                info.setUpstreamModelName(think.baseModel);
                // thinkingType: "enabled"→追加 thinking type, "disabled"→不处理
                if ("enabled".equals(think.thinkingType) && !think.effort.isEmpty()) {
                    req.setReasoningEffort(think.effort);
                }
            }
        }
        return request;
    }

    @Override
    public List<String> getModelList() {
        return DeepSeekConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return DeepSeekConstant.CHANNEL_NAME;
    }
}
