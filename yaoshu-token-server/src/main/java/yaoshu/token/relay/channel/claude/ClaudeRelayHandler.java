package yaoshu.token.relay.channel.claude;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/**
 * Claude 响应处理（流式/非流式）  * <p>
 * Claude 渠道使用 OpenAI 格式作为中转枢纽，流式 SSE 透传，非流式完整写入。
 */
@Slf4j
public final class ClaudeRelayHandler {

    private ClaudeRelayHandler() {}

    /** Claude 流式处理器：SSE 透传 + [DONE] */
    public static Usage claudeStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();

        StreamScanner.scan(inputStream, info, data -> {
            RelayCommonHelper.stringData(response, data);
            return true;
        }, response);
        RelayCommonHelper.done(response);
        return new Usage();
    }

    /** Claude 非流式处理器：透传完整响应 */
    public static Usage claudeHandler(byte[] responseBody, RelayInfo info) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }
}
