package yaoshu.token.relay.channel.xunfei;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.xunfei.XunfeiDTOPlaceholder.*;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import java.util.*;

/**
 * 讯飞星风中转处理器  * <p>
 * 讯飞星火使用 WebSocket 通信，请求/响应为嵌套 JSON 结构。
 * 本类负责请求格式转换，WebSocket 通信由 Adaptor 层处理。
 */
@Slf4j
public class XunfeiRelayHandler {    /**
     * OpenAI 请求 → 讯飞请求      */
    @SuppressWarnings("unchecked")
    public static XunfeiChatRequest requestOpenAI2Xunfei(GeneralOpenAIRequest request, String appId, String domain) {
        XunfeiChatRequest xunfeiReq = new XunfeiChatRequest();

        // Header
        XunfeiHeader header = new XunfeiHeader();
        header.setAppId(appId);
        xunfeiReq.setHeader(header);

        // Parameter
        XunfeiParameter parameter = new XunfeiParameter();
        XunfeiChatParam chat = new XunfeiChatParam();
        chat.setDomain(domain != null ? domain : "generalv2");
        chat.setTemperature(request.getTemperature());
        xunfeiReq.setParameter(parameter);

        // Payload
        XunfeiPayload payload = new XunfeiPayload();
        XunfeiPayloadMessage message = new XunfeiPayloadMessage();
        List<XunfeiMessage> textMessages = new ArrayList<>();

        List<Object> rawMessages = (List<Object>) (Object) request.getMessages();
        if (rawMessages != null) {
            for (Object msgObj : rawMessages) {
                if (msgObj instanceof Map) {
                    Map<String, Object> msg = (Map<String, Object>) msgObj;
                    XunfeiMessage xunfeiMsg = new XunfeiMessage();
                    xunfeiMsg.setRole((String) msg.get("role"));
                    Object content = msg.get("content");
                    xunfeiMsg.setContent(contentToString(content));
                    textMessages.add(xunfeiMsg);
                }
            }
        }
        message.setText(textMessages);
        payload.setMessage(message);
        xunfeiReq.setPayload(payload);

        return xunfeiReq;
    }

    /**
     * 讯飞响应 → OpenAI 响应      */
    public static Usage responseXunfei2OpenAI(RelayInfo info, XunfeiChatResponse xunfeiResp) {
        Usage usage = new Usage();

        if (xunfeiResp.getHeader() != null && xunfeiResp.getHeader().getCode() != 0) {
            throw new RuntimeException("xunfei error: " + xunfeiResp.getHeader().getMessage()
                    + " (code=" + xunfeiResp.getHeader().getCode() + ")");
        }

        // 提取 usage
        if (xunfeiResp.getPayload() != null && xunfeiResp.getPayload().getUsage() != null
                && xunfeiResp.getPayload().getUsage().getText() != null) {
            yaoshu.token.pojo.dto.Usage xunfeiUsage = xunfeiResp.getPayload().getUsage().getText();
            usage.setPromptTokens(xunfeiUsage.getPromptTokens());
            usage.setCompletionTokens(xunfeiUsage.getCompletionTokens());
            usage.setTotalTokens(xunfeiUsage.getTotalTokens());
        }

        return usage;
    }

    @SuppressWarnings("unchecked")
    private static String contentToString(Object content) {
        if (content == null) return "";
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                    sb.append(m.get("text"));
                }
            }
            return sb.toString();
        }
        return content.toString();
    }
}
