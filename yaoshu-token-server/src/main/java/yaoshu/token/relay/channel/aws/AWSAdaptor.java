package yaoshu.token.relay.channel.aws;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor; import yaoshu.token.relay.common.RelayInfo;
import java.util.*; @SuppressWarnings("unchecked")
@Slf4j public class AWSAdaptor extends OpenAIAdaptor {
    @Override public String getRequestURL(RelayInfo info) throws Exception {
        String modelId = info.getUpstreamModelName();
        String awsId = AWSConstant.MODEL_ID_MAP.getOrDefault(modelId, modelId);
        return info.getChannelBaseUrl()+"/model/"+awsId+"/invoke";
    }
    @Override public Map<String,String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String,String> h = super.setupRequestHeader(info);
        h.put("Authorization","Bearer "+info.getApiKey()); return h;
    }
    @Override public List<String> getModelList() { return AWSConstant.MODEL_LIST; }
    @Override public String getChannelName() { return AWSConstant.CHANNEL_NAME; }
}
