package yaoshu.token.relay.helper;

import ai.yue.library.base.convert.Convert;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.pojo.dto.ClaudeDTO.ClaudeRequest;
import yaoshu.token.pojo.dto.GeminiDTO.GeminiChatRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 请求验证与解析辅助  * <p>
 * 根据 RelayFormat 分发到对应的反序列化+验证方法。
 */
public final class ValidRequestHelper {

    private ValidRequestHelper() {
    }

    /**
     * 按格式反序列化请求体      *
     * @param relayFormat OpenAI / claude / gemini / responses / image / embedding / rerank / audio
     * @param bodyBytes   HTTP 请求体字节
     * @return 对应的 DTO 对象
     */
    public static Object parse(String relayFormat, byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) return null;

        return switch (relayFormat != null ? relayFormat.toLowerCase() : "openai") {
            case "claude" -> Convert.toJavaBean(bodyBytes, ClaudeRequest.class);
            case "gemini" -> Convert.toJavaBean(bodyBytes, GeminiChatRequest.class);
            case "responses" -> Convert.toJavaBean(bodyBytes, OpenAIResponsesRequest.class);
            case "openai" -> Convert.toJavaBean(bodyBytes, OpenAIRequestDTO.class);
            default -> {
                // 其他格式回退为原始字节流，由各自处理器自行解析
                InputStream is = new ByteArrayInputStream(bodyBytes);
                yield is;
            }
        };
    }
}
