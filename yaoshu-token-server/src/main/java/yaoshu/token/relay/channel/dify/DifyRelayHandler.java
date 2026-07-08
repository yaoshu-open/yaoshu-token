package yaoshu.token.relay.channel.dify;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/**
 * Dify 响应处理（流式/非流式）  */
@Slf4j
public final class DifyRelayHandler {

    private DifyRelayHandler() {}

    /** Dify SSE 流式处理器 */
    public static Usage difyStreamHandler(InputStream inputStream, RelayInfo info) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();

        Usage[] usage = {new Usage()};
        StreamScanner.scan(inputStream, info, data -> {
            RelayCommonHelper.stringData(response, data);
            return true;
        }, response);
        RelayCommonHelper.done(response);
        return usage[0];
    }

    /** Dify 非流式处理器 */
    public static Usage difyHandler(byte[] responseBody, RelayInfo info) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }
}
