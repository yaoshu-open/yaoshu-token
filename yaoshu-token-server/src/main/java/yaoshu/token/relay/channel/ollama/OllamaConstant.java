package yaoshu.token.relay.channel.ollama;

import java.util.List;

/**
 * Ollama 渠道常量  */
public final class OllamaConstant {

    private OllamaConstant() {
    }

    public static final String CHANNEL_NAME = "ollama";

    public static final List<String> MODEL_LIST = List.of("llama3-7b");
}
