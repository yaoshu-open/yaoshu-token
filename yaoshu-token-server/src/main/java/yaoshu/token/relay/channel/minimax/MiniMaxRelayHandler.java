package yaoshu.token.relay.channel.minimax;

import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;

/**
 * MiniMax 中转处理器  * <p>
 * MiniMax 使用自定义 URL 模式，支持 Chat / Image / TTS。
 */
public class MiniMaxRelayHandler {

    /**
     * 构建请求 URL      */
    public static String getRequestURL(RelayInfo info) {
        String baseUrl = info.getChannelBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.minimax.chat";
        }

        // Claude 格式
        if ("claude".equals(info.getRelayFormat())) {
            return baseUrl + "/anthropic/v1/messages";
        }

        switch (info.getRelayMode()) {
            case RelayModeEnum.CHAT_COMPLETIONS:
                return baseUrl + "/v1/text/chatcompletion_v2";
            case RelayModeEnum.IMAGES_GENERATIONS:
                return baseUrl + "/v1/image_generation";
            case RelayModeEnum.AUDIO_SPEECH:
                return baseUrl + "/v1/t2a_v2";
            default:
                throw new UnsupportedOperationException("unsupported minimax relay mode: " + info.getRelayMode());
        }
    }
}
