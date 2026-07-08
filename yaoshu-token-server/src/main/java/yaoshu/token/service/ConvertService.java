package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.pojo.dto.ClaudeDTO.*;
import yaoshu.token.pojo.dto.GeminiDTO.*;
import yaoshu.token.pojo.dto.OpenAIRequestDTO.*;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponseChoice;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.reasonmap.ReasonMapHelper;

import java.util.*;

/**
 * 格式转换服务  * <p>
 * 核心方法：OpenAI ↔ Claude ↔ Gemini 格式转换（请求/响应/流式）。
 */
@Slf4j
public final class ConvertService {

    private ConvertService() {
    }

    // ======================== 工具方法 ========================

    /** Go toJSONString 等价 */
    private static String toJSONString(Object v) {
        if (v == null) return "{}";
        return Convert.toJSONString(v);
    }

    /** 判断 Go Message 是否字符串内容 */
    private static boolean isStringContent(Message msg) {
        return msg != null && msg.getContent() instanceof String;
    }

    /** 获取字符串内容 */
    private static String getStringContent(Message msg) {
        if (msg == null) return "";
        Object c = msg.getContent();
        if (c instanceof String s) return s;
        if (c instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object type = m.get("type");
                    if ("text".equals(type) || "input_text".equals(type)) {
                        Object text = m.get("text");
                        if (text != null) sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** 设置字符串内容 */
    private static void setStringContent(Message msg, String s) {
        msg.setContent(s);
    }

    /** 解析内容列表（返回 List<Map> 通用形态） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseContent(Message msg) {
        if (msg == null) return Collections.emptyList();
        Object c = msg.getContent();
        if (c instanceof List<?> list) {
            if (list.isEmpty()) return Collections.emptyList();
            if (list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) list;
            }
        }
        if (c instanceof String s && !s.isEmpty()) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", "text");
            item.put("text", s);
            return Collections.singletonList(item);
        }
        return Collections.emptyList();
    }

    /** 设置内容列表 */
    private static void setMediaContent(Message msg, List<Map<String, Object>> media) {
        msg.setContent(media);
    }

    /** 解析 ToolCalls（返回 List<Map> 通用形态） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseToolCallsRaw(Message msg) {
        if (msg == null || msg.getToolCalls() == null) return Collections.emptyList();
        Object tc = msg.getToolCalls();
        if (tc instanceof List<?> list) {
            if (list.isEmpty()) return Collections.emptyList();
            if (list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) list;
            }
        }
        return Collections.emptyList();
    }

    /** 解析 ToolCalls（转换为 ToolCallRequest 列表） */
    @SuppressWarnings("unchecked")
    private static List<ToolCallRequest> parseToolCalls(Message msg) {
        if (msg == null || msg.getToolCalls() == null) return Collections.emptyList();
        Object tc = msg.getToolCalls();
        if (tc instanceof List<?> list) {
            if (list.isEmpty()) return Collections.emptyList();
            if (list.get(0) instanceof ToolCallRequest) {
                return (List<ToolCallRequest>) list;
            }
            return list.stream()
                    .map(item -> Convert.toJavaBean(item, ToolCallRequest.class))
                    .toList();
        }
        return Collections.emptyList();
    }

    /** 设置 ToolCalls */
    private static void setToolCalls(Message msg, List<ToolCallRequest> toolCalls) {
        msg.setToolCalls(toolCalls);
    }

    /** Go stopReasonOpenAI2Claude */
    private static String stopReasonOpenAI2Claude(String reason) {
        return ReasonMapHelper.openAIFinishReasonToClaudeStopReason(reason);
    }

    /** Go NormalizeCacheCreationSplit */
    private static int[] normalizeCacheCreationSplit(int totalTokens, int tokens5m, int tokens1h) {
        int remainder = Math.max(totalTokens - tokens5m - tokens1h, 0);
        return new int[]{tokens5m + remainder, tokens1h};
    }

    /** Go buildClaudeUsageFromOpenAIUsage */
    private static ClaudeUsage buildClaudeUsageFromOpenAIUsage(Usage oaiUsage) {
        if (oaiUsage == null) return null;
        ClaudeUsage usage = new ClaudeUsage();
        usage.setInputTokens(oaiUsage.getPromptTokens());
        usage.setOutputTokens(oaiUsage.getCompletionTokens());
        // PromptTokensDetails.cachedCreationTokens / cachedTokens（缓存读取 + 缓存创建）
        int cachedCreationTokens = 0;
        if (oaiUsage.getPromptTokensDetails() != null) {
            usage.setCacheReadInputTokens(oaiUsage.getPromptTokensDetails().getCachedTokens());
            Integer cct = oaiUsage.getPromptTokensDetails().getCachedCreationTokens();
            cachedCreationTokens = cct == null ? 0 : cct;
        }
        usage.setCacheCreationInputTokens(cachedCreationTokens);
        // 5m / 1h 分桶（NormalizeCacheCreationSplit）
        int[] split = normalizeCacheCreationSplit(
                cachedCreationTokens,
                oaiUsage.getClaudeCacheCreation5mTokens(),
                oaiUsage.getClaudeCacheCreation1hTokens());
        int cacheCreation5m = split[0];
        int cacheCreation1h = split[1];
        if (cacheCreation5m > 0 || cacheCreation1h > 0) {
            ClaudeCacheCreationUsage cacheCreation = new ClaudeCacheCreationUsage();
            cacheCreation.setEphemeral5mInputTokens(cacheCreation5m);
            cacheCreation.setEphemeral1hInputTokens(cacheCreation1h);
            usage.setCacheCreation(cacheCreation);
        }
        return usage;
    }

    /** 从 RelayInfo.Usage 转换为 dto.Usage */
    private static Usage toDtoUsage(RelayInfo.Usage relayUsage) {
        if (relayUsage == null) return null;
        Usage u = new Usage();
        u.setPromptTokens(relayUsage.getPromptTokens());
        u.setCompletionTokens(relayUsage.getCompletionTokens());
        u.setTotalTokens(relayUsage.getTotalTokens());
        return u;
    }

    /** Go convertGeminiRoleToOpenAI */
    private static String convertGeminiRoleToOpenAI(String geminiRole) {
        return switch (geminiRole) {
            case "user" -> "user";
            case "model" -> "assistant";
            case "function" -> "function";
            default -> "user";
        };
    }

    /** Go extractTextFromGeminiParts */
    private static String extractTextFromGeminiParts(List<GeminiPart> parts) {
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (GeminiPart part : parts) {
            if (part.getText() != null && !part.getText().isEmpty()) {
                sb.append(part.getText());
            }
        }
        return sb.toString();
    }

    /** Go generateStopBlock */
    private static ClaudeResponse generateStopBlock(int index) {
        ClaudeResponse resp = new ClaudeResponse();
        resp.setType("content_block_stop");
        resp.setIndex(index);
        return resp;
    }

    // ======================== Claude → OpenAI 请求转换 ========================

    /**
     * 将 Claude 请求转换为 OpenAI 请求      */
    public static OpenAIRequestDTO claudeToOpenAIRequest(ClaudeRequest claudeRequest, RelayInfo info) {
        OpenAIRequestDTO openAIRequest = new OpenAIRequestDTO();
        openAIRequest.setModel(claudeRequest.getModel());
        openAIRequest.setTemperature(claudeRequest.getTemperature());

        if (claudeRequest.getMaxTokens() != null) {
            openAIRequest.setMaxTokens(claudeRequest.getMaxTokens());
        }
        if (claudeRequest.getTopP() != null) {
            openAIRequest.setTopP(claudeRequest.getTopP());
        }
        if (claudeRequest.getTopK() != null) {
            openAIRequest.setTopK(claudeRequest.getTopK());
        }
        if (claudeRequest.getStream() != null) {
            openAIRequest.setStream(claudeRequest.getStream());
        }

        boolean isOpenRouter = info.getChannelType() == ChannelConstants.CHANNEL_TYPE_OPENROUTER;

        // Thinking → Reasoning（OpenRouter 通道）
        if (isOpenRouter) {
            Thinking thinking = claudeRequest.getThinking();
            if (thinking != null) {
                Map<String, Object> reasoning = new HashMap<>();
                if ("enabled".equals(thinking.getType())) {
                    reasoning.put("enabled", true);
                    reasoning.put("max_tokens", thinking.getBudgetTokens());
                } else if ("adaptive".equals(thinking.getType())) {
                    reasoning.put("enabled", true);
                }
                openAIRequest.setReasoning(reasoning);
            }
        } else {
            // 非 OpenRouter：追加 -thinking 后缀
            String thinkingSuffix = "-thinking";
            String originModel = info.getOriginModelName();
            if (originModel != null && originModel.endsWith(thinkingSuffix)
                    && !openAIRequest.getModel().endsWith(thinkingSuffix)) {
                openAIRequest.setModel(openAIRequest.getModel() + thinkingSuffix);
            }
        }

        // Stop sequences
        List<String> stopSequences = claudeRequest.getStopSequences();
        if (stopSequences != null) {
            if (stopSequences.size() == 1) {
                openAIRequest.setStop(stopSequences.get(0));
            } else if (stopSequences.size() > 1) {
                openAIRequest.setStop(stopSequences);
            }
        }

        // Tools 转换
        List<ToolCallRequest> openAITools = new ArrayList<>();
        if (claudeRequest.getTools() instanceof List<?> claudeTools) {
            for (Object claudeTool : claudeTools) {
                Map<String, Object> tool = Convert.toJSONObject(claudeTool);
                ToolCallRequest openAITool = new ToolCallRequest();
                openAITool.setType("function");
                FunctionRequest func = new FunctionRequest();
                func.setName((String) tool.get("name"));
                func.setDescription((String) tool.get("description"));
                func.setParameters(tool.get("input_schema"));
                openAITool.setFunction(func);
                openAITools.add(openAITool);
            }
        }
        openAIRequest.setTools(openAITools.isEmpty() ? null : openAITools);

        // Messages 转换
        List<Message> openAIMessages = new ArrayList<>();

        // System message
        Object system = claudeRequest.getSystem();
        if (system instanceof String sysStr && !sysStr.isEmpty()) {
            Message sysMsg = new Message();
            sysMsg.setRole("system");
            setStringContent(sysMsg, sysStr);
            openAIMessages.add(sysMsg);
        } else if (system instanceof List<?> sysList && !sysList.isEmpty()) {
            Message sysMsg = new Message();
            sysMsg.setRole("system");
            StringBuilder sysText = new StringBuilder();
            for (Object item : sysList) {
                if (item instanceof Map<?, ?> m) {
                    Object text = m.get("text");
                    if (text != null) sysText.append(text);
                }
            }
            if (!sysText.isEmpty()) {
                setStringContent(sysMsg, sysText.toString());
                openAIMessages.add(sysMsg);
            }
        }

        // Claude messages → OpenAI messages
        List<ClaudeMessage> claudeMessages = claudeRequest.getMessages();
        if (claudeMessages != null) {
            for (ClaudeMessage claudeMsg : claudeMessages) {
                Message oaiMsg = new Message();
                oaiMsg.setRole(claudeMsg.getRole());

                Object content = claudeMsg.getContent();
                if (content instanceof String s) {
                    setStringContent(oaiMsg, s);
                } else if (content instanceof List<?> contents) {
                    List<ToolCallRequest> toolCalls = new ArrayList<>();
                    List<Map<String, Object>> mediaMsgs = new ArrayList<>();
                    for (Object item : contents) {
                        if (item instanceof Map<?, ?> mediaMsg) {
                            String type = (String) mediaMsg.get("type");
                            switch (type) {
                                case "text", "input_text" -> {
                                    Map<String, Object> mc = new HashMap<>();
                                    mc.put("type", "text");
                                    mc.put("text", mediaMsg.get("text"));
                                    mc.put("cache_control", mediaMsg.get("cache_control"));
                                    mediaMsgs.add(mc);
                                }
                                case "image" -> {
                                    Map<String, Object> mc = new HashMap<>();
                                    mc.put("type", "image_url");
                                    Map<?, ?> source = (Map<?, ?>) mediaMsg.get("source");
                                    String imageData = "data:" + source.get("media_type") + ";base64," + source.get("data");
                                    Map<String, Object> imageUrl = new HashMap<>();
                                    imageUrl.put("url", imageData);
                                    mc.put("image_url", imageUrl);
                                    mediaMsgs.add(mc);
                                }
                                case "tool_use" -> {
                                    ToolCallRequest tc = new ToolCallRequest();
                                    tc.setId((String) mediaMsg.get("id"));
                                    tc.setType("function");
                                    FunctionRequest func = new FunctionRequest();
                                    func.setName((String) mediaMsg.get("name"));
                                    func.setArguments(toJSONString(mediaMsg.get("input")));
                                    tc.setFunction(func);
                                    toolCalls.add(tc);
                                }
                                case "tool_result" -> {
                                    String toolName = (String) mediaMsg.get("name");
                                    if (toolName == null || toolName.isEmpty()) {
                                        toolName = searchToolNameByToolCallId(
                                                claudeRequest, (String) mediaMsg.get("tool_use_id"));
                                    }
                                    Message toolMsg = new Message();
                                    toolMsg.setRole("tool");
                                    toolMsg.setName(toolName);
                                    toolMsg.setToolCallId((String) mediaMsg.get("tool_use_id"));
                                    Object resultContent = mediaMsg.get("content");
                                    if (resultContent instanceof String s) {
                                        setStringContent(toolMsg, s);
                                    } else if (resultContent != null) {
                                        setStringContent(toolMsg, toJSONString(resultContent));
                                    }
                                    openAIMessages.add(toolMsg);
                                }
                            }
                        }
                    }
                    if (!toolCalls.isEmpty()) {
                        setToolCalls(oaiMsg, toolCalls);
                    }
                    if (!mediaMsgs.isEmpty() && toolCalls.isEmpty()) {
                        setMediaContent(oaiMsg, mediaMsgs);
                    }
                }
                if (!parseContent(oaiMsg).isEmpty() || !parseToolCalls(oaiMsg).isEmpty()) {
                    openAIMessages.add(oaiMsg);
                }
            }
        }
        openAIRequest.setMessages(openAIMessages);
        return openAIRequest;
    }

    /** 在 Claude 请求中根据 tool_use_id 搜索工具名称 */
    private static String searchToolNameByToolCallId(ClaudeRequest request, String toolUseId) {
        if (toolUseId == null || request.getMessages() == null) return "";
        for (ClaudeMessage msg : request.getMessages()) {
            Object content = msg.getContent();
            if (content instanceof List<?> contents) {
                for (Object item : contents) {
                    if (item instanceof Map<?, ?> m
                            && "tool_use".equals(m.get("type"))
                            && toolUseId.equals(m.get("id"))) {
                        return (String) m.get("name");
                    }
                }
            }
        }
        return "";
    }

    // ======================== Gemini → OpenAI 请求转换 ========================

    /**
     * 将 Gemini 请求转换为 OpenAI 请求      */
    public static OpenAIRequestDTO geminiToOpenAIRequest(GeminiChatRequest geminiRequest, RelayInfo info) {
        OpenAIRequestDTO openaiRequest = new OpenAIRequestDTO();
        openaiRequest.setModel(info.getUpstreamModelName());
        openaiRequest.setStream(info.isStream());

        // Messages 转换
        List<Message> messages = new ArrayList<>();
        List<GeminiChatContent> contents = geminiRequest.getContents();
        if (contents != null) {
            for (GeminiChatContent content : contents) {
                Message message = new Message();
                message.setRole(convertGeminiRoleToOpenAI(content.getRole()));

                List<Map<String, Object>> mediaContents = new ArrayList<>();
                List<ToolCallRequest> toolCalls = new ArrayList<>();
                List<GeminiPart> parts = content.getParts();
                if (parts != null) {
                    for (GeminiPart part : parts) {
                        if (part.getText() != null && !part.getText().isEmpty()) {
                            Map<String, Object> mc = new HashMap<>();
                            mc.put("type", "text");
                            mc.put("text", part.getText());
                            mediaContents.add(mc);
                        } else if (part.getInlineData() != null) {
                            Map<String, Object> mc = new HashMap<>();
                            mc.put("type", "image_url");
                            Map<String, Object> imageUrl = new HashMap<>();
                            imageUrl.put("url", "data:" + part.getInlineData().getMimeType()
                                    + ";base64," + part.getInlineData().getData());
                            imageUrl.put("detail", "auto");
                            imageUrl.put("mime_type", part.getInlineData().getMimeType());
                            mc.put("image_url", imageUrl);
                            mediaContents.add(mc);
                        } else if (part.getFileData() != null) {
                            Map<String, Object> mc = new HashMap<>();
                            mc.put("type", "image_url");
                            Map<String, Object> imageUrl = new HashMap<>();
                            imageUrl.put("url", part.getFileData().getFileUri());
                            imageUrl.put("detail", "auto");
                            imageUrl.put("mime_type", part.getFileData().getMimeType());
                            mc.put("image_url", imageUrl);
                            mediaContents.add(mc);
                        } else if (part.getFunctionCall() != null) {
                            ToolCallRequest tc = new ToolCallRequest();
                            tc.setId("call_" + (toolCalls.size() + 1));
                            tc.setType("function");
                            FunctionRequest func = new FunctionRequest();
                            func.setName(part.getFunctionCall().getName());
                            func.setArguments(toJSONString(part.getFunctionCall().getArgs()));
                            tc.setFunction(func);
                            toolCalls.add(tc);
                        } else if (part.getFunctionResponse() != null) {
                            Message toolMsg = new Message();
                            toolMsg.setRole("tool");
                            toolMsg.setToolCallId("call_" + toolCalls.size());
                            setStringContent(toolMsg,
                                    toJSONString(part.getFunctionResponse().getResponse()));
                            messages.add(toolMsg);
                        }
                    }
                }

                if (!toolCalls.isEmpty()) {
                    setToolCalls(message, toolCalls);
                } else if (mediaContents.size() == 1 && "text".equals(mediaContents.get(0).get("type"))) {
                    setStringContent(message, (String) mediaContents.get(0).get("text"));
                } else if (!mediaContents.isEmpty()) {
                    setMediaContent(message, mediaContents);
                }

                if (!parseContent(message).isEmpty() || !parseToolCalls(message).isEmpty()) {
                    messages.add(message);
                }
            }
        }
        openaiRequest.setMessages(messages);

        // Generation config
        GeminiChatGenerationConfig genConfig = geminiRequest.getGenerationConfig();
        if (genConfig != null) {
            if (genConfig.getTemperature() != null) {
                openaiRequest.setTemperature(genConfig.getTemperature());
            }
            if (genConfig.getTopP() != null && genConfig.getTopP() > 0) {
                openaiRequest.setTopP(genConfig.getTopP());
            }
            if (genConfig.getTopK() != null && genConfig.getTopK() > 0) {
                openaiRequest.setTopK(genConfig.getTopK().intValue());
            }
            if (genConfig.getMaxOutputTokens() != null && genConfig.getMaxOutputTokens() > 0) {
                openaiRequest.setMaxTokens(genConfig.getMaxOutputTokens());
            }
            if (genConfig.getStopSequences() != null && !genConfig.getStopSequences().isEmpty()) {
                List<String> seqs = genConfig.getStopSequences();
                openaiRequest.setStop(seqs.size() > 4 ? seqs.subList(0, 4) : seqs);
            }
            if (genConfig.getCandidateCount() != null && genConfig.getCandidateCount() > 0) {
                openaiRequest.setN(genConfig.getCandidateCount());
            }
        }

        // System instructions → first message
        GeminiChatContent sysInstruction = geminiRequest.getSystemInstructions();
        if (sysInstruction != null && sysInstruction.getParts() != null) {
            String sysText = extractTextFromGeminiParts(sysInstruction.getParts());
            if (!sysText.isEmpty()) {
                Message sysMsg = new Message();
                sysMsg.setRole("system");
                setStringContent(sysMsg, sysText);
                messages.add(0, sysMsg);
                openaiRequest.setMessages(messages);
            }
        }

        // Gemini Tools → OpenAI Tools 转换（Go convert.go L763-L789）
        // gemini tools 结构：[{functionDeclarations: [{name, description, parameters}, ...]}, ...]
        if (geminiRequest.getTools() instanceof List<?> geminiTools && !geminiTools.isEmpty()) {
            List<ToolCallRequest> openAITools = new ArrayList<>();
            for (Object tool : geminiTools) {
                if (!(tool instanceof Map<?, ?> toolMap)) continue;
                Object fdRaw = toolMap.get("functionDeclarations");
                if (fdRaw == null) fdRaw = toolMap.get("function_declarations");
                if (!(fdRaw instanceof List<?> fdList) || fdList.isEmpty()) continue;
                for (Object fd : fdList) {
                    if (!(fd instanceof Map<?, ?> fdMap)) continue;
                    ToolCallRequest openAITool = new ToolCallRequest();
                    openAITool.setType("function");
                    FunctionRequest func = new FunctionRequest();
                    func.setName((String) fdMap.get("name"));
                    func.setDescription((String) fdMap.get("description"));
                    func.setParameters(fdMap.get("parameters"));
                    openAITool.setFunction(func);
                    openAITools.add(openAITool);
                }
            }
            if (!openAITools.isEmpty()) {
                openaiRequest.setTools(openAITools);
            }
        }

        return openaiRequest;
    }

    // ======================== OpenAI 流式 → Claude 流式 ========================

    /** Go StreamResponseOpenAI2Claude 的核心状态常量 */
    private static final String LAST_MSG_NONE = "none";
    private static final String LAST_MSG_TEXT = "text";
    private static final String LAST_MSG_THINKING = "thinking";
    private static final String LAST_MSG_TOOLS = "tools";

    /**
     * 将 OpenAI ChatCompletions 流式响应转换为 Claude 流式响应      */
    public static List<ClaudeResponse> streamResponseOpenAI2Claude(
            ChatCompletionsStreamResponse openAIResponse, RelayInfo info) {

        if (info.isClaudeDone()) return null;

        List<ClaudeResponse> claudeResponses = new ArrayList<>();

        // 首块消息
        if (info.getSendResponseCount() == 1) {
            claudeResponses.addAll(buildFirstChunkClaude(openAIResponse, info));
            return claudeResponses;
        }

        List<ChatCompletionsStreamResponse.Choice> choices = openAIResponse.getChoices();
        if (choices == null || choices.isEmpty()) {
            // 仅 Usage 的 SSE chunk
            Usage oaiUsage = openAIResponse.getUsage() != null
                    ? openAIResponse.getUsage() : toDtoUsage(info.getClaudeUsage());
            if (oaiUsage != null) {
                stopOpenBlocks(claudeResponses, info);
                String stopReason = stopReasonOpenAI2Claude(info.getClaudeFinishReason());
                if (stopReason == null || stopReason.isEmpty()) stopReason = "end_turn";
                ClaudeResponse msgDelta = new ClaudeResponse();
                msgDelta.setType("message_delta");
                msgDelta.setUsage(buildClaudeUsageFromOpenAIUsage(oaiUsage));
                ClaudeMediaMessage delta = new ClaudeMediaMessage();
                delta.setStopReason(stopReason);
                msgDelta.setDelta(delta);
                claudeResponses.add(msgDelta);
                ClaudeResponse msgStop = new ClaudeResponse();
                msgStop.setType("message_stop");
                claudeResponses.add(msgStop);
                info.setClaudeDone(true);
            }
            return claudeResponses;
        }

        ChatCompletionsStreamResponse.Choice chosenChoice = choices.get(0);
        boolean doneChunk = chosenChoice.getFinishReason() != null && !chosenChoice.getFinishReason().isEmpty();
        if (doneChunk) {
            info.setClaudeFinishReason(chosenChoice.getFinishReason());
            Usage oaiUsage = openAIResponse.getUsage();
            if (oaiUsage == null) {
                oaiUsage = toDtoUsage(info.getClaudeUsage());
                // 上游先发 finish_reason 再发 usage chunk，暂不关闭等 usage
                if (oaiUsage == null) return claudeResponses;
            }
        }

        ClaudeResponse claudeResponse = new ClaudeResponse();
        boolean isEmpty = false;
        claudeResponse.setType("content_block_delta");

        ChatCompletionsStreamResponse.Delta delta = chosenChoice.getDelta();
        if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
            // 工具调用分支
            handleToolCallDelta(claudeResponses, info, delta.getToolCalls());
        } else if (delta != null) {
            String reasoning = delta.getReasoningContent();
            String textContent = delta.getContent();
            if ((reasoning != null && !reasoning.isEmpty()) || (textContent != null && !textContent.isEmpty())) {
                if (reasoning != null && !reasoning.isEmpty()) {
                    needTypeSwitch(claudeResponses, info, LAST_MSG_THINKING);
                    info.setLastMessagesType(LAST_MSG_THINKING);
                    ClaudeMediaMessage cm = new ClaudeMediaMessage();
                    cm.setType("thinking_delta");
                    cm.setThinking(reasoning);
                    claudeResponse.setDelta(cm);
                } else {
                    needTypeSwitch(claudeResponses, info, LAST_MSG_TEXT);
                    info.setLastMessagesType(LAST_MSG_TEXT);
                    ClaudeMediaMessage cm = new ClaudeMediaMessage();
                    cm.setType("text_delta");
                    cm.setText(textContent);
                    claudeResponse.setDelta(cm);
                }
            } else {
                isEmpty = true;
            }
        }

        claudeResponse.setIndex(info.getClaudeIndex());
        if (!isEmpty && claudeResponse.getDelta() != null) {
            claudeResponses.add(claudeResponse);
        }

        // 完成块
        if (doneChunk || info.isClaudeDone()) {
            stopOpenBlocks(claudeResponses, info);
            Usage oaiUsage = openAIResponse.getUsage();
            if (oaiUsage == null) oaiUsage = toDtoUsage(info.getClaudeUsage());
            if (oaiUsage != null) {
                ClaudeResponse msgDelta = new ClaudeResponse();
                msgDelta.setType("message_delta");
                msgDelta.setUsage(buildClaudeUsageFromOpenAIUsage(oaiUsage));
                ClaudeMediaMessage dm = new ClaudeMediaMessage();
                dm.setStopReason(stopReasonOpenAI2Claude(info.getClaudeFinishReason()));
                msgDelta.setDelta(dm);
                claudeResponses.add(msgDelta);
            }
            ClaudeResponse msgStop = new ClaudeResponse();
            msgStop.setType("message_stop");
            claudeResponses.add(msgStop);
            info.setClaudeDone(true);
            return claudeResponses;
        }

        return claudeResponses;
    }

    /** 处理首块 Claude 响应 */
    private static List<ClaudeResponse> buildFirstChunkClaude(
            ChatCompletionsStreamResponse openAIResponse, RelayInfo info) {
        List<ClaudeResponse> claudeResponses = new ArrayList<>();

        // message_start
        ClaudeMediaMessage msg = new ClaudeMediaMessage();
        msg.setId(openAIResponse.getId());
        msg.setModel(openAIResponse.getModel());
        msg.setType("message");
        msg.setRole("assistant");
        ClaudeUsage initialUsage = new ClaudeUsage();
        initialUsage.setInputTokens(info.getEstimatePromptTokens());
        initialUsage.setOutputTokens(0);
        msg.setUsage(initialUsage);
        msg.setContent(new ArrayList<>());
        ClaudeResponse startResp = new ClaudeResponse();
        startResp.setType("message_start");
        startResp.setMessage(msg);
        claudeResponses.add(startResp);

        List<ChatCompletionsStreamResponse.Choice> choices = openAIResponse.getChoices();
        if (choices == null || choices.isEmpty()) return claudeResponses;

        ChatCompletionsStreamResponse.Choice choice = choices.get(0);
        ChatCompletionsStreamResponse.Delta delta = choice.getDelta();

        // 判断是否为工具调用
        if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
            info.setLastMessagesType(LAST_MSG_TOOLS);
            info.setToolCallBaseIndex(0);
            info.setToolCallMaxIndexOffset(0);
            Map<String, Object> firstTool = getFirstToolCall(openAIResponse);
            int idx = 0;
            ClaudeResponse cbStart = new ClaudeResponse();
            cbStart.setIndex(idx);
            cbStart.setType("content_block_start");
            ClaudeMediaMessage cb = new ClaudeMediaMessage();
            cb.setId((String) firstTool.get("id"));
            cb.setType("tool_use");
            cb.setName((String) firstTool.getOrDefault("name",
                    firstTool.get("function") != null
                            ? ((Map<String, String>) firstTool.get("function")).get("name")
                            : ""));
            cb.setInput(new HashMap<>());
            cbStart.setContentBlock(cb);
            claudeResponses.add(cbStart);
            // 首块含 tool delta → 追加 input_json_delta
            Object funcObj = firstTool.get("function");
            if (funcObj instanceof Map<?, ?> funcMap) {
                String args = (String) funcMap.get("arguments");
                if (args != null && !args.isEmpty()) {
                    ClaudeResponse deltaResp = new ClaudeResponse();
                    deltaResp.setIndex(idx);
                    deltaResp.setType("content_block_delta");
                    ClaudeMediaMessage dm = new ClaudeMediaMessage();
                    dm.setType("input_json_delta");
                    dm.setPartialJson(args);
                    deltaResp.setDelta(dm);
                    claudeResponses.add(deltaResp);
                }
            }
        } else if (delta != null) {
            String reasoning = delta.getReasoningContent();
            String content = delta.getContent();
            if (reasoning != null && !reasoning.isEmpty()) {
                needTypeSwitch(claudeResponses, info, LAST_MSG_THINKING);
                info.setLastMessagesType(LAST_MSG_THINKING);
                int idx = info.getClaudeIndex();
                ClaudeResponse cbStart = new ClaudeResponse();
                cbStart.setIndex(idx);
                cbStart.setType("content_block_start");
                ClaudeMediaMessage cb = new ClaudeMediaMessage();
                cb.setType("thinking");
                cb.setThinking("");
                cbStart.setContentBlock(cb);
                claudeResponses.add(cbStart);
                ClaudeResponse deltaResp = new ClaudeResponse();
                deltaResp.setIndex(idx);
                deltaResp.setType("content_block_delta");
                ClaudeMediaMessage dm = new ClaudeMediaMessage();
                dm.setType("thinking_delta");
                dm.setThinking(reasoning);
                deltaResp.setDelta(dm);
                claudeResponses.add(deltaResp);
            } else if (content != null && !content.isEmpty()) {
                needTypeSwitch(claudeResponses, info, LAST_MSG_TEXT);
                info.setLastMessagesType(LAST_MSG_TEXT);
                int idx = info.getClaudeIndex();
                ClaudeResponse cbStart = new ClaudeResponse();
                cbStart.setIndex(idx);
                cbStart.setType("content_block_start");
                ClaudeMediaMessage cb = new ClaudeMediaMessage();
                cb.setType("text");
                cb.setText("");
                cbStart.setContentBlock(cb);
                claudeResponses.add(cbStart);
                ClaudeResponse deltaResp = new ClaudeResponse();
                deltaResp.setIndex(idx);
                deltaResp.setType("content_block_delta");
                ClaudeMediaMessage dm = new ClaudeMediaMessage();
                dm.setType("text_delta");
                dm.setText(content);
                deltaResp.setDelta(dm);
                claudeResponses.add(deltaResp);
            }
        }

        // 首块就带 finish_reason
        if (choice.getFinishReason() != null && !choice.getFinishReason().isEmpty()) {
            info.setClaudeFinishReason(choice.getFinishReason());
            stopOpenBlocks(claudeResponses, info);
            Usage oaiUsage = openAIResponse.getUsage();
            if (oaiUsage == null) oaiUsage = toDtoUsage(info.getClaudeUsage());
            if (oaiUsage != null) {
                ClaudeResponse msgDelta = new ClaudeResponse();
                msgDelta.setType("message_delta");
                msgDelta.setUsage(buildClaudeUsageFromOpenAIUsage(oaiUsage));
                ClaudeMediaMessage dm = new ClaudeMediaMessage();
                dm.setStopReason(stopReasonOpenAI2Claude(info.getClaudeFinishReason()));
                msgDelta.setDelta(dm);
                claudeResponses.add(msgDelta);
            }
            ClaudeResponse msgStop = new ClaudeResponse();
            msgStop.setType("message_stop");
            claudeResponses.add(msgStop);
            info.setClaudeDone(true);
        }

        return claudeResponses;
    }

    /** 获取首个工具调用 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getFirstToolCall(ChatCompletionsStreamResponse resp) {
        if (resp.getChoices() == null || resp.getChoices().isEmpty()) return Map.of();
        List<Object> toolCalls = resp.getChoices().get(0).getDelta().getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            Object tc = toolCalls.get(0);
            if (tc instanceof Map) return (Map<String, Object>) tc;
        }
        return Map.of();
    }

    /** 处理工具调用的 delta */
    @SuppressWarnings("unchecked")
    private static void handleToolCallDelta(List<ClaudeResponse> claudeResponses,
                                            RelayInfo info, List<Object> toolCalls) {
        if (!LAST_MSG_TOOLS.equals(info.getLastMessagesType())) {
            stopOpenBlocksAndAdvance(info, claudeResponses);
            info.setToolCallBaseIndex(info.getClaudeIndex());
            info.setToolCallMaxIndexOffset(0);
        }
        info.setLastMessagesType(LAST_MSG_TOOLS);
        int base = info.getToolCallBaseIndex();
        int maxOffset = info.getToolCallMaxIndexOffset();

        for (int i = 0; i < toolCalls.size(); i++) {
            Map<String, Object> tc = (Map<String, Object>) toolCalls.get(i);
            int offset;
            Object idxObj = tc.get("index");
            if (idxObj instanceof Number n) offset = n.intValue();
            else offset = i;
            if (offset > maxOffset) maxOffset = offset;
            int blockIndex = base + offset;

            // function name 出现 → content_block_start
            Object funcObj = tc.get("function");
            if (funcObj instanceof Map<?, ?> func) {
                String funcName = (String) func.get("name");
                if (funcName != null && !funcName.isEmpty()) {
                    ClaudeResponse cbStart = new ClaudeResponse();
                    cbStart.setIndex(blockIndex);
                    cbStart.setType("content_block_start");
                    ClaudeMediaMessage cb = new ClaudeMediaMessage();
                    cb.setId((String) tc.get("id"));
                    cb.setType("tool_use");
                    cb.setName(funcName);
                    cb.setInput(new HashMap<>());
                    cbStart.setContentBlock(cb);
                    claudeResponses.add(cbStart);
                }

                // arguments → input_json_delta
                String args = (String) func.get("arguments");
                if (args != null && !args.isEmpty()) {
                    ClaudeResponse deltaResp = new ClaudeResponse();
                    deltaResp.setIndex(blockIndex);
                    deltaResp.setType("content_block_delta");
                    ClaudeMediaMessage dm = new ClaudeMediaMessage();
                    dm.setType("input_json_delta");
                    dm.setPartialJson(args);
                    deltaResp.setDelta(dm);
                    claudeResponses.add(deltaResp);
                }
            }
        }
        info.setToolCallMaxIndexOffset(maxOffset);
        info.setClaudeIndex(base + maxOffset);
    }

    /** 关闭当前打开的内容块 */
    private static void stopOpenBlocks(List<ClaudeResponse> responses, RelayInfo info) {
        String lastType = info.getLastMessagesType();
        switch (lastType) {
            case LAST_MSG_TEXT, LAST_MSG_THINKING ->
                    responses.add(generateStopBlock(info.getClaudeIndex()));
            case LAST_MSG_TOOLS -> {
                int base = info.getToolCallBaseIndex();
                for (int offset = 0; offset <= info.getToolCallMaxIndexOffset(); offset++) {
                    responses.add(generateStopBlock(base + offset));
                }
            }
        }
    }

    /** 关闭并推进索引 */
    private static void stopOpenBlocksAndAdvance(RelayInfo info, List<ClaudeResponse> responses) {
        if (LAST_MSG_NONE.equals(info.getLastMessagesType())) return;
        stopOpenBlocks(responses, info);
        switch (info.getLastMessagesType()) {
            case LAST_MSG_TOOLS -> {
                info.setClaudeIndex(info.getToolCallBaseIndex() + info.getToolCallMaxIndexOffset() + 1);
                info.setToolCallBaseIndex(0);
                info.setToolCallMaxIndexOffset(0);
            }
            default -> info.setClaudeIndex(info.getClaudeIndex() + 1);
        }
        info.setLastMessagesType(LAST_MSG_NONE);
    }

    /** 切换到新类型时推进 */
    private static void needTypeSwitch(List<ClaudeResponse> responses, RelayInfo info, String newType) {
        if (!newType.equals(info.getLastMessagesType())) {
            stopOpenBlocksAndAdvance(info, responses);
        }
    }

    // ======================== OpenAI 非流式 → Claude 非流式 ========================

    /** 从 OpenAIResponseDTO.Message 提取字符串内容 */
    private static String getRespStringContent(OpenAIResponseDTO.Message msg) {
        if (msg == null) return "";
        Object c = msg.getContent();
        if (c instanceof String s) return s;
        if (c instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object type = m.get("type");
                    if ("text".equals(type) || "input_text".equals(type)) {
                        Object text = m.get("text");
                        if (text != null) sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** 从 OpenAIResponseDTO.Usage 构建 ClaudeUsage（适配内部 Usage 类型） */
    private static ClaudeUsage buildClaudeUsageFromResponseUsage(OpenAIResponseDTO.Usage oaiUsage) {
        // OpenAIResponseDTO.Usage 使用 @JsonProperty 字段，通过 Convert 桥接到内部 Usage
        if (oaiUsage == null) return null;
        Usage u = Convert.toJavaBean(oaiUsage, Usage.class);
        return buildClaudeUsageFromOpenAIUsage(u);
    }

    /**
     * 将 OpenAI 文本响应转换为 Claude 响应      */
    public static ClaudeResponse responseOpenAI2Claude(OpenAITextResponse openAIResponse, RelayInfo info) {
        ClaudeResponse claudeResponse = new ClaudeResponse();
        claudeResponse.setId(openAIResponse.getId());
        claudeResponse.setType("message");
        claudeResponse.setRole("assistant");
        claudeResponse.setModel(openAIResponse.getModel());

        List<ClaudeMediaMessage> contents = new ArrayList<>();
        String stopReason = null;

        List<OpenAITextResponseChoice> choices = openAIResponse.getChoices();
        if (choices != null) {
            for (OpenAITextResponseChoice choice : choices) {
                stopReason = stopReasonOpenAI2Claude(choice.getFinishReason());
                if ("tool_calls".equals(choice.getFinishReason())) {
                    List<OpenAIResponseDTO.ToolCallResponse> tcs = choice.getMessage() != null
                            ? choice.getMessage().getToolCalls() : null;
                    if (tcs != null) {
                        for (OpenAIResponseDTO.ToolCallResponse tc : tcs) {
                            ClaudeMediaMessage cm = new ClaudeMediaMessage();
                            cm.setType("tool_use");
                            cm.setId(tc.getId());
                            cm.setName(tc.getFunction().getName());
                            String args = tc.getFunction().getArguments();
                            Map<?, ?> mapParams = Convert.toJSONObject(args);
                            cm.setInput(mapParams != null ? mapParams : args);
                            contents.add(cm);
                        }
                    }
                } else if (choice.getMessage() != null) {
                    ClaudeMediaMessage cm = new ClaudeMediaMessage();
                    cm.setType("text");
                    cm.setText(getRespStringContent(choice.getMessage()));
                    contents.add(cm);
                }
            }
        }

        claudeResponse.setContent(contents);
        claudeResponse.setStopReason(stopReason);
        claudeResponse.setUsage(buildClaudeUsageFromResponseUsage(openAIResponse.getUsage()));

        return claudeResponse;
    }

    // ======================== OpenAI → Gemini 转换 ========================

    /**
     * 将 OpenAI 文本响应转换为 Gemini 响应      */
    public static GeminiChatResponse responseOpenAI2Gemini(OpenAITextResponse openAIResponse, RelayInfo info) {
        GeminiChatResponse geminiResponse = new GeminiChatResponse();
        List<GeminiChatCandidate> candidates = new ArrayList<>();

        for (OpenAITextResponseChoice choice : openAIResponse.getChoices()) {
            GeminiChatCandidate candidate = new GeminiChatCandidate();
            candidate.setIndex((long) choice.getIndex());
            candidate.setSafetyRatings(new ArrayList<>());

            String finishReason = switch (choice.getFinishReason()) {
                case "stop" -> "STOP";
                case "length" -> "MAX_TOKENS";
                case "content_filter" -> "SAFETY";
                case "tool_calls" -> "STOP";
                default -> "STOP";
            };
            candidate.setFinishReason(finishReason);

            GeminiChatContent content = new GeminiChatContent();
            content.setRole("model");
            List<GeminiPart> parts = new ArrayList<>();

            List<OpenAIResponseDTO.ToolCallResponse> tcs = choice.getMessage() != null
                    ? choice.getMessage().getToolCalls() : null;
            if (tcs != null && !tcs.isEmpty()) {
                for (OpenAIResponseDTO.ToolCallResponse tc : tcs) {
                    GeminiPart part = new GeminiPart();
                    FunctionCall funcCall = new FunctionCall();
                    funcCall.setName(tc.getFunction().getName());
                    String argsStr = tc.getFunction().getArguments();
                    Object args = Convert.toJSONObject(argsStr);
                    funcCall.setArgs(args != null ? args : argsStr);
                    part.setFunctionCall(funcCall);
                    parts.add(part);
                }
            } else {
                String textContent = getRespStringContent(choice.getMessage());
                if (!textContent.isEmpty()) {
                    GeminiPart part = new GeminiPart();
                    part.setText(textContent);
                    parts.add(part);
                }
            }

            content.setParts(parts);
            candidate.setContent(content);
            candidates.add(candidate);
        }

        geminiResponse.setCandidates(candidates);
        GeminiUsageMetadata usageMeta = new GeminiUsageMetadata();
        if (openAIResponse.getUsage() != null) {
            Usage u = Convert.toJavaBean(openAIResponse.getUsage(), Usage.class);
            usageMeta.setPromptTokenCount(u.getPromptTokens());
            usageMeta.setCandidatesTokenCount(u.getCompletionTokens());
            usageMeta.setTotalTokenCount(u.getTotalTokens());
        }
        geminiResponse.setUsageMetadata(usageMeta);

        return geminiResponse;
    }

    /**
     * 将 OpenAI 流式响应转换为 Gemini 流式响应      */
    public static GeminiChatResponse streamResponseOpenAI2Gemini(
            ChatCompletionsStreamResponse openAIResponse, RelayInfo info) {

        boolean hasContent = false;
        boolean hasFinishReason = false;
        List<ChatCompletionsStreamResponse.Choice> choices = openAIResponse.getChoices();
        if (choices != null) {
            for (ChatCompletionsStreamResponse.Choice choice : choices) {
                ChatCompletionsStreamResponse.Delta delta = choice.getDelta();
                if (delta != null) {
                    if ((delta.getContent() != null && !delta.getContent().isEmpty())
                            || (delta.getToolCalls() != null && !delta.getToolCalls().isEmpty())) {
                        hasContent = true;
                    }
                }
                if (choice.getFinishReason() != null) hasFinishReason = true;
            }
        }

        if (!hasContent && !hasFinishReason) return null;

        GeminiChatResponse geminiResponse = new GeminiChatResponse();
        List<GeminiChatCandidate> candidates = new ArrayList<>();

        for (ChatCompletionsStreamResponse.Choice choice : choices) {
            GeminiChatCandidate candidate = new GeminiChatCandidate();
            candidate.setIndex((long) choice.getIndex());
            candidate.setSafetyRatings(new ArrayList<>());

            if (choice.getFinishReason() != null) {
                String finishReason = switch (choice.getFinishReason()) {
                    case "stop" -> "STOP";
                    case "length" -> "MAX_TOKENS";
                    case "content_filter" -> "SAFETY";
                    case "tool_calls" -> "STOP";
                    default -> "STOP";
                };
                candidate.setFinishReason(finishReason);
            }

            GeminiChatContent content = new GeminiChatContent();
            content.setRole("model");
            List<GeminiPart> parts = new ArrayList<>();

            ChatCompletionsStreamResponse.Delta delta = choice.getDelta();
            if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
                for (Object tcObj : delta.getToolCalls()) {
                    Map<String, Object> tc = Convert.toJSONObject(tcObj);
                    GeminiPart part = new GeminiPart();
                    FunctionCall funcCall = new FunctionCall();
                    Object funcObj = tc.get("function");
                    if (funcObj instanceof Map<?, ?> func) {
                        funcCall.setName((String) func.get("name"));
                        String argsStr = (String) func.get("arguments");
                        Object args = Convert.toJSONObject(argsStr);
                        funcCall.setArgs(args != null ? args : argsStr);
                    }
                    part.setFunctionCall(funcCall);
                    parts.add(part);
                }
            } else if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty()) {
                GeminiPart part = new GeminiPart();
                part.setText(delta.getContent());
                parts.add(part);
            }

            content.setParts(parts);
            candidate.setContent(content);
            candidates.add(candidate);
        }

        geminiResponse.setCandidates(candidates);
        GeminiUsageMetadata usageMeta = new GeminiUsageMetadata();
        usageMeta.setPromptTokenCount(info.getEstimatePromptTokens());
        usageMeta.setCandidatesTokenCount(0);
        usageMeta.setTotalTokenCount(info.getEstimatePromptTokens());
        if (openAIResponse.getUsage() != null) {
            usageMeta.setPromptTokenCount(openAIResponse.getUsage().getPromptTokens());
            usageMeta.setCandidatesTokenCount(openAIResponse.getUsage().getCompletionTokens());
            usageMeta.setTotalTokenCount(openAIResponse.getUsage().getTotalTokens());
        }
        geminiResponse.setUsageMetadata(usageMeta);

        return geminiResponse;
    }
}
