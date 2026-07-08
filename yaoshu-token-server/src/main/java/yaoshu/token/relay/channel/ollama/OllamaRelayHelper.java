package yaoshu.token.relay.channel.ollama;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.CachedFileData;
import yaoshu.token.pojo.dto.EmbeddingDTO;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest.Message;
import yaoshu.token.pojo.dto.OpenAIResponseDTO;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAIEmbeddingResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAIEmbeddingResponseItem;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.DownloadService;
import yaoshu.token.service.HttpProxyService;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.*;

/**
 * Ollama 请求转换与响应处理  * <p>
 * 提供 OpenAI→Ollama Chat/Generate/Embedding 请求转换 + Embedding 响应处理。
 */
@Slf4j
public final class OllamaRelayHelper {

    private OllamaRelayHelper() {
    }

    // ======================== Chat 模式 ========================

    /**
     * OpenAI Chat → Ollama Chat 请求转换      */
    @SuppressWarnings("unchecked")
    public static OllamaDTO.OllamaChatRequest openAIChatToOllamaChat(GeneralOpenAIRequest r) {
        OllamaDTO.OllamaChatRequest req = new OllamaDTO.OllamaChatRequest()
                .setModel(r.getModel())
                .setStream(Boolean.TRUE.equals(r.getStream()))
                .setOptions(new LinkedHashMap<>())
                .setThink(r.getThink());

        // Response format 映射（json / json_schema）
        if (r.getResponseFormat() != null) {
            if (r.getResponseFormat() instanceof Map) {
                Map<String, Object> rf = (Map<String, Object>) r.getResponseFormat();
                if ("json".equals(rf.get("type"))) {
                    req.setFormat("json");
                } else if ("json_schema".equals(rf.get("type"))) {
                    // Go: json.Unmarshal(r.ResponseFormat.JsonSchema, &schema) → format = schema
                    Object jsonSchema = rf.get("json_schema");
                    if (jsonSchema != null) {
                        try {
                            req.setFormat(Convert.toJSONObject(jsonSchema));
                        } catch (Exception e) {
                            log.warn("Failed to parse json_schema for Ollama: {}", e.getMessage());
                            req.setFormat("json");
                        }
                    }
                }
            }
        }

        // Options 映射
        Map<String, Object> opts = req.getOptions();
        if (r.getTemperature() != null) opts.put("temperature", r.getTemperature());
        if (r.getTopP() != null) opts.put("top_p", r.getTopP());
        if (r.getTopK() != null) opts.put("top_k", r.getTopK());
        if (r.getFrequencyPenalty() != null) opts.put("frequency_penalty", r.getFrequencyPenalty());
        if (r.getPresencePenalty() != null) opts.put("presence_penalty", r.getPresencePenalty());
        if (r.getSeed() != null) opts.put("seed", r.getSeed().intValue());
        if (r.getMaxTokens() != null && r.getMaxTokens() > 0) opts.put("num_predict", r.getMaxTokens());

        // Stop 参数
        if (r.getStop() != null) {
            if (r.getStop() instanceof String s) {
                opts.put("stop", List.of(s));
            } else if (r.getStop() instanceof List) {
                opts.put("stop", r.getStop());
            }
        }

        // Tools 转换
        if (r.getTools() != null && !r.getTools().isEmpty()) {
            List<OllamaDTO.OllamaTool> tools = new ArrayList<>();
            for (Object t : r.getTools()) {
                if (t instanceof Map) {
                    Map<String, Object> tm = (Map<String, Object>) t;
                    OllamaDTO.OllamaTool tool = new OllamaDTO.OllamaTool();
                    tool.setType("function");
                    Map<String, Object> funcMap = (Map<String, Object>) tm.get("function");
                    if (funcMap != null) {
                        OllamaDTO.OllamaToolFunction func = new OllamaDTO.OllamaToolFunction();
                        func.setName((String) funcMap.get("name"));
                        func.setDescription((String) funcMap.get("description"));
                        func.setParameters(funcMap.get("parameters"));
                        tool.setFunction(func);
                    }
                    tools.add(tool);
                }
            }
            req.setTools(tools);
        }

        // Messages 转换（含 images、tool_calls、tool_name）
        List<OllamaDTO.OllamaChatMessage> messages = new ArrayList<>();
        if (r.getMessages() != null) {
            List<Message> rawMessages = r.getMessages();
            for (Message m : rawMessages) {
                String role = m.getRole() != null ? m.getRole() : "user";

                // 提取文本内容 + 图片
                StringBuilder textBuilder = new StringBuilder();
                List<String> images = new ArrayList<>();

                Object content = m.getContent();
                if (content instanceof String s) {
                    textBuilder.append(s);
                } else if (content instanceof List) {
                    for (Object part : (List<?>) content) {
                        if (part instanceof Map) {
                            Map<?, ?> p = (Map<?, ?>) part;
                            if ("image_url".equals(p.get("type"))) {
                                // 图片 URL → base64 提取
                                Object imageUrl = p.get("image_url");
                                if (imageUrl instanceof Map) {
                                    Map<?, ?> imgMap = (Map<?, ?>) imageUrl;
                                    String url = (String) imgMap.get("url");
                                    if (url != null) {
                                        // 尝试解析 data URI 或下载图片
                                        String base64 = extractImageBase64(url);
                                        if (base64 != null && !base64.isEmpty()) {
                                            images.add(base64);
                                        }
                                    }
                                }
                            } else if ("text".equals(p.get("type")) && p.get("text") instanceof String t) {
                                textBuilder.append(t);
                            }
                        }
                    }
                }

                OllamaDTO.OllamaChatMessage msg = new OllamaDTO.OllamaChatMessage()
                        .setRole(role)
                        .setContent(textBuilder.toString());

                if (!images.isEmpty()) {
                    msg.setImages(images);
                }

                // tool_name（仅 tool 角色）
                if ("tool".equals(role) && m.getName() != null) {
                    msg.setToolName(m.getName());
                }

                // ToolCalls（assistant 角色的工具调用）
                if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                    List<OllamaDTO.OllamaToolCall> calls = new ArrayList<>();
                    for (Object tc : m.getToolCalls()) {
                        if (tc instanceof Map) {
                            Map<?, ?> tcm = (Map<?, ?>) tc;
                            Map<?, ?> funcMap = (Map<?, ?>) tcm.get("function");
                            if (funcMap != null) {
                                String funcName = (String) funcMap.get("name");
                                Object argsObj = funcMap.get("arguments");
                                Object args;
                                if (argsObj instanceof String argsStr) {
                                    try {
                                        args = Convert.toJSONObject(argsStr);
                                    } catch (Exception e) {
                                        args = Map.of();
                                    }
                                } else {
                                    args = argsObj != null ? argsObj : Map.of();
                                }

                                OllamaDTO.OllamaToolCall call = new OllamaDTO.OllamaToolCall();
                                OllamaDTO.OllamaToolCall.FunctionPart funcPart = new OllamaDTO.OllamaToolCall.FunctionPart();
                                funcPart.setName(funcName);
                                funcPart.setArguments(args);
                                call.setFunction(funcPart);
                                calls.add(call);
                            }
                        }
                    }
                    msg.setToolCalls(calls);
                }

                messages.add(msg);
            }
        }
        req.setMessages(messages);
        return req;
    }

    // ======================== Generate 模式（Completions） ========================

    /**
     * OpenAI Completions → Ollama Generate 请求转换      * <p>
     * Go 的 Completions 端点使用 Prompt/Suffix 字段；若未提供 prompt，则回退到 messages 文本拼接。
     */
    @SuppressWarnings("unchecked")
    public static OllamaDTO.OllamaGenerateRequest openAIToGenerate(GeneralOpenAIRequest r) {
        OllamaDTO.OllamaGenerateRequest gen = new OllamaDTO.OllamaGenerateRequest()
                .setModel(r.getModel())
                .setStream(Boolean.TRUE.equals(r.getStream()))
                .setOptions(new LinkedHashMap<>())
                .setThink(r.getThink());

        // Prompt: 优先使用 Completions prompt，缺失时回退到 messages 文本
        Object prompt = r.getPrompt();
        if (prompt instanceof String s) {
            gen.setPrompt(s);
        } else if (prompt instanceof List<?> parts) {
            StringBuilder promptBuilder = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof String text) {
                    promptBuilder.append(text);
                }
            }
            gen.setPrompt(promptBuilder.toString());
        } else if (prompt != null) {
            gen.setPrompt(String.valueOf(prompt));
        }
        if ((gen.getPrompt() == null || gen.getPrompt().isEmpty()) && r.getMessages() != null) {
            StringBuilder promptBuilder = new StringBuilder();
            for (Message m : r.getMessages()) {
                if (m.getContent() instanceof String s) {
                    promptBuilder.append(s);
                }
            }
            gen.setPrompt(promptBuilder.toString());
        }
        if (r.getSuffix() instanceof String suffix) {
            gen.setSuffix(suffix);
        }

        // Response format 映射
        if (r.getResponseFormat() != null) {
            if (r.getResponseFormat() instanceof Map) {
                Map<String, Object> rf = (Map<String, Object>) r.getResponseFormat();
                if ("json".equals(rf.get("type"))) {
                    gen.setFormat("json");
                } else if ("json_schema".equals(rf.get("type"))) {
                    Object jsonSchema = rf.get("json_schema");
                    if (jsonSchema != null) {
                        try {
                            gen.setFormat(Convert.toJSONObject(jsonSchema));
                        } catch (Exception e) {
                            log.warn("Failed to parse json_schema for Ollama generate: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        // Options 映射
        Map<String, Object> opts = gen.getOptions();
        if (r.getTemperature() != null) opts.put("temperature", r.getTemperature());
        if (r.getTopP() != null) opts.put("top_p", r.getTopP());
        if (r.getTopK() != null) opts.put("top_k", r.getTopK());
        if (r.getFrequencyPenalty() != null) opts.put("frequency_penalty", r.getFrequencyPenalty());
        if (r.getPresencePenalty() != null) opts.put("presence_penalty", r.getPresencePenalty());
        if (r.getSeed() != null) opts.put("seed", r.getSeed().intValue());
        if (r.getMaxTokens() != null && r.getMaxTokens() > 0) opts.put("num_predict", r.getMaxTokens());

        // Stop 参数
        if (r.getStop() != null) {
            if (r.getStop() instanceof String s) {
                opts.put("stop", List.of(s));
            } else if (r.getStop() instanceof List) {
                opts.put("stop", r.getStop());
            }
        }

        return gen;
    }

    // ======================== Embedding 模式 ========================

    /**
     * OpenAI Embedding → Ollama Embedding 请求转换      */
    @SuppressWarnings("unchecked")
    public static OllamaDTO.OllamaEmbeddingRequest requestOpenAI2Embeddings(EmbeddingDTO r) {
        Map<String, Object> opts = new LinkedHashMap<>();
        if (r.getTemperature() != null) opts.put("temperature", r.getTemperature());
        if (r.getTopP() != null) opts.put("top_p", r.getTopP());
        if (r.getFrequencyPenalty() != null) opts.put("frequency_penalty", r.getFrequencyPenalty());
        if (r.getPresencePenalty() != null) opts.put("presence_penalty", r.getPresencePenalty());
        if (r.getSeed() != null) opts.put("seed", r.getSeed().intValue());

        int dimensions = r.getDimensions() != null ? r.getDimensions() : 0;
        if (r.getDimensions() != null) {
            opts.put("dimensions", dimensions);
        }

        // Go 风格：input 可以是单个字符串或数组
        Object input = r.getInput();
        return new OllamaDTO.OllamaEmbeddingRequest()
                .setModel(r.getModel())
                .setInput(input)
                .setOptions(opts)
                .setDimensions(dimensions);
    }

    /**
     * Ollama Embedding 响应处理      * <p>
     * 流程：读取 OllamaEmbeddingResponse → 转换为 OpenAIEmbeddingResponse → ioCopyBytesGracefully 写入客户端
     */
    public static Usage ollamaEmbeddingHandler(HttpServletRequest request, HttpServletResponse response,
                                                RelayInfo info, byte[] responseBody) throws Exception {
        OllamaDTO.OllamaEmbeddingResponse oResp;
        try {
            oResp = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), OllamaDTO.OllamaEmbeddingResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse Ollama embedding response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Ollama embedding response", e);
        }

        if (oResp.getError() != null && !oResp.getError().isEmpty()) {
            throw new RuntimeException("Ollama error: " + oResp.getError());
        }

        // 构建 OpenAIEmbeddingResponse
        List<OpenAIEmbeddingResponseItem> data = new ArrayList<>();
        if (oResp.getEmbeddings() != null) {
            for (int i = 0; i < oResp.getEmbeddings().size(); i++) {
                OpenAIEmbeddingResponseItem item = new OpenAIEmbeddingResponseItem();
                item.setIndex(i);
                item.setObject("embedding");
                item.setEmbedding(oResp.getEmbeddings().get(i));
                data.add(item);
            }
        }

        Usage usage = new Usage();
        usage.setPromptTokens(oResp.getPromptEvalCount());
        usage.setCompletionTokens(0);
        usage.setTotalTokens(oResp.getPromptEvalCount());

        // OpenAIEmbeddingResponse.usage 类型为 OpenAIResponseDTO.Usage（内部类），
        // 与返回值 yaoshu.token.pojo.dto.Usage 不兼容，需手动构造
        OpenAIResponseDTO.Usage respUsage = new OpenAIResponseDTO.Usage();
        respUsage.setPromptTokens(oResp.getPromptEvalCount());
        respUsage.setCompletionTokens(0);
        respUsage.setTotalTokens(oResp.getPromptEvalCount());

        OpenAIEmbeddingResponse embResp = new OpenAIEmbeddingResponse();
        embResp.setObject("list");
        embResp.setData(data);
        embResp.setModel(info.getUpstreamModelName());
        embResp.setUsage(respUsage);

        byte[] out = Convert.toJSONString(embResp).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpProxyService.ioCopyBytesGracefully(request, response, null, 200, out);
        return usage;
    }

    // ======================== 图片 base64 提取（辅助） ========================

    /**
     * 从图片 URL 提取 base64 数据      * <p>
     * 支持 data URI（data:image/...;base64,...）和远程 URL（通过 HTTP 下载）。
     */
    private static String extractImageBase64(String url) {
        if (url == null || url.isEmpty()) return null;
        // data URI 格式：data:image/png;base64,xxxx
        if (url.startsWith("data:")) {
            int commaIdx = url.indexOf(",");
            if (commaIdx != -1) {
                String header = url.substring(0, commaIdx);
                if (header.contains("base64")) {
                    return url.substring(commaIdx + 1);
                }
                return Base64.getEncoder().encodeToString(url.substring(commaIdx + 1)
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            HttpResponse<byte[]> response = DownloadService.doDownloadRequest(url, "fetch image for ollama chat");
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String mimeType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            String base64 = Base64.getEncoder().encodeToString(bytes);
            try (CachedFileData cache = CachedFileData.newMemory(base64, mimeType, bytes.length)) {
                return cache.getBase64Data();
            }
        } catch (Exception e) {
            log.warn("Failed to download image for Ollama base64 extraction: {}", e.getMessage());
            return null;
        }
    }
}
