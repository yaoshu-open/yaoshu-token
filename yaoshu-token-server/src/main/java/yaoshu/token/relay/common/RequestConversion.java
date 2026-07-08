package yaoshu.token.relay.common;

import yaoshu.token.pojo.dto.AudioDTO;
import yaoshu.token.pojo.dto.ClaudeDTO;
import yaoshu.token.pojo.dto.EmbeddingDTO;
import yaoshu.token.pojo.dto.GeminiDTO;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.OpenAIImageDTO;
import yaoshu.token.pojo.dto.OpenAIRequestDTO;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.pojo.dto.RelayFormat;
import yaoshu.token.pojo.dto.RerankRequest;

/**
 * 请求格式推断与转换链管理  */
public final class RequestConversion {

    private RequestConversion() {
    }

    /**
     * 根据请求对象类型推断 Relay 格式。      * <p>
     * 使用精确的 DTO 类型检测（instanceof），与 Go 的 type switch 语义一致。
     * OpenAIResponsesRequest 必须先于 OpenAI 通用请求判断（前者更具体）。
     */
    public static String guessRelayFormatFromRequest(Object req) {
        if (req == null) return null;

        if (req instanceof OpenAIResponsesRequest) {
            return RelayFormat.OPENAI_RESPONSES;
        } else if (req instanceof GeneralOpenAIRequest || req instanceof OpenAIRequestDTO) {
            return RelayFormat.OPENAI;
        } else if (req instanceof ClaudeDTO.ClaudeRequest) {
            return RelayFormat.CLAUDE;
        } else if (req instanceof GeminiDTO.GeminiChatRequest) {
            return RelayFormat.GEMINI;
        } else if (req instanceof EmbeddingDTO.EmbeddingRequest) {
            return RelayFormat.EMBEDDING;
        } else if (req instanceof RerankRequest) {
            return RelayFormat.RERANK;
        } else if (req instanceof OpenAIImageDTO) {
            return RelayFormat.OPENAI_IMAGE;
        } else if (req instanceof AudioDTO.AudioRequest) {
            return RelayFormat.OPENAI_AUDIO;
        }

        return null;
    }

    /**
     * 从请求对象追加格式转换到 RelayInfo 的转换链      */
    public static void appendRequestConversionFromRequest(RelayInfo info, Object req) {
        if (info == null) return;
        String format = guessRelayFormatFromRequest(req);
        if (format != null) {
            info.appendRequestConversion(format);
        }
    }
}
