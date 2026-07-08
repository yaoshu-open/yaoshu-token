package yaoshu.token.relay.channel.aws;
import java.util.*;
public final class AWSConstant { private AWSConstant(){}
    public static final String CHANNEL_NAME = "aws";
    public static final Map<String,String> MODEL_ID_MAP = new LinkedHashMap<>();
    static {
        MODEL_ID_MAP.put("claude-3-sonnet-20240229","anthropic.claude-3-sonnet-20240229-v1:0");
        MODEL_ID_MAP.put("claude-3-opus-20240229","anthropic.claude-3-opus-20240229-v1:0");
        MODEL_ID_MAP.put("claude-3-haiku-20240307","anthropic.claude-3-haiku-20240307-v1:0");
        MODEL_ID_MAP.put("claude-3-5-sonnet-20240620","anthropic.claude-3-5-sonnet-20240620-v1:0");
        MODEL_ID_MAP.put("claude-3-5-sonnet-20241022","anthropic.claude-3-5-sonnet-20241022-v2:0");
        MODEL_ID_MAP.put("claude-3-5-haiku-20241022","anthropic.claude-3-5-haiku-20241022-v1:0");
        MODEL_ID_MAP.put("claude-3-7-sonnet-20250219","anthropic.claude-3-7-sonnet-20250219-v1:0");
        MODEL_ID_MAP.put("claude-sonnet-4-20250514","anthropic.claude-sonnet-4-20250514-v1:0");
        MODEL_ID_MAP.put("claude-opus-4-20250514","anthropic.claude-opus-4-20250514-v1:0");
        MODEL_ID_MAP.put("nova-micro-v1:0","amazon.nova-micro-v1:0");
        MODEL_ID_MAP.put("nova-lite-v1:0","amazon.nova-lite-v1:0");
        MODEL_ID_MAP.put("nova-pro-v1:0","amazon.nova-pro-v1:0");
        MODEL_ID_MAP.put("nova-canvas-v1:0","amazon.nova-canvas-v1:0");
    }
    public static final List<String> MODEL_LIST = new ArrayList<>(MODEL_ID_MAP.keySet());
}
