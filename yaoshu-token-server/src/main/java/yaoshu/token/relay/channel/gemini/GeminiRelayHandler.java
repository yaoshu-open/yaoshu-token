package yaoshu.token.relay.channel.gemini;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/**
 * Gemini 响应处理（流式/非流式/Native）  * <p>
 * Gemini 渠道使用 OpenAI 格式作为中转枢纽，请求/响应通过 ConvertService 转换。
 */
@Slf4j
public final class GeminiRelayHandler {
    private GeminiRelayHandler() {}

    /** Gemini 流式处理器：SSE 透传 + [DONE] */
    public static Usage geminiStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();

        StreamScanner.scan(inputStream, info, data -> {
            RelayCommonHelper.stringData(response, data);
            return true;
        }, response);
        RelayCommonHelper.done(response);
        return new Usage();
    }

    /** Gemini 非流式处理器：透传完整响应 */
    public static Usage geminiHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }

    /** Gemini Native 处理器：Gemini 原生格式透传 */
    public static Usage geminiNativeHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }
}
