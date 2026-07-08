package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Image 响应处理器（DALL-E / GPT Image / 流式 + 非流式），
 */
@Slf4j
public final class OpenAIImageHandler {    private OpenAIImageHandler() {
    }

    // ======================== 非流式图片响应 ========================

    /**
     * 非流式图片响应处理      * <p>
     * 读取完整响应体 → 解析 usage → write 回客户端
     */
    public static Usage openaiImageHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) {
            return new Usage();
        }

        // 1. 解析 usage
        Usage usage = extractUsageFromBody(responseBody);

        // 2. normalizeOpenAIUsage：将 input_tokens/output_tokens 映射到 prompt/completion
        normalizeOpenAIUsage(usage);

        // 3. Usage 后处理
        UsagePostProcessor.applyUsagePostProcessing(info, usage, responseBody);

        // 4. 写入客户端
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();

        return usage;
    }

    // ======================== 流式图片响应 ========================

    /**
     * 流式图片响应处理（SSE 格式）      * <p>
     * StreamScanner 扫描 SSE events → 提取 usage → writeOpenaiImageStreamChunk 每帧写入
     */
    public static Usage openaiImageStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) {
            return new Usage();
        }

        Usage[] usage = {new Usage()};
        final byte[][] lastStreamData = {null};

        // StreamScanner 扫描 SSE 流
        StreamScanner.scan(inputStream, info, data -> {
            byte[] raw = data.getBytes();
            lastStreamData[0] = raw;

            // 检测流错误事件
            if (isOpenAIImageStreamErrorEvent(raw)) {
                String errMsg = extractOpenAIImageStreamErrorMessage(raw);
                log.warn("Image stream error: {}", errMsg);
                // 继续处理（不中断流）
            }

            // 提取 usage
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = Convert.toJSONObject(raw);
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMap = (Map<String, Object>) dataMap.get("usage");
                if (usageMap != null) {
                    Number totalTokens = (Number) usageMap.get("total_tokens");
                    if (totalTokens != null && totalTokens.intValue() > 0) {
                        usage[0] = parseUsageFromMap(usageMap);
                        normalizeOpenAIUsage(usage[0]);
                    }
                }
            } catch (Exception e) {
                // 非 JSON 或 parsing 失败，跳过
                log.debug("解析 image stream chunk usage 失败，跳过", e);
            }

            // 写入 SSE chunk（event: + data: 格式）
            writeOpenaiImageStreamChunk(response, raw);
            return true;
        }, response);

        // 发送 [DONE]
        RelayCommonHelper.done(response);

        // Usage 后处理
        if (lastStreamData[0] != null && usage[0] != null) {
            UsagePostProcessor.applyUsagePostProcessing(info, usage[0], lastStreamData[0]);
        }

        return usage[0];
    }

    // ======================== JSON-as-Stream ========================

    /**
     * JSON-as-Stream 降级处理（非 SSE Content-Type 的流式回复）      * <p>
     * 完整读取 JSON body → 将每张图片封装为 SSE event 逐条发送
     */
    public static Usage openaiImageJSONAsStreamHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) {
            return new Usage();
        }

        // 解析完整 JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = Convert.toJSONObject(responseBody);

        // 提取 usage
        @SuppressWarnings("unchecked")
        Map<String, Object> usageMap = (Map<String, Object>) bodyMap.get("usage");
        Usage usage = usageMap != null ? parseUsageFromMap(usageMap) : new Usage();
        normalizeOpenAIUsage(usage);

        // 设置 SSE 头
        RelayCommonHelper.setEventStreamHeaders(response);
        response.setStatus(200);

        long created = 0;
        Object createdObj = bodyMap.get("created");
        if (createdObj instanceof Number) {
            created = ((Number) createdObj).longValue();
        }
        if (created <= 0) {
            created = System.currentTimeMillis() / 1000;
        }

        info.setFirstResponseTime();

        // 逐张图片发送 SSE event
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) bodyMap.get("data");
        if (dataList != null) {
            for (Map<String, Object> image : dataList) {
                String url = image.get("url") != null ? image.get("url").toString() : "";
                String b64Json = image.get("b64_json") != null ? image.get("b64_json").toString() : "";
                String revisedPrompt = image.get("revised_prompt") != null
                        ? image.get("revised_prompt").toString() : "";

                // 先写 event: 行
                response.getOutputStream().write("event: image_generation.completed\n".getBytes());

                // 构建 data 行
                StringBuilder dataBuilder = new StringBuilder("{");
                dataBuilder.append("\"type\":\"image_generation.completed\"");
                dataBuilder.append(",\"created_at\":").append(created);
                if (!url.isEmpty()) {
                    dataBuilder.append(",\"url\":").append(Convert.toJSONString(url));
                }
                if (!b64Json.isEmpty()) {
                    dataBuilder.append(",\"b64_json\":").append(Convert.toJSONString(b64Json));
                }
                if (!revisedPrompt.isEmpty()) {
                    dataBuilder.append(",\"revised_prompt\":").append(Convert.toJSONString(revisedPrompt));
                }
                if (validUsage(usage)) {
                    dataBuilder.append(",\"usage\":").append(Convert.toJSONString(usage));
                }
                dataBuilder.append("}");

                response.getOutputStream()
                        .write(("data: " + dataBuilder.toString() + "\n\n").getBytes());
                RelayCommonHelper.flushWriter(response);
            }
        }

        // 发送 [DONE]
        RelayCommonHelper.done(response);

        // Usage 后处理
        UsagePostProcessor.applyUsagePostProcessing(info, usage, responseBody);

        return usage;
    }

    // ======================== 内部辅助方法 ========================

    /**
     * normalizeOpenAIUsage：将 input_tokens/output_tokens 映射到 prompt/completion，
     */
    static void normalizeOpenAIUsage(Usage usage) {
        if (usage == null) return;
        // 图片 API 不返回 prompt_tokens/completion_tokens，而是 input_tokens/output_tokens
        // 当前 Java Usage 类以 promptTokens/completionTokens 为主字段
        // 如果没有值，说明上游未直接返回 prompt/completion，从 input/output 推断
        if (usage.getPromptTokens() == 0 && usage.getInputTokensDetails() != null) {
            // 从 InputTokensDetails 推断（如有需要）
        }
        if (usage.getTotalTokens() == 0) {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
    }

    /**
     * 从 Map 解析 Usage
     */
    private static Usage parseUsageFromMap(Map<String, Object> map) {
        Usage usage = new Usage();
        if (map.containsKey("prompt_tokens")) {
            usage.setPromptTokens(toInt(map.get("prompt_tokens")));
        }
        if (map.containsKey("completion_tokens")) {
            usage.setCompletionTokens(toInt(map.get("completion_tokens")));
        }
        if (map.containsKey("total_tokens")) {
            usage.setTotalTokens(toInt(map.get("total_tokens")));
        }
        if (map.containsKey("input_tokens")) {
            // 映射 input_tokens → prompt_tokens
            if (usage.getPromptTokens() == 0) {
                usage.setPromptTokens(toInt(map.get("input_tokens")));
            }
        }
        if (map.containsKey("output_tokens")) {
            if (usage.getCompletionTokens() == 0) {
                usage.setCompletionTokens(toInt(map.get("output_tokens")));
            }
        }
        if (usage.getTotalTokens() == 0) {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        return usage;
    }

    /**
     * 从响应体提取 usage
     */
    private static Usage extractUsageFromBody(byte[] body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = Convert.toJSONObject(body);
            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) bodyMap.get("usage");
            if (usageMap != null) {
                return parseUsageFromMap(usageMap);
            }
        } catch (Exception e) {
            log.debug("Failed to extract usage from image response body: {}", e.getMessage());
        }
        return new Usage();
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (Exception e) {
                log.debug("String 转 int 失败，返回 0: {}", val, e);
            }
        }
        return 0;
    }

    // ======================== 流错误检测（Go isOpenAIImageStreamErrorEvent） ========================

    static boolean isOpenAIImageStreamErrorEvent(byte[] data) {
        try {
            Convert.toJSONString(data); // JSON 有效性检测
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = Convert.toJSONObject(data);
            String type = payload.get("type") != null
                    ? payload.get("type").toString().toLowerCase().trim() : "";
            Object error = payload.get("error");
            return "error".equals(type) || "upstream_error".equals(type) || error != null;
        } catch (Exception e) {
            return false;
        }
    }

    static String extractOpenAIImageStreamErrorMessage(byte[] data) {
        if (data == null || data.length == 0) return "upstream image stream returned error event";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = Convert.toJSONObject(data);
            String message = payload.get("message") != null
                    ? payload.get("message").toString().trim() : "";
            if (!message.isEmpty()) return message;
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) payload.get("error");
            if (error != null) {
                String errMsg = error.get("message") != null
                        ? error.get("message").toString().trim() : "";
                if (!errMsg.isEmpty()) return errMsg;
            }
        } catch (Exception e) {
            log.debug("解析 image stream error event 失败", e);
        }
        return "upstream image stream returned error event";
    }

    /**
     * 写入图片流 SSE chunk：先写 event: 行（如存在），再写 data: 行，
     */
    private static void writeOpenaiImageStreamChunk(HttpServletResponse response, byte[] data) throws Exception {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = Convert.toJSONObject(data);
            String type = payload.get("type") != null
                    ? payload.get("type").toString().trim() : "";
            if (!type.isEmpty()) {
                response.getOutputStream()
                        .write(("event: " + type + "\n").getBytes());
            }
        } catch (Exception e) {
            log.debug("写入 image SSE event 行失败", e);
        }
        response.getOutputStream().write("data: ".getBytes());
        response.getOutputStream().write(data);
        response.getOutputStream().write("\n\n".getBytes());
        RelayCommonHelper.flushWriter(response);
    }

    /**
     * 校验 Usage 是否有效      */
    private static boolean validUsage(Usage usage) {
        return usage != null && (usage.getPromptTokens() > 0
                || usage.getCompletionTokens() > 0
                || usage.getTotalTokens() > 0);
    }
}
