package yaoshu.token.relay.channel.mistral;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/**
 * Mistral 响应处理（委托 OpenAI 模式）  */
@Slf4j
public final class MistralRelayHandler {

    private MistralRelayHandler() {}

    public static Usage mistralStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();
        StreamScanner.scan(inputStream, info, data -> {
            RelayCommonHelper.stringData(response, data); return true;
        }, response);
        RelayCommonHelper.done(response);
        return new Usage();
    }

    public static Usage mistralHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return new Usage();
    }
}
