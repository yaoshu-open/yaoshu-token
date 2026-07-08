package yaoshu.token.relay.channel.cloudflare;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.util.List;
import java.util.Map;

/**
 * Cloudflare Workers AI 渠道适配器  * <p>
 * 关键差异：/client/v4/accounts/{id}/ai/ 前缀 URL + API 版本占位
 */
@Slf4j
public class CloudflareAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String base = info.getChannelBaseUrl();
        String accountId = info.getApiVersion();
        int mode = info.getRelayMode();
        if (mode == RelayModeEnum.CHAT_COMPLETIONS)
            return base + "/client/v4/accounts/" + accountId + "/ai/v1/chat/completions";
        if (mode == RelayModeEnum.EMBEDDINGS)
            return base + "/client/v4/accounts/" + accountId + "/ai/v1/embeddings";
        if (mode == RelayModeEnum.RESPONSES)
            return base + "/client/v4/accounts/" + accountId + "/ai/v1/responses";
        return base + "/client/v4/accounts/" + accountId + "/ai/run/" + info.getUpstreamModelName();
    }

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());
        return headers;
    }

    @Override public List<String> getModelList() { return CloudflareConstant.MODEL_LIST; }
    @Override public String getChannelName() { return CloudflareConstant.CHANNEL_NAME; }
}
