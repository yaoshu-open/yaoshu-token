package yaoshu.token.relay.channel.perplexity;

import java.util.List;

public final class PerplexityConstant {
    private PerplexityConstant() {}
    public static final String CHANNEL_NAME = "perplexity";
    public static final List<String> MODEL_LIST = List.of(
            "llama-3-sonar-small-32k-chat", "llama-3-sonar-small-32k-online",
            "llama-3-sonar-large-32k-chat", "llama-3-sonar-large-32k-online",
            "llama-3-8b-instruct", "llama-3-70b-instruct", "mixtral-8x7b-instruct",
            "sonar", "sonar-pro", "sonar-reasoning"
    );
}
