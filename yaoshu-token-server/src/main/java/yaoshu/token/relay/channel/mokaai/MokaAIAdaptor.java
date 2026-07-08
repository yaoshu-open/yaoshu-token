package yaoshu.token.relay.channel.mokaai;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

/**
 * MokaAI 渠道适配器  */
@Slf4j
public class MokaAIAdaptor extends OpenAIAdaptor {

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    @Override public List<String> getModelList() { return MokaAIConstant.MODEL_LIST; }
    @Override public String getChannelName() { return MokaAIConstant.CHANNEL_NAME; }
}
