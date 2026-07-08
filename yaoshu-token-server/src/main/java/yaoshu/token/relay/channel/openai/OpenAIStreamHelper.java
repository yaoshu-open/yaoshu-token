package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.util.SpringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.ChatCompletionsStreamResponse;
import yaoshu.token.pojo.dto.ClaudeDTO.ClaudeResponse;
import yaoshu.token.pojo.dto.GeminiDTO.GeminiChatResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.service.ConvertService;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 渠道辅助工具（流格式处理 + Token处理 + FinalResponse），
 * <p>
 * 流式数据写入已接入 HttpServletResponse（通过 info.getResponse() 传递），
 * Claude/Gemini 格式转换待接入对应 Service 类。
 */
@Slf4j
public final class OpenAIStreamHelper {

    /** Spring 管理的 ObjectMapper（全局 SNAKE_CASE 策略，用于反序列化上游蛇形 JSON） */
    private static ObjectMapper MAPPER;

    private static ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = SpringUtils.getBean(ObjectMapper.class);
        }
        return MAPPER;
    }

    private OpenAIStreamHelper() {
    }

    // ======================== 流格式分发 ========================

    /**
     * 根据 RelayFormat 分派流式数据处理      */
    public static void handleStreamFormat(RelayInfo info, String data, boolean forceFormat, boolean thinkToContent) throws Exception {
        info.setSendResponseCount(info.getSendResponseCount() + 1);

        String relayFormat = info.getRelayFormat();
        if ("openai".equals(relayFormat)) {
            sendStreamData(info, data, forceFormat, thinkToContent);
        } else if ("claude".equals(relayFormat)) {
            handleClaudeFormat(info, data);
        } else if ("gemini".equals(relayFormat)) {
            handleGeminiFormat(info, data);
        }
    }

    /**
     * OpenAI 流式 → Claude 流式格式转换      */
    private static void handleClaudeFormat(RelayInfo info, String data) throws Exception {
        ChatCompletionsStreamResponse streamResp;
        try {
            streamResp = getMapper().readValue(data, ChatCompletionsStreamResponse.class);
        } catch (Exception e) {
            log.debug("Skipping non-standard Claude stream chunk: {}", e.getMessage());
            return;
        }

        if (streamResp.getUsage() != null) {
            info.getClaudeUsage().setPromptTokens(streamResp.getUsage().getPromptTokens());
            info.getClaudeUsage().setCompletionTokens(streamResp.getUsage().getCompletionTokens());
            info.getClaudeUsage().setTotalTokens(streamResp.getUsage().getTotalTokens());
        }

        List<ClaudeResponse> claudeResponses = ConvertService.streamResponseOpenAI2Claude(streamResp, info);
        if (claudeResponses != null) {
            HttpServletResponse response = info.getResponse();
            if (response != null) {
                for (ClaudeResponse cr : claudeResponses) {
                    RelayCommonHelper.claudeData(response, cr);
                }
            }
        }
    }

    /**
     * OpenAI 流式 → Gemini 流式格式转换      */
    private static void handleGeminiFormat(RelayInfo info, String data) throws Exception {
        ChatCompletionsStreamResponse streamResp;
        try {
            streamResp = getMapper().readValue(data, ChatCompletionsStreamResponse.class);
        } catch (Exception e) {
            log.debug("Skipping non-standard Gemini stream chunk: {}", e.getMessage());
            return;
        }

        GeminiChatResponse geminiResponse = ConvertService.streamResponseOpenAI2Gemini(streamResp, info);
        if (geminiResponse == null) return;

        HttpServletResponse response = info.getResponse();
        if (response != null) {
            String geminiJson = Convert.toJSONString(geminiResponse);
            RelayCommonHelper.stringData(response, "data: " + geminiJson);
        }
    }

    // ======================== 流式数据发送 ========================

    /**
     * 发送流式数据（含 think→content 转换）      */
    static void sendStreamData(RelayInfo info, String data, boolean forceFormat, boolean thinkToContent) throws Exception {
        if (data == null || data.isEmpty()) return;

        HttpServletResponse response = info.getResponse();
        if (response == null) return;

        if (!forceFormat && !thinkToContent) {
            RelayCommonHelper.stringData(response, data);
            return;
        }

        ChatCompletionsStreamResponse streamResp;
        try {
            streamResp = getMapper().readValue(data, ChatCompletionsStreamResponse.class);
        } catch (Exception e) {
            log.debug("Skipping non-standard stream chunk in sendStreamData: {}", e.getMessage());
            RelayCommonHelper.stringData(response, data);  // 回退：原样透传
            return;
        }

        if (!thinkToContent) {
            RelayCommonHelper.objectData(response, streamResp);
            return;
        }

        // think→content 转换——将 reasoning_content 重映射为 content
        boolean hasThinkingContent = false;
        boolean hasContent = false;
        StringBuilder thinkingBuilder = new StringBuilder();

        if (streamResp.getChoices() != null) {
            for (ChatCompletionsStreamResponse.Choice choice : streamResp.getChoices()) {
                String reasoning = choice.getDelta() != null ? choice.getDelta().getReasoningContent() : null;
                if (reasoning != null && !reasoning.isEmpty()) {
                    hasThinkingContent = true;
                    thinkingBuilder.append(reasoning);
                }
                String content = choice.getDelta() != null ? choice.getDelta().getContent() : null;
                if (content != null && !content.isEmpty()) {
                    hasContent = true;
                }
            }
        }

        if (hasThinkingContent) {
            if (!info.isHasSentThinkingContent()) {
                // 首个 think→content chunk：注入 <｜end▁of▁thinking｜> 标签
                info.setHasSentThinkingContent(true);
                if (streamResp.getChoices() != null) {
                    for (ChatCompletionsStreamResponse.Choice choice : streamResp.getChoices()) {
                        if (choice.getDelta() != null) {
                            choice.getDelta().setContent(" <｜end▁of▁thinking｜>" + (
                                    choice.getDelta().getReasoningContent() != null
                                            ? choice.getDelta().getReasoningContent() : ""));
                        }
                    }
                }
            } else if (hasContent && info.isSendLastThinkingContent()) {
                // 有实际 content 输出 → 插入  标签闭合 thinking
                info.setSendLastThinkingContent(false);
                if (streamResp.getChoices() != null) {
                    for (ChatCompletionsStreamResponse.Choice choice : streamResp.getChoices()) {
                        if (choice.getDelta() != null) {
                            choice.getDelta().setContent("  \n" + (
                                    choice.getDelta().getContent() != null
                                            ? choice.getDelta().getContent() : ""));
                        }
                    }
                }
            }
            // 将 reasoning_content 映射到 content
            if (streamResp.getChoices() != null) {
                for (ChatCompletionsStreamResponse.Choice choice : streamResp.getChoices()) {
                    if (choice.getDelta() != null) {
                        choice.getDelta().setContent(choice.getDelta().getReasoningContent());
                    }
                }
            }
        }

        RelayCommonHelper.objectData(response, streamResp);
    }

    // ======================== Token 处理 ========================

    /**
     * 处理流式响应的 token 数据      */
    static void processTokenData(int relayMode, String data, StringBuilder responseTextBuilder, int[] toolCount) {
        if (relayMode == RelayModeEnum.CHAT_COMPLETIONS) {
            ChatCompletionsStreamResponse streamResp;
            try {
                streamResp = getMapper().readValue(data, ChatCompletionsStreamResponse.class);
            } catch (Exception e) {
                log.debug("Skipping non-standard chunk in processTokenData: {}", e.getMessage());
                return;  // 流式数据中可能包含非标准 JSON（数组/空值），跳过继续处理
            }
            processStreamResponse(streamResp, responseTextBuilder, toolCount);
        } else if (relayMode == RelayModeEnum.COMPLETIONS) {
            processCompletionsStreamResponse(data, responseTextBuilder);
        }
    }

    /**
     * 处理 ChatCompletions 流响应的选择项 token 累积      */
    static void processStreamResponse(ChatCompletionsStreamResponse streamResp,
                                      StringBuilder responseTextBuilder, int[] toolCount) {
        if (streamResp.getChoices() == null) return;

        for (ChatCompletionsStreamResponse.Choice choice : streamResp.getChoices()) {
            if (choice.getDelta() != null) {
                String content = choice.getDelta().getContent();
                if (content != null) responseTextBuilder.append(content);

                String reasoning = choice.getDelta().getReasoningContent();
                if (reasoning != null) responseTextBuilder.append(reasoning);

                if (choice.getDelta().getToolCalls() != null) {
                    if (choice.getDelta().getToolCalls().size() > toolCount[0]) {
                        toolCount[0] = choice.getDelta().getToolCalls().size();
                    }
                    // 累积 tool call 文本（name + arguments）
                        for (Object tcObj : choice.getDelta().getToolCalls()) {
                            if (tcObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> tc = (Map<String, Object>) tcObj;
                                Object func = tc.get("function");
                                if (func instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> f = (Map<String, Object>) func;
                                    if (f.get("name") != null) responseTextBuilder.append(f.get("name").toString());
                                    if (f.get("arguments") != null) responseTextBuilder.append(f.get("arguments").toString());
                                }
                            }
                        }
                }
            }
        }
    }

    /**
     * 处理 Completions 流响应      * <p>
     * Completions 模式（非 chat）的流响应格式与 Chat 不同，
     * 此处按 JSON path "choices[0].text" 提取文本
     */
    static void processCompletionsStreamResponse(String data, StringBuilder responseTextBuilder) {
        try {
            Map<String, Object> map = getMapper().readValue(data, new TypeReference<Map<String, Object>>() {});
            Object choices = map.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object first = ((List<?>) choices).get(0);
                if (first instanceof Map) {
                    Object text = ((Map<?, ?>) first).get("text");
                    if (text != null) responseTextBuilder.append(text.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse completions stream response: {}", e.getMessage());
        }
    }

    // ======================== 最后响应处理 ========================

    /**
     * 处理最后一条流数据，提取 responseId/createAt/systemFingerprint/model/usage，
     */
    static void handleLastResponse(String lastStreamData, String[] responseIdHolder, long[] createdAtHolder,
                                   String[] systemFpHolder, String[] modelHolder, Usage[] usageHolder,
                                   boolean[] containStreamUsage, RelayInfo info, boolean[] shouldSendLastResp) throws Exception {
        if (lastStreamData == null || lastStreamData.isEmpty()) return;

        ChatCompletionsStreamResponse lastResp;
        try {
            lastResp = getMapper().readValue(lastStreamData, ChatCompletionsStreamResponse.class);
        } catch (Exception e) {
            log.debug("Skipping non-standard last stream chunk: {}", e.getMessage());
            return;  // 最后一条数据可能是非标准 JSON（数组/空值），跳过
        }

        responseIdHolder[0] = lastResp.getId();
        createdAtHolder[0] = lastResp.getCreated();
        systemFpHolder[0] = lastResp.getSystemFingerprint();
        modelHolder[0] = lastResp.getModel();

        if (isValidUsage(lastResp.getUsage())) {
            containStreamUsage[0] = true;
            usageHolder[0] = lastResp.getUsage();
            if (!info.isShouldIncludeUsage()) {
                shouldSendLastResp[0] = hasDeltaContent(lastResp);
            }
        }
    }

    /**
     * 判断 Usage 是否有效（promptTokens > 0 或 completionTokens > 0 或 totalTokens > 0），
     */
    static boolean isValidUsage(Usage usage) {
        return usage != null && (usage.getPromptTokens() > 0
                || usage.getCompletionTokens() > 0
                || usage.getTotalTokens() > 0);
    }

    /**
     * 判断最后流响应是否包含实际 content delta      */
    private static boolean hasDeltaContent(ChatCompletionsStreamResponse resp) {
        if (resp == null || resp.getChoices() == null) return false;
        return resp.getChoices().stream().anyMatch(c -> {
            if (c.getDelta() == null) return false;
            String content = c.getDelta().getContent();
            String reasoning = c.getDelta().getReasoningContent();
            return (content != null && !content.isEmpty()) || (reasoning != null && !reasoning.isEmpty());
        });
    }

    /**
     * 发送最终响应（格式感知）      */
    static void handleFinalResponse(RelayInfo info, String lastStreamData,
                                    String responseId, long createdAt, String model,
                                    String systemFingerprint, Usage usage, boolean containStreamUsage) throws Exception {

        HttpServletResponse response = info.getResponse();
        if (response == null) return;

        String relayFormat = info.getRelayFormat();
        if (info.isShouldIncludeUsage() && !containStreamUsage && "openai".equals(relayFormat)) {
            // 生成并发送仅含 usage 的最终 chunk
            ChatCompletionsStreamResponse finalResp = RelayCommonHelper.generateFinalUsageResponse(
                    responseId, createdAt, model, usage);
            RelayCommonHelper.objectData(response, finalResp);
        }

        // 发送 SSE [DONE]
        RelayCommonHelper.done(response);

        log.debug("HandleFinalResponse for format={}, model={}, usage={}", relayFormat, model,
                usage != null ? usage.getTotalTokens() : 0);
    }
}
