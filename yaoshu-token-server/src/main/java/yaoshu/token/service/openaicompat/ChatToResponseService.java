package yaoshu.token.service.openaicompat;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest.Message;

import java.util.*;

/**
 * Chat → Response 转换服务  */
@Slf4j
public final class ChatToResponseService {    private ChatToResponseService() {
    }

    // ======================== 类型常量 ========================

    private static final String TYPE_INPUT_TEXT = "input_text";
    private static final String TYPE_OUTPUT_TEXT = "output_text";
    private static final String TYPE_INPUT_IMAGE = "input_image";
    private static final String TYPE_INPUT_AUDIO = "input_audio";
    private static final String TYPE_INPUT_FILE = "input_file";
    private static final String TYPE_INPUT_VIDEO = "input_video";
    private static final String TYPE_FUNCTION_CALL = "function_call";
    private static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";

    /**
     * 将 Chat Completions 请求转换为 Responses API 请求      */
    @SuppressWarnings("unchecked")
    public static OpenAIResponsesRequest chatCompletionsRequestToResponsesRequest(GeneralOpenAIRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request is nil");
        }
        if (req.getModel() == null || req.getModel().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }
        if (req.getN() != null && req.getN() > 1) {
            throw new IllegalArgumentException("n>1 is not supported in responses compatibility mode");
        }

        // 累积 instructions（system/developer 消息合并）
        List<String> instructionsParts = new ArrayList<>();
        // 累积 input items
        List<Map<String, Object>> inputItems = new ArrayList<>();

        if (req.getMessages() != null) {
            for (Message msg : req.getMessages()) {
                String role = trimToEmpty(msg.getRole());
                if (role.isEmpty()) continue;

                // ---- tool / function 消息 → function_call_output ----
                if ("tool".equals(role) || "function".equals(role)) {
                    String callId = trimToEmpty(msg.getToolCallId());
                    Object output = normalizeContentValue(msg.getContent());

                    if (callId.isEmpty()) {
                        // 无 call_id 的回退为 user 消息
                        inputItems.add(Map.of("role", "user", "content",
                                "[tool_output_missing_call_id] " + (output != null ? output : "")));
                        continue;
                    }
                    inputItems.add(Map.of("type", TYPE_FUNCTION_CALL_OUTPUT,
                            "call_id", callId, "output", output != null ? output : ""));
                    continue;
                }

                // ---- system / developer → instructions ----
                if ("system".equals(role) || "developer".equals(role)) {
                    String text = extractTextContent(msg.getContent());
                    if (text != null && !text.isBlank()) {
                        instructionsParts.add(text.trim());
                    }
                    continue;
                }

                // ---- user / assistant → input items ----
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", role);

                if (msg.getContent() == null || isBlankContent(msg.getContent())) {
                    item.put("content", "");
                    inputItems.add(item);
                    // assistant with tool_calls
                    if ("assistant".equals(role)) {
                        appendToolCallsAsItems(inputItems, msg.getToolCalls());
                    }
                    continue;
                }

                if (msg.getContent() instanceof String s) {
                    item.put("content", s);
                    inputItems.add(item);
                    if ("assistant".equals(role)) {
                        appendToolCallsAsItems(inputItems, msg.getToolCalls());
                    }
                    continue;
                }

                // 多 part content parsing
                if (msg.getContent() instanceof List<?> parts) {
                    List<Map<String, Object>> contentParts = new ArrayList<>();
                    for (Object part : parts) {
                        if (!(part instanceof Map)) continue;
                        Map<String, Object> p = (Map<String, Object>) part;
                        String type = obj2Str(p.get("type"), "");
                        switch (type) {
                            case "text" -> {
                                String textType = "assistant".equals(role) ? TYPE_OUTPUT_TEXT : TYPE_INPUT_TEXT;
                                contentParts.add(Map.of("type", textType, "text", obj2Str(p.get("text"), "")));
                            }
                            case "image_url" -> contentParts.add(Map.of("type", TYPE_INPUT_IMAGE,
                                    "image_url", normalizeChatImageURL(p.get("image_url"))));
                            case "input_audio" -> contentParts.add(Map.of("type", TYPE_INPUT_AUDIO,
                                    "input_audio", p.getOrDefault("input_audio", "")));
                            case "file" -> contentParts.add(Map.of("type", TYPE_INPUT_FILE,
                                    "file", p.getOrDefault("file", "")));
                            case "video_url" -> contentParts.add(Map.of("type", TYPE_INPUT_VIDEO,
                                    "video_url", p.getOrDefault("video_url", "")));
                            default -> contentParts.add(Map.of("type", type));
                        }
                    }
                    item.put("content", contentParts);
                    inputItems.add(item);

                    if ("assistant".equals(role)) {
                        appendToolCallsAsItems(inputItems, msg.getToolCalls());
                    }
                }
            }
        }

