package yaoshu.token.relay.channel.volcengine;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 * 火山引擎 TTS 处理器  * <p>
 * 火山引擎 TTS 使用自定义二进制 WebSocket 协议（VolcEngineProtocolsPlaceholder）。
 * 非流式模式下，音频数据通过 HTTP 响应返回。
 */
@Slf4j
public class VolcEngineTtsHandler {

    /**
     * 火山引擎 TTS 响应处理
     * <p>
     * 将火山引擎返回的音频数据直接透传给客户端。
     */
    public static Usage volcEngineTtsHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] audioBytes;
        try (InputStream bodyStream = resp.body()) {
            audioBytes = bodyStream.readAllBytes();
        }

        // 火山引擎 TTS 返回二进制音频数据，直接透传
        response.setContentType("audio/mpeg");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(audioBytes);
        response.getOutputStream().flush();

        return new Usage();
    }
}
