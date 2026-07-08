package yaoshu.token.relay.channel.minimax;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * MiniMax TTS 处理器  * <p>
 * MiniMax TTS 返回 hex 编码的音频数据，需解码后写入响应。
 */
@Slf4j
public class MiniMaxTtsHandler {    /**
     * MiniMax TTS 响应处理
     * <p>
     * 非流式：解析 hex 格式的 audio（hex 字段），解码后写入。
     * 流式：直接透传 SSE 数据。
     */
    @SuppressWarnings("unchecked")
    public static Usage miniMaxTtsHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        // 解析响应 JSON
        java.util.Map<String, Object> miniMaxResp = Convert.toJSONObject(responseBody);

        // 错误检查
        Object baseResp = miniMaxResp.get("base_resp");
        if (baseResp instanceof java.util.Map<?, ?> baseMap) {
            Object statusCode = baseMap.get("status_code");
            Object statusMsg = baseMap.get("status_msg");
            if (statusCode instanceof Number n && n.intValue() != 0) {
                throw new RuntimeException("minimax tts error: " + statusMsg + " (code=" + n + ")");
            }
        }

        // 提取 hex 格式的音频数据
        Object dataObj = miniMaxResp.get("data");
        if (dataObj instanceof java.util.Map<?, ?> data) {
            Object audio = data.get("audio");
            if (audio instanceof String hexAudio && !hexAudio.isEmpty()) {
                // hex → bytes
                byte[] audioBytes = hexToBytes(hexAudio);
                response.setContentType("audio/mpeg");
                response.setStatus(resp.statusCode());
                response.getOutputStream().write(audioBytes);
                response.getOutputStream().flush();
                return new Usage();
            }
        }

        // 如果没有 audio 字段，返回原始响应
        response.setContentType("application/json");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();

        return new Usage();
    }

    /** hex 字符串转字节数组 */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
