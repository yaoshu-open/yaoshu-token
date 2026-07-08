package yaoshu.token.relay.channel.jina;
import java.util.List;
public final class JinaConstant {
    private JinaConstant() {}
    public static final String CHANNEL_NAME = "jina";
    public static final List<String> MODEL_LIST = List.of("jina-clip-v1", "jina-reranker-v2-base-multilingual", "jina-reranker-m0");
}
