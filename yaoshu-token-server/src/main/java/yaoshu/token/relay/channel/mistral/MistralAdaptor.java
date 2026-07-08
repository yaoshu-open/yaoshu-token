package yaoshu.token.relay.channel.mistral;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;

import java.util.List;
import java.util.Map;

/**
 * Mistral 渠道适配器  */
@Slf4j
public class MistralAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        return RelayUtils.getFullRequestURL(info.getChannelBaseUrl(), info.getRequestURLPath(), info.getChannelType());
    }

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    @Override @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        // Mistral 原生支持 OpenAI 兼容格式，直接透传
        return super.convertOpenAIRequest(info, request);
    }

    @Override public List<String> getModelList() { return MistralConstant.MODEL_LIST; }
    @Override public String getChannelName() { return MistralConstant.CHANNEL_NAME; }
}
