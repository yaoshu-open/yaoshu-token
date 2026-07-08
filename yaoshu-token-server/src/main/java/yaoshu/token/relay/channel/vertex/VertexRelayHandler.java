package yaoshu.token.relay.channel.vertex;

import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.claude.ClaudeAdaptor;
import yaoshu.token.relay.channel.gemini.GeminiAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.net.http.HttpResponse;

/**
 * Vertex AI 响应处理器  * <p>
 * 根据 RequestMode 分发到 Claude/Gemini/OpenAI 的响应处理逻辑。
 */
public class VertexRelayHandler {

    public static final int REQUEST_MODE_CLAUDE = 1;
    public static final int REQUEST_MODE_GEMINI = 2;
    public static final int REQUEST_MODE_OPEN_SOURCE = 3;

    /**
     * Vertex 响应处理入口      * <p>
     * 根据 requestMode 和 isStream 分发到对应渠道的 DoResponse。
     */
    public static IAdaptor.DoResponseResult doResponse(
            RelayInfo info, HttpResponse<?> resp, int requestMode) throws Exception {

        if (info.isStream()) {
            switch (requestMode) {
                case REQUEST_MODE_CLAUDE:
                    return new ClaudeAdaptor().doResponse(info, resp);
                case REQUEST_MODE_GEMINI:
                    return new GeminiAdaptor().doResponse(info, resp);
                case REQUEST_MODE_OPEN_SOURCE:
                    return new OpenAIAdaptor().doResponse(info, resp);
            }
        } else {
            switch (requestMode) {
                case REQUEST_MODE_CLAUDE:
                    return new ClaudeAdaptor().doResponse(info, resp);
                case REQUEST_MODE_GEMINI:
                    return new GeminiAdaptor().doResponse(info, resp);
                case REQUEST_MODE_OPEN_SOURCE:
                    return new OpenAIAdaptor().doResponse(info, resp);
            }
        }

        throw new UnsupportedOperationException("unsupported vertex request mode: " + requestMode);
    }
}
