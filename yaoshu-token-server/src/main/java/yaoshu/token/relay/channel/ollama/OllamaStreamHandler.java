package yaoshu.token.relay.channel.ollama;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/**
 * Ollama 流式/非流式响应处理器  * <p>
 * Ollama 使用 NDJSON 逐行格式，经 Adaptor 转换为 OpenAI 格式后，
 * 流式通过 SSE 透传，非流式完整写入。
 */
@Slf4j
public final class OllamaStreamHandler {

    private OllamaStreamHandler() {}

    /** Ollama 流式处理器：SSE 透传 + [DONE] */
    public static Usage ollamaStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();

        StreamScanner.scan(inputStream, info, data -> {
            RelayCommonHelper.stringData(response, data);
            return true;
        }, response);
        RelayCommonHelper.done(response);
        return new Usage();
    }

    /** Ollama 非流式处理器：透传完整响应 */
    public static Usage ollamaChatHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }
}
