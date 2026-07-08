package yaoshu.token.relay.channel.deepseek;

import java.util.List;

/**
 * DeepSeek 渠道常量  */
public final class DeepSeekConstant {

    private DeepSeekConstant() {
    }

    public static final String CHANNEL_NAME = "deepseek";

    public static final List<String> MODEL_LIST = List.of(
            "deepseek-chat", "deepseek-reasoner",
            "deepseek-v4-flash", "deepseek-v4-flash-none", "deepseek-v4-flash-max",
            "deepseek-v4-pro", "deepseek-v4-pro-none", "deepseek-v4-pro-max"
    );
}
