package yaoshu.token.relay.channel.baidu;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import java.util.List; import java.util.Map;
@Slf4j
public class BaiduAdaptor extends OpenAIAdaptor {
    @Override public String getRequestURL(RelayInfo info) throws Exception {
        return info.getChannelBaseUrl()+"/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/"+info.getUpstreamModelName()+"?access_token="+info.getApiKey();
    }
    @Override @SuppressWarnings("unchecked")
    public Map<String,String> setupRequestHeader(RelayInfo info) throws Exception {
        return super.setupRequestHeader(info); // 百度用 URL query token，不需要 Auth header
    }
    @Override public List<String> getModelList() { return BaiduConstant.MODEL_LIST; }
    @Override public String getChannelName() { return BaiduConstant.CHANNEL_NAME; }
}
