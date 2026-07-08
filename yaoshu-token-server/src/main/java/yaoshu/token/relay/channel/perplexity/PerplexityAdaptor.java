package yaoshu.token.relay.channel.perplexity;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

@Slf4j
public class PerplexityAdaptor extends OpenAIAdaptor {

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    @Override public List<String> getModelList() { return PerplexityConstant.MODEL_LIST; }
    @Override public String getChannelName() { return PerplexityConstant.CHANNEL_NAME; }
}
