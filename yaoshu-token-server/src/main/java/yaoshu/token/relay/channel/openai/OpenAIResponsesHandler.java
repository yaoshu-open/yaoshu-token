package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.ChatCompletionsStreamResponse;
import yaoshu.token.pojo.dto.ClaudeDTO.ClaudeResponse;
import yaoshu.token.pojo.dto.GeminiDTO.GeminiChatResponse;
import yaoshu.token.pojo.dto.OpenAIError;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAIResponsesResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ResponsesStreamResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ToolCallResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.service.ConvertService;
import yaoshu.token.service.UsageHelperService;
import yaoshu.token.service.openaicompat.ResponseToChatService;
import yaoshu.token.service.openaicompat.ResponseToChatService.ResponsesToChatResult;

import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 处理器（流式 + 非流式 + Compact + Chat 转换），
 */
@Slf4j
public final class OpenAIResponsesHandler {

    private OpenAIResponsesHandler() {
    }

    // ======================== 非流式处理 ========================

    /**
     * 非流式 Responses 处理      */
    public static Usage oaiResponsesHandler(RelayInfo info, byte[] responseBody, HttpServletResponse resp) throws Exception {
        OpenAIResponsesResponse parsed = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), OpenAIResponsesResponse.class);
        Usage usage = extractUsageFromResponses(parsed);
        if (resp != null) {
            resp.setContentType("application/json");
            resp.getOutputStream().write(responseBody);
        }
        return usage;
    }

    /**
     * Responses 压缩处理      */
    public static Usage oaiResponsesCompactionHandler(byte[] responseBody, HttpServletResponse resp) throws Exception {
        OpenAIResponsesResponse parsed = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), OpenAIResponsesResponse.class);
        Usage usage = extractUsageFromResponses(parsed);
        if (resp != null) {
            resp.setContentType("application/json");
            resp.getOutputStream().write(responseBody);
        }
        return usage;
    }

    /** 从 Responses 响应提取 Usage */
    private static Usage extractUsageFromResponses(OpenAIResponsesResponse resp) {
        Usage usage = new Usage();
        if (resp != null && resp.getUsage() != null) {
            var u = resp.getUsage();
            if (u.getInputTokens() != null) usage.setPromptTokens(u.getInputTokens());
            if (u.getOutputTokens() != null) usage.setCompletionTokens(u.getOutputTokens());
            if (u.getTotalTokens() != null) usage.setTotalTokens(u.getTotalTokens());
        }
        return usage;
    }

    // ======================== Responses→Chat 非流式 ========================

    /**
     * Responses→Chat 转换（非流式）      * <p>
     * 完整流程：读取上游 HTTP 响应 byte[] → 解析 OpenAIResponsesResponse →
     * 检查 API 错误 → Responses→Chat 转换 → Usage 兜底估算 →
     * 格式转换（OpenAI/Claude/Gemini）→ ioCopyBytesGracefully 写入客户端。
     *
     * @param info         RelayInfo 上下文
     * @param responseBody 上游 Responses API 响应体字节
     * @param resp         HttpServletResponse（可为 null，null 时不写入客户端）
     * @return Usage 计费信息
     */
    public static Usage oaiResponsesToChatHandler(RelayInfo info, byte[] responseBody,
                                                   HttpServletResponse resp) throws Exception {
        if (responseBody == null || responseBody.length == 0) {
            throw new IllegalArgumentException("response body is empty");
        }

        // 1. 解析 OpenAIResponsesResponse
        OpenAIResponsesResponse responsesResp;
        try {
            responsesResp = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), OpenAIResponsesResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Responses API response: " + e.getMessage(), e);
        }

        // 2. 检查 API 错误
        OpenAIError oaiError = responsesResp.getOpenAIError();
        if (oaiError != null && oaiError.getType() != null && !oaiError.getType().isEmpty()) {
            throw new RuntimeException("Responses API error [" + oaiError.getType() + "]: " + oaiError.getMessage());
        }

        // 3. 生成 Chat Completions 响应 ID
        String chatId = "chatcmpl-" + (info.getRequestId() != null ? info.getRequestId() : "unknown");

        // 4. Responses → Chat 转换
        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(responsesResp, chatId);

        // 5. Usage 兜底：无有效 usage 时通过文本估算
        Usage usage = result.usage();
        if (usage == null || usage.getTotalTokens() == 0) {
            String text = ResponseToChatService.extractOutputTextFromResponses(responsesResp);
            usage = new Usage();
            usage.setPromptTokens(info.getEstimatePromptTokens());
            usage.setCompletionTokens(estimateTextTokens(text));
            usage.setTotalTokens(info.getEstimatePromptTokens() + usage.getCompletionTokens());
            // 序列化用 setUsage 需 OpenAIResponseDTO.Usage 类型，此处 result.response() 已持有原 usage
        }

        // 6. 格式转换 + 序列化写入客户端
        String relayFormat = info.getRelayFormat();
        String relayFormatLower = relayFormat != null ? relayFormat.toLowerCase() : "";
        byte[] responseBytes;

        if ("claude".equals(relayFormatLower)) {
            // OpenAI → Claude 格式转换
            ClaudeResponse claudeResp = ConvertService.responseOpenAI2Claude(result.response(), info);
            responseBytes = Convert.toJSONString(claudeResp).getBytes(StandardCharsets.UTF_8);
        } else if ("gemini".equals(relayFormatLower)) {
            // OpenAI → Gemini 格式转换
            GeminiChatResponse geminiResp = ConvertService.responseOpenAI2Gemini(result.response(), info);
            responseBytes = Convert.toJSONString(geminiResp).getBytes(StandardCharsets.UTF_8);
        } else {
            // OpenAI 格式（默认）
            responseBytes = Convert.toJSONString(result.response()).getBytes(StandardCharsets.UTF_8);
        }

        if (resp != null) {
            resp.setContentType("application/json");
            resp.getOutputStream().write(responseBytes);
        }
        return usage;
    }

    /**
     * 简单字符级 token 估算（无 HttpServletRequest 时的回退方案），
     * 约为 TokenEstimatorService 的简化版本。
     */
    private static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：英文 ~4 chars/token，中文 ~1.5 chars/token
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }

    // ======================== Responses→Chat 流式 ========================

    /**
     * Responses→Chat 流式转换      * <p>
     * 核心流程：
     * 1. 从上游 SSE 流逐行扫描 Responses 流事件
     * 2. 根据事件类型发送对应的 Chat Completions 流块
     * 3. 流末发送 stop chunk + 兜底 usage 估算 + final usage + [DONE]
     * <p>
     * <b>当前仅支持 OpenAI 格式输出</b>。Claude/Gemini 流式格式转换需逐 chunk 调用
     * ConvertService.streamResponseOpenAI2Claude/Gemini + ClaudeConvertInfo 状态管理，
     * 实现方式参考 Go OaiResponsesToChatStreamHandler 的 sendChatChunk 闭包。
     *
     * @param info        RelayInfo 上下文
     * @param inputStream 上游 SSE 响应流
     * @param response    HttpServletResponse
     * @return Usage 计费信息
     */
    public static Usage oaiResponsesToChatStreamHandler(RelayInfo info, InputStream inputStream, HttpServletResponse response) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("response body is null");
        }

        String responseId = "chatcmpl-" + (info.getRequestId() != null ? info.getRequestId() : "unknown");
        ResponsesStreamState state = new ResponsesStreamState(
                responseId,
                System.currentTimeMillis() / 1000,
                info.getUpstreamModelName()
        );

        // ========== 状态累积器 ==========
        Usage usage = new Usage();
        StringBuilder outputText = new StringBuilder();
        StringBuilder usageText = new StringBuilder();

        // tool_call 状态追踪
        Map<String, Integer> toolCallIndexByID = new LinkedHashMap<>();
        Map<String, String> toolCallNameByID = new LinkedHashMap<>();
        Map<String, String> toolCallArgsByID = new LinkedHashMap<>();
        Map<String, Boolean> toolCallNameSent = new LinkedHashMap<>();
        Map<String, String> toolCallCanonicalIDByItemID = new LinkedHashMap<>();

        // 读取上游 SSE 流
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        StringBuilder sseData = new StringBuilder();

        try {
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // SSE 事件结束
                    if (sseData.length() > 0) {
                        processStreamEvent(sseData.toString().trim(), info, state,
                                outputText, usageText, usage, response,
                                toolCallIndexByID, toolCallNameByID,
                                toolCallArgsByID, toolCallNameSent, toolCallCanonicalIDByItemID);
                        sseData.setLength(0);
                    }
                    continue;
                }
                if (line.startsWith("data: ")) {
                    sseData.append(line.substring(6));
                }
                // 非 data: 行忽略（event: / id: / retry:）
            }
        } finally {
            reader.close();
        }

        // 处理最后未闭合的事件
        if (sseData.length() > 0) {
            processStreamEvent(sseData.toString().trim(), info, state,
                    outputText, usageText, usage, response,
                    toolCallIndexByID, toolCallNameByID,
                    toolCallArgsByID, toolCallNameSent, toolCallCanonicalIDByItemID);
        }

        // 如果有流错误
        if (state.streamError.length() > 0) {
            throw new RuntimeException("Stream error: " + state.streamError);
        }

        // 兜底 Usage 估算
        if (usage.getTotalTokens() == 0) {
            String text = usageText.length() > 0 ? usageText.toString() : outputText.toString();
            Usage fallbackUsage = new Usage();
            fallbackUsage.setPromptTokens(info.getEstimatePromptTokens());
            fallbackUsage.setCompletionTokens(estimateTextTokens(text));
            fallbackUsage.setTotalTokens(info.getEstimatePromptTokens() + fallbackUsage.getCompletionTokens());
            usage = fallbackUsage;
        }

        // 发送流末 chunks
        if (response != null) {
            if (!state.sentStart) {
                RelayCommonHelper.objectData(response,
                        generateStartEmptyResponse(responseId, state.createdAt, state.model, null));
            }
            if (!state.sentStop) {
                String finishReason = state.sawToolCall && outputText.isEmpty() ? "tool_calls" : "stop";
                RelayCommonHelper.objectData(response,
                        generateStopResponse(responseId, state.createdAt, state.model, finishReason));
            }
            if (usage.getTotalTokens() > 0) {
                RelayCommonHelper.objectData(response,
                        generateFinalUsageResponse(responseId, state.createdAt, state.model, usage));
            }
            RelayCommonHelper.done(response);
        }

        return usage;
    }

    /**
     * 处理单条 SSE 事件数据      */
    private static void processStreamEvent(String data, RelayInfo info, ResponsesStreamState state,
                                           StringBuilder outputText, StringBuilder usageText, Usage usage,
                                           HttpServletResponse response,
                                           Map<String, Integer> toolCallIndexByID,
                                           Map<String, String> toolCallNameByID,
                                           Map<String, String> toolCallArgsByID,
                                           Map<String, Boolean> toolCallNameSent,
                                           Map<String, String> toolCallCanonicalIDByItemID) throws Exception {
        if (data == null || data.isEmpty() || data.equals("[DONE]")) {
            // [DONE] 标记 → 流结束，由外层处理
            return;
        }

        ResponsesStreamResponse streamResp;
        try {
            streamResp = Convert.toJavaBean(data, ResponsesStreamResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse responses stream event: {} — {}", e.getMessage(), data);
            return;
        }

        String eventType = streamResp.getType();
        if (eventType == null) return;

        switch (eventType) {
            // ---- response.created：提取 model / created_at ----
            case "response.created" -> {
                if (streamResp.getResponse() != null) {
                    if (streamResp.getResponse().getModel() != null) {
                        state.model = streamResp.getResponse().getModel();
                    }
                    if (streamResp.getResponse().getCreatedAt() != null) {
                        state.createdAt = streamResp.getResponse().getCreatedAt();
                    }
                }
            }

            // ---- response.reasoning_summary_text.delta：发送推理摘要 delta ----
            case "response.reasoning_summary_text.delta" -> {
                String delta = streamResp.getDelta();
                if (delta != null && !delta.isEmpty()) {
                    if (state.needsReasoningSummarySeparator) {
                        if (delta.startsWith("\n\n")) {
                            state.needsReasoningSummarySeparator = false;
                        } else if (delta.startsWith("\n")) {
                            delta = "\n" + delta;
                            state.needsReasoningSummarySeparator = false;
                        } else {
                            delta = "\n\n" + delta;
                            state.needsReasoningSummarySeparator = false;
                        }
                    }
                    usageText.append(delta);
                    if (response != null) {
                        sendStartIfNeeded(response, state);
                        ChatCompletionsStreamResponse chunk = new ChatCompletionsStreamResponse();
                        chunk.setId(state.responseId);
                        chunk.setObject("chat.completion.chunk");
                        chunk.setCreated(state.createdAt);
                        chunk.setModel(state.model);
                        ChatCompletionsStreamResponse.Choice choice = new ChatCompletionsStreamResponse.Choice();
                        choice.setIndex(0);
                        ChatCompletionsStreamResponse.Delta deltaChunk = new ChatCompletionsStreamResponse.Delta();
                        deltaChunk.setReasoningContent(delta);
                        choice.setDelta(deltaChunk);
                        chunk.setChoices(List.of(choice));
                        RelayCommonHelper.objectData(response, chunk);
                    }
                    state.hasSentReasoningSummary = true;
                }
            }

            case "response.reasoning_summary_text.done" -> {
                if (state.hasSentReasoningSummary) {
                    state.needsReasoningSummarySeparator = true;
                }
            }

            // ---- response.output_text.delta：发送文本 delta ----
            case "response.output_text.delta" -> {
                if (response != null) {
                    sendStartIfNeeded(response, state);
                }
                String delta = streamResp.getDelta();
                if (delta != null) {
                    outputText.append(delta);
                    usageText.append(delta);
                    if (response != null) {
                        ChatCompletionsStreamResponse chunk = new ChatCompletionsStreamResponse();
                        chunk.setId(state.responseId);
                        chunk.setObject("chat.completion.chunk");
                        chunk.setCreated(state.createdAt);
                        chunk.setModel(state.model);
                        ChatCompletionsStreamResponse.Choice choice = new ChatCompletionsStreamResponse.Choice();
                        choice.setIndex(0);
                        ChatCompletionsStreamResponse.Delta deltaChunk = new ChatCompletionsStreamResponse.Delta();
                        deltaChunk.setContent(delta);
                        choice.setDelta(deltaChunk);
                        chunk.setChoices(List.of(choice));
                        RelayCommonHelper.objectData(response, chunk);
                    }
                }
            }

            // ---- response.output_item.added/done：function_call 工具调用 ----
            case "response.output_item.added", "response.output_item.done" -> {
                var item = streamResp.getItem();
                if (item == null || !"function_call".equals(item.getType())) break;

                String itemID = trimToNull(item.getId());
                String callID = trimToNull(item.getCallId());
                if (callID == null) callID = itemID;
                if (callID == null) break;

                if (itemID != null) {
                    toolCallCanonicalIDByItemID.put(itemID, callID);
                }

                String name = trimToNull(item.getName());
                if (name != null) {
                    toolCallNameByID.put(callID, name);
                }

                // 累积 arguments
                String newArgs = item.argumentsString();
                if (newArgs != null && !newArgs.isEmpty()) {
                    toolCallArgsByID.put(callID, newArgs);
                }

                // 发送 tool_call chunk
                if (response != null && name != null) {
                    sendStartIfNeeded(response, state);
                    state.sawToolCall = true;
                    try {
                        Map<String, Object> tcChunk = new LinkedHashMap<>();
                        tcChunk.put("id", state.responseId);
                        tcChunk.put("object", "chat.completion.chunk");
                        tcChunk.put("created", state.createdAt);
                        tcChunk.put("model", state.model);
                        List<Map<String, Object>> choices = new ArrayList<>();
                        Map<String, Object> choice = new LinkedHashMap<>();
                        choice.put("index", 0);
                        Map<String, Object> delta = new LinkedHashMap<>();
                        List<Map<String, Object>> toolCalls = new ArrayList<>();
                        Map<String, Object> tc = new LinkedHashMap<>();
                        tc.put("index", 0);
                        tc.put("id", callID);
                        tc.put("type", "function");
                        Map<String, Object> func = new LinkedHashMap<>();
                        func.put("name", name);
                        func.put("arguments", newArgs != null ? newArgs : "");
                        tc.put("function", func);
                        toolCalls.add(tc);
                        delta.put("tool_calls", toolCalls);
                        choice.put("delta", delta);
                        choices.add(choice);
                        tcChunk.put("choices", choices);
                        RelayCommonHelper.objectData(response, tcChunk);
                    } catch (Exception e) {
                        log.warn("Failed to send tool_call chunk: {}", e.getMessage());
                    }
                }
                if (name != null) {
                    usageText.append(name);
                }
                if (newArgs != null) {
                    usageText.append(newArgs);
                }
            }

            // ---- response.function_call_arguments.delta：累积参数 delta ----
            case "response.function_call_arguments.delta" -> {
                String itemID = trimToNull(streamResp.getItemId());
                String callID = toolCallCanonicalIDByItemID.get(itemID);
                if (callID == null) callID = itemID;
                if (callID == null) break;

                String delta = streamResp.getDelta();
                if (delta != null) {
                    toolCallArgsByID.merge(callID, delta, String::concat);
                    usageText.append(delta);
                    if (response != null) {
                        sendStartIfNeeded(response, state);
                        state.sawToolCall = true;
                        // 发送 arguments delta（已计算 index）
                        int idx = toolCallIndexByID.getOrDefault(callID, 0);
                        try {
                            Map<String, Object> argChunk = new LinkedHashMap<>();
                            argChunk.put("id", state.responseId);
                            argChunk.put("object", "chat.completion.chunk");
                            argChunk.put("created", state.createdAt);
                            argChunk.put("model", state.model);
                            List<Map<String, Object>> choices = new ArrayList<>();
                            Map<String, Object> choice = new LinkedHashMap<>();
                            choice.put("index", 0);
                            Map<String, Object> deltaMap = new LinkedHashMap<>();
                            List<Map<String, Object>> toolCalls = new ArrayList<>();
                            Map<String, Object> tc = new LinkedHashMap<>();
                            tc.put("index", idx);
                            tc.put("id", callID);
                            tc.put("type", "function");
                            Map<String, Object> func = new LinkedHashMap<>();
                            func.put("arguments", delta);
                            tc.put("function", func);
                            toolCalls.add(tc);
                            deltaMap.put("tool_calls", toolCalls);
                            choice.put("delta", deltaMap);
                            choices.add(choice);
                            argChunk.put("choices", choices);
                            RelayCommonHelper.objectData(response, argChunk);
                        } catch (Exception e) {
                            log.warn("Failed to send function_call_arguments delta: {}", e.getMessage());
                        }
                    }
                }
            }

            case "response.function_call_arguments.done" -> {
                // arguments 累积完成
            }

            // ---- response.completed：提取 usage + 发送 stop ----
            case "response.completed" -> {
                if (streamResp.getResponse() != null) {
                    var resp = streamResp.getResponse();
                    if (resp.getModel() != null) {
                        state.model = resp.getModel();
                    }
                    if (resp.getCreatedAt() != null) {
                        state.createdAt = resp.getCreatedAt();
                    }
                    if (resp.getUsage() != null) {
                        var u = resp.getUsage();
                        if (u.getInputTokens() != null && u.getInputTokens() != 0) {
                            usage.setPromptTokens(u.getInputTokens());
                        }
                        if (u.getOutputTokens() != null && u.getOutputTokens() != 0) {
                            usage.setCompletionTokens(u.getOutputTokens());
                        }
                        if (u.getTotalTokens() != null && u.getTotalTokens() != 0) {
                            usage.setTotalTokens(u.getTotalTokens());
                        } else {
                            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
                        }
                        if (u.getInputTokensDetails() != null) {
                            var id = u.getInputTokensDetails();
                            var pd = new Usage.InputTokensDetails();
                            pd.setCachedTokens(id.getCachedTokens() != null ? id.getCachedTokens() : 0);
                            pd.setImageTokens(id.getImageTokens() != null ? id.getImageTokens() : 0);
                            pd.setAudioTokens(id.getAudioTokens() != null ? id.getAudioTokens() : 0);
                            usage.setInputTokensDetails(pd);
                        }
                        if (u.getPromptTokensDetails() != null) {
                            var details = u.getPromptTokensDetails();
                            var ptd = new Usage.PromptTokensDetails();
                            ptd.setCachedTokens(details.getCachedTokens() != null ? details.getCachedTokens() : 0);
                            usage.setPromptTokensDetails(ptd);
                        }
                        if (u.getCompletionTokenDetails() != null
                                && u.getCompletionTokenDetails().getReasoningTokens() != null
                                && u.getCompletionTokenDetails().getReasoningTokens() != 0) {
                            var ctd = new Usage.CompletionTokenDetails();
                            ctd.setReasoningTokens(u.getCompletionTokenDetails().getReasoningTokens());
                            usage.setCompletionTokenDetails(ctd);
                        }
                    }
                }
                // 发送 stop chunk
                if (response != null) {
                    sendStartIfNeeded(response, state);
                    state.sentStop = true;
                    String finishReason = state.sawToolCall && outputText.isEmpty() ? "tool_calls" : "stop";
                    RelayCommonHelper.objectData(response,
                            generateStopResponse(state.responseId, state.createdAt, state.model, finishReason));
                }
            }

            // ---- response.error / response.failed：错误处理 ----
            case "response.error", "response.failed" -> {
                if (streamResp.getResponse() != null) {
                    var oaiErr = streamResp.getResponse().getOpenAIError();
                    if (oaiErr != null && oaiErr.getType() != null && !oaiErr.getType().isEmpty()) {
                        state.streamError.append(oaiErr.getType()).append(": ").append(oaiErr.getMessage());
                    }
                }
                if (state.streamError.length() == 0) {
                    state.streamError.append("responses stream error: ").append(eventType);
                }
            }

            default -> {
                // 忽略未知事件类型
            }
        }
    }

    private static void sendStartIfNeeded(HttpServletResponse response, ResponsesStreamState state) throws Exception {
        if (response == null || state.sentStart) {
            return;
        }
        RelayCommonHelper.objectData(response,
                generateStartEmptyResponse(state.responseId, state.createdAt, state.model, null));
        state.sentStart = true;
    }

    // ======================== 辅助方法 ========================

    private static final class ResponsesStreamState {
        private final String responseId;
        private long createdAt;
        private String model;
        private boolean sentStart;
        private boolean sentStop;
        private boolean sawToolCall;
        private boolean hasSentReasoningSummary;
        private boolean needsReasoningSummarySeparator;
        private final StringBuilder streamError = new StringBuilder();

        private ResponsesStreamState(String responseId, long createdAt, String model) {
            this.responseId = responseId;
            this.createdAt = createdAt;
            this.model = model;
        }
    }

    /**
     * 生成 Chat Completions 起始空 chunk      */
    static ChatCompletionsStreamResponse generateStartEmptyResponse(
            String id, long createdAt, String model, String fingerprint) {
        ChatCompletionsStreamResponse chunk = new ChatCompletionsStreamResponse();
        chunk.setId(id);
        chunk.setObject("chat.completion.chunk");
        chunk.setCreated(createdAt);
        chunk.setModel(model);
        chunk.setSystemFingerprint(fingerprint);

        ChatCompletionsStreamResponse.Choice choice = new ChatCompletionsStreamResponse.Choice();
        choice.setIndex(0);
        ChatCompletionsStreamResponse.Delta delta = new ChatCompletionsStreamResponse.Delta();
        delta.setRole("assistant");
        delta.setContent("");
        choice.setDelta(delta);

        chunk.setChoices(List.of(choice));
        return chunk;
    }

    /**
     * 生成 Chat Completions stop chunk      */
    static ChatCompletionsStreamResponse generateStopResponse(
            String id, long createdAt, String model, String finishReason) {
        ChatCompletionsStreamResponse chunk = new ChatCompletionsStreamResponse();
        chunk.setId(id);
        chunk.setObject("chat.completion.chunk");
        chunk.setCreated(createdAt);
        chunk.setModel(model);

        ChatCompletionsStreamResponse.Choice choice = new ChatCompletionsStreamResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason(finishReason);

        chunk.setChoices(List.of(choice));
        return chunk;
    }

    /**
     * 生成 Chat Completions final usage chunk      */
    static ChatCompletionsStreamResponse generateFinalUsageResponse(
            String id, long createdAt, String model, Usage usage) {
        ChatCompletionsStreamResponse chunk = new ChatCompletionsStreamResponse();
        chunk.setId(id);
        chunk.setObject("chat.completion.chunk");
        chunk.setCreated(createdAt);
        chunk.setModel(model);
        chunk.setChoices(List.of());
        chunk.setUsage(usage);
        return chunk;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
