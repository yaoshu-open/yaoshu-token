package yaoshu.token.relay.channel.cloudflare;

import java.util.List;

public final class CloudflareConstant {
    private CloudflareConstant() {}
    public static final String CHANNEL_NAME = "cloudflare";
    public static final List<String> MODEL_LIST = List.of(
            "@cf/meta/llama-3.1-8b-instruct", "@cf/meta/llama-2-7b-chat-fp16",
            "@cf/mistral/mistral-7b-instruct-v0.1", "@cf/meta/llama-3-8b-instruct"
    );
}
