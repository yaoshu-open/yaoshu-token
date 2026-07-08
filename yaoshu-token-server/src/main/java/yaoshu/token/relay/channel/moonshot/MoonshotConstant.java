package yaoshu.token.relay.channel.moonshot;

import java.util.List;

/**
 * Moonshot (Kimi) 渠道常量  */
public final class MoonshotConstant {

    private MoonshotConstant() {
    }

    public static final String CHANNEL_NAME = "moonshot";

    public static final List<String> MODEL_LIST = List.of(
            "kimi-k2.5",
            "kimi-k2-0905-preview",
            "kimi-k2-turbo-preview",
            "kimi-k2-thinking",
            "kimi-k2-thinking-turbo"
    );
}