        String inputRaw = toJson(inputItems);
        String instructionsRaw = instructionsParts.isEmpty() ? null
                : "\"" + String.join("\\n\\n", instructionsParts).replace("\"", "\\\"") + "\"";

        // 工具转换
        String toolsRaw = convertToolsToJson(req.getTools());
        String toolChoiceRaw = convertToolChoice(req.getToolChoice());
        String parallelRaw = req.getParallelToolCalls() != null
                ? String.valueOf(req.getParallelToolCalls()) : null;
        String textRaw = convertResponseFormatToText(req.getResponseFormat());

        // max_output_tokens
        int maxOut = 0;
        if (req.getMaxTokens() != null && req.getMaxTokens() > 0) maxOut = req.getMaxTokens();
        if (req.getMaxCompletionTokens() != null && req.getMaxCompletionTokens() > maxOut) {
            maxOut = req.getMaxCompletionTokens();
        }

        Double topP = req.getTopP();

        // 构建请求
        OpenAIResponsesRequest out = new OpenAIResponsesRequest();
        out.setModel(req.getModel());
        out.setInput(inputRaw);
        out.setInstructions(instructionsRaw);
        out.setStream(req.getStream());
        out.setTemperature(req.getTemperature());
        out.setText(textRaw);
        out.setToolChoice(toolChoiceRaw);
        out.setTools(toolsRaw);
        out.setTopP(topP);
        out.setUser(req.getUser());
        out.setParallelToolCalls(parallelRaw);
        out.setStore(req.getStore());
        out.setMetadata(req.getMetadata() != null ? req.getMetadata() : null);
        if (maxOut > 0) out.setMaxOutputTokens(maxOut);
        if (req.getReasoningEffort() != null && !req.getReasoningEffort().isEmpty()) {
            OpenAIResponsesRequest.Reasoning reasoning = new OpenAIResponsesRequest.Reasoning();
            reasoning.setEffort(req.getReasoningEffort());
            reasoning.setSummary("detailed");
            out.setReasoning(reasoning);
        }
        return out;
    }

    // ======================== 工具方法 ========================

    @SuppressWarnings("unchecked")
    private static void appendToolCallsAsItems(List<Map<String, Object>> inputItems, List<Object> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return;
        for (Object tcObj : toolCalls) {
            if (!(tcObj instanceof Map tc)) continue;
            String id = trimToEmpty(obj2Str(tc.get("id"), ""));
            if (id.isEmpty()) continue;
            String type = obj2Str(tc.get("type"), "");
            if (!type.isEmpty() && !"function".equals(type)) continue;

            Object fnObj = tc.get("function");
            if (!(fnObj instanceof Map fn)) continue;
            String name = trimToEmpty(obj2Str(fn.get("name"), ""));
            if (name.isEmpty()) continue;

            inputItems.add(Map.of(
                    "type", TYPE_FUNCTION_CALL,
                    "call_id", id,
                    "name", name,
                    "arguments", fn.getOrDefault("arguments", "{}")
            ));
        }
    }

    /**
     * 提取消息的纯文本内容
     */
    @SuppressWarnings("unchecked")
    private static String extractTextContent(Object content) {
        if (content == null) return null;
        if (content instanceof String s) return s;
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map p && "text".equals(p.get("type"))) {
                    String text = obj2Str(p.get("text"), "");
                    if (!text.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        return null;
    }

    /**
     * 将 content 规范化为 JSON 可序列化的值
     */
    @SuppressWarnings("unchecked")
    private static Object normalizeContentValue(Object content) {
        if (content == null) return "";
        if (content instanceof String) return content;
        if (content instanceof List && !((List<?>) content).isEmpty()) {
            try {
                return Convert.toJSONString(content);
            } catch (Exception e) {
                return content.toString();
            }
        }
        return content.toString();
    }

    private static boolean isBlankContent(Object content) {
        if (content == null) return true;
        if (content instanceof String s) return s.isEmpty();
        if (content instanceof List) return ((List<?>) content).isEmpty();
        return false;
    }

    /**
     * 转换 tools 列表为 JSON      */
    @SuppressWarnings("unchecked")
    private static String convertToolsToJson(List<Object> tools) {
        if (tools == null || tools.isEmpty()) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof Map m) {
                String type = obj2Str(m.get("type"), "");
                if ("function".equals(type)) {
                    Map<String, Object> fn = (Map<String, Object>) m.get("function");
                    result.add(Map.of("type", "function",
                            "name", fn != null ? fn.getOrDefault("name", "") : "",
                            "description", fn != null ? fn.getOrDefault("description", "") : "",
                            "parameters", fn != null ? fn.getOrDefault("parameters", Map.of()) : Map.of()));
                } else {
                    result.add(new LinkedHashMap<>(m));
                }
            }
        }
        return toJson(result);
    }

    /**
     * 转换 tool_choice      */
    @SuppressWarnings("unchecked")
    private static String convertToolChoice(Object toolChoice) {
        if (toolChoice == null) return null;
        if (toolChoice instanceof String s) return "\"" + s + "\"";
        if (toolChoice instanceof Map m) {
            String type = obj2Str(m.get("type"), "");
            if ("function".equals(type)) {
                if (m.get("name") instanceof String n && !n.isEmpty()) {
                    return toJson(Map.of("type", "function", "name", n));
                }
                if (m.get("function") instanceof Map fn) {
                    if (fn.get("name") instanceof String n && !n.isEmpty()) {
                        return toJson(Map.of("type", "function", "name", n));
                    }
                }
            }
            return toJson(m);
        }
        return null;
    }

    /**
     * 转换 response_format → text.format      */
    @SuppressWarnings("unchecked")
    private static String convertResponseFormatToText(Object responseFormat) {
        if (responseFormat == null) return null;

        if (responseFormat instanceof Map fmt) {
            String type = obj2Str(fmt.get("type"), "");
            if ("json_schema".equals(type)) {
                Map<String, Object> format = new LinkedHashMap<>();
                format.put("type", type);
                Object jsonSchema = fmt.get("json_schema");
                if (jsonSchema instanceof Map js) {
                    for (Map.Entry<String, Object> e : ((Map<String, Object>) js).entrySet()) {
                        if (!"type".equals(e.getKey())) {
                            format.put(e.getKey(), e.getValue());
                        }
                    }
                } else if (jsonSchema != null) {
                    format.put("json_schema", jsonSchema);
                }
                return toJson(Map.of("format", format));
            }
            if (type != null && !type.isEmpty()) {
                return toJson(Map.of("format", Map.of("type", type)));
            }
        }
        if (responseFormat instanceof String s && !s.isEmpty()) {
            return toJson(Map.of("format", Map.of("type", s)));
        }
        return null;
    }

    /**
     * 规范化图片 URL：Go normalizeChatImageURLToString
     */
    @SuppressWarnings("unchecked")
    private static Object normalizeChatImageURL(Object imageUrl) {
        if (imageUrl == null) return "";
        if (imageUrl instanceof String s) {
            if (s.startsWith("http")) return s;
            // 可能是 data: URL，直接返回
            return s;
        }
        if (imageUrl instanceof Map m) {
            Object url = m.get("url");
            if (url instanceof String s && !s.isEmpty()) return s;
        }
        return imageUrl;
    }

    // ======================== 基础工具 ========================

    private static String toJson(Object obj) {
        try {
            return Convert.toJSONString(obj);
        } catch (Exception e) {
            log.error("JSON marshal failed: {}", e.getMessage());
            return null;
        }
    }

    private static String obj2Str(Object obj, String defaultVal) {
        return obj instanceof String s ? s : defaultVal;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
