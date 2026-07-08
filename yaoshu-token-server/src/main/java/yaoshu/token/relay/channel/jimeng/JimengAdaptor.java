package yaoshu.token.relay.channel.jimeng;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import java.util.List; import java.util.Map;
@Slf4j
public class JimengAdaptor extends OpenAIAdaptor {
    @Override @SuppressWarnings("unchecked")
    public Map<String,String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String,String> h = super.setupRequestHeader(info); h.put("Authorization","Bearer "+info.getApiKey()); return h;
    }
    @Override public List<String> getModelList() { return JimengConstant.MODEL_LIST; }
    @Override public String getChannelName() { return JimengConstant.CHANNEL_NAME; }
}
