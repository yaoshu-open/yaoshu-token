package yaoshu.token.relay.channel.tencent;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.tencent.TencentDTOPlaceholder.*;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 腾讯混元中转处理器  * <p>
 * 腾讯混元使用 PascalCase 命名，需要请求/响应格式转换。
 */
@Slf4j
public class TencentRelayHandler {    /**
     * OpenAI 请求 → 腾讯混元请求      */
    @SuppressWarnings("unchecked")
    public static TencentChatRequest requestOpenAI2Tencent(GeneralOpenAIRequest request) {
        TencentChatRequest tencentReq = new TencentChatRequest();
        tencentReq.setModel(request.getModel());

        List<TencentMessage> messages = new ArrayList<>();
        List<Object> rawMessages = (List<Object>) (Object) request.getMessages();
        if (rawMessages != null) {
            for (Object msgObj : rawMessages) {
                if (msgObj instanceof Map) {
                    Map<String, Object> msg = (Map<String, Object>) msgObj;
                    TencentMessage tencentMsg = new TencentMessage();
                    tencentMsg.setRole((String) msg.get("role"));
                    Object content = msg.get("content");
                    tencentMsg.setContent(contentToString(content));
                    messages.add(tencentMsg);
                }
            }
        }
        tencentReq.setMessages(messages);
        tencentReq.setStream(request.getStream());
        tencentReq.setTopP(request.getTopP());
        tencentReq.setTemperature(request.getTemperature());

        return tencentReq;
    }

    /**
     * 腾讯混元响应 → OpenAI 响应      */
    public static Usage tencentHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        TencentChatResponseSB sb = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), TencentChatResponseSB.class);
        TencentChatResponse tencentResp = sb.getResponse();

        // 错误检查
        if (tencentResp.getError() != null && tencentResp.getError().getMessage() != null
                && !tencentResp.getError().getMessage().isEmpty()) {
            throw new RuntimeException("tencent error: " + tencentResp.getError().getMessage()
                    + " (code=" + tencentResp.getError().getCode() + ")");
        }

        // 构建 OpenAI 响应
        Map<String, Object> openaiResp = new LinkedHashMap<>();
        openaiResp.put("id", tencentResp.getId());
        openaiResp.put("object", "chat.completion");
        openaiResp.put("created", tencentResp.getCreated() > 0 ? tencentResp.getCreated() : System.currentTimeMillis() / 1000);
        openaiResp.put("model", info.getUpstreamModelName());

        List<Map<String, Object>> choices = new ArrayList<>();
        if (tencentResp.getChoices() != null) {
            for (int i = 0; i < tencentResp.getChoices().size(); i++) {
                TencentResponseChoices choice = tencentResp.getChoices().get(i);
                Map<String, Object> openaiChoice = new LinkedHashMap<>();
                openaiChoice.put("index", i);

                Map<String, Object> message = new LinkedHashMap<>();
                TencentMessage msg = choice.getMessage();
                if (msg == null) msg = choice.getDelta();
                if (msg != null) {
                    message.put("role", msg.getRole());
                    message.put("content", msg.getContent());
                }
                openaiChoice.put("message", message);
                openaiChoice.put("finish_reason", choice.getFinishReason() != null ? choice.getFinishReason() : "stop");
                choices.add(openaiChoice);
            }
        }
        openaiResp.put("choices", choices);

        // Usage
        Usage usage = new Usage();
        if (tencentResp.getUsage() != null) {
            usage.setPromptTokens(tencentResp.getUsage().getPromptTokens());
            usage.setCompletionTokens(tencentResp.getUsage().getCompletionTokens());
            usage.setTotalTokens(tencentResp.getUsage().getTotalTokens());
        }
        openaiResp.put("usage", Map.of(
                "prompt_tokens", usage.getPromptTokens(),
                "completion_tokens", usage.getCompletionTokens(),
                "total_tokens", usage.getTotalTokens()
        ));

        byte[] jsonResp = Convert.toJSONString(openaiResp).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setContentType("application/json");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(jsonResp);
        response.getOutputStream().flush();

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
