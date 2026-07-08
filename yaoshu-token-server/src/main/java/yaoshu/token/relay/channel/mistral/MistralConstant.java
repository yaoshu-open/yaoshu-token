package yaoshu.token.relay.channel.mistral;

import java.util.List;

public final class MistralConstant {
    private MistralConstant() {}
    public static final String CHANNEL_NAME = "mistral";
    public static final List<String> MODEL_LIST = List.of(
            "open-mistral-7b", "open-mixtral-8x7b", "mistral-small-latest",
            "mistral-medium-latest", "mistral-large-latest", "mistral-embed"
    );
}
