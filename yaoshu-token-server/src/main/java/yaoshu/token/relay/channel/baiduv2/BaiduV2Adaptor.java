package yaoshu.token.relay.channel.baiduv2;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import java.util.List; import java.util.Map;
@Slf4j
public class BaiduV2Adaptor extends OpenAIAdaptor {
    @Override @SuppressWarnings("unchecked")
    public Map<String,String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String,String> h = super.setupRequestHeader(info); h.put("Authorization","Bearer "+info.getApiKey()); return h;
    }
    @Override public List<String> getModelList() { return BaiduV2Constant.MODEL_LIST; }
    @Override public String getChannelName() { return BaiduV2Constant.CHANNEL_NAME; }
}
