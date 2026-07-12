package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.dto.ChatCompletionsStreamResponse;
import yaoshu.token.pojo.dto.ClaudeDTO.ClaudeResponse;
import yaoshu.token.pojo.dto.GeminiDTO.GeminiChatResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;
import yaoshu.token.service.ConvertService;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat 响应处理器（流式 + 非流式）  * <p>
 * 核心流程：
 * - OaiStreamHandler：SSE 流式扫描 → token 累积 → 最后响应处理
 * - OpenaiHandler：全量响应读取 → usage 补全 → 格式转换（OpenAI/Claude/Gemini）
 */
@Slf4j
public final class OpenAIRelayHandler {    private OpenAIRelayHandler() {
    }

    // ======================== 流式处理 ========================

    /**
     * OpenAI Chat 流式处理器      * <p>
     * 使用 StreamScanner 扫描 SSE 流，逐行累积 token 和响应文本。
     * 流末通过 handleLastResponse + handleFinalResponse 收尾。
     *
     * @param info         Relay 上下文（含 HttpServletResponse、ChannelSetting）
     * @param inputStream  上游 HTTP 响应 InputStream
     */
    public static Usage oaiStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) {
            Usage fallback = new Usage();
            fallback.setPromptTokens(info.getEstimatePromptTokens());
            fallback.setTotalTokens(info.getEstimatePromptTokens());
            return fallback;
        }

        String model = info.getUpstreamModelName();
        boolean forceFormat = info.getChannelSetting() != null
                && Boolean.TRUE.equals(info.getChannelSetting().getForceFormat());
        boolean thinkToContent = info.getChannelSetting() != null
                && Boolean.TRUE.equals(info.getChannelSetting().getThinkingToContent());

        // 音频模型检测
        boolean isAudioModel = model != null && model.toLowerCase().contains("audio");

        // 累积状态（用数组实现闭包可变引用）
        final String[] lastStreamData = {null};
        final String[] secondLastStreamData = {null};
        int[] toolCount = {0};
        StringBuilder responseTextBuilder = new StringBuilder();

        // ====== StreamScanner 扫描 SSE 流 ======
        StreamScanner.scan(inputStream, info, data -> {
            // 1. 先发送上一条已累积的流数据
            if (lastStreamData[0] != null) {
                OpenAIStreamHelper.handleStreamFormat(info, lastStreamData[0], forceFormat, thinkToContent);
            }

            if (data != null && !data.isEmpty() && !"[DONE]".equals(data)) {
                // 音频模型：保存倒数第二条数据用于提取 usage
                if (isAudioModel && lastStreamData[0] != null) {
                    secondLastStreamData[0] = lastStreamData[0];
                }

                lastStreamData[0] = data;

                // 2. 累积 token 数据
                OpenAIStreamHelper.processTokenData(info.getRelayMode(), data, responseTextBuilder, toolCount);
            }
            return true;
        }, response);

        // ====== 从音频模型的倒数第二条数据提取 usage ======
        Usage usage = new Usage();
        boolean containStreamUsage = false;

        if (isAudioModel && secondLastStreamData[0] != null) {
            try {
                Map<String, Object> streamMap = Convert.toJSONObject(secondLastStreamData[0]);
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMap = (Map<String, Object>) streamMap.get("usage");
                if (usageMap != null) {
                    Number totalTokens = (Number) usageMap.get("total_tokens");
                    if (totalTokens != null && totalTokens.intValue() > 0) {
                        usage = Convert.toJavaBean(usageMap, Usage.class);
                        containStreamUsage = true;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract audio model usage from second last SSE: {}", e.getMessage());
            }
        }

        // ====== 处理最后一条流数据 ======
        String[] responseId = {RelayCommonHelper.getResponseID(String.valueOf(System.currentTimeMillis()))};
        long[] createdAt = {0};
        String[] systemFp = {""};
        String[] responseModel = {model};
        boolean[] shouldSendLastResp = {true};

        if (lastStreamData[0] != null) {
            OpenAIStreamHelper.handleLastResponse(lastStreamData[0], responseId, createdAt,
                    systemFp, responseModel, new Usage[]{usage}, new boolean[]{containStreamUsage},
                    info, shouldSendLastResp);
        }

        // 发送最后一条流数据（仅 OpenAI 格式且 shouldSend）
        if ("openai".equals(info.getRelayFormat()) && shouldSendLastResp[0] && lastStreamData[0] != null) {
            OpenAIStreamHelper.sendStreamData(info, lastStreamData[0], forceFormat, thinkToContent);
        }

        // 如果没有从流中获取到 usage，通过 Token 估算器计算
        if (!containStreamUsage) {
            int completionTokens = estimateCompletionTokens(responseTextBuilder.toString(),
                    info.getUpstreamModelName());
            usage = new Usage();
            usage.setPromptTokens(info.getEstimatePromptTokens());
            usage.setCompletionTokens(completionTokens + toolCount[0] * 7);
            usage.setTotalTokens(info.getEstimatePromptTokens() + usage.getCompletionTokens());
        }

        // Usage 后处理
        if (lastStreamData[0] != null) {
            UsagePostProcessor.applyUsagePostProcessing(info, usage, lastStreamData[0].getBytes());
        }

        // ====== 发送最终响应（含 [DONE]） ======
        OpenAIStreamHelper.handleFinalResponse(info, lastStreamData[0],
                responseId[0], createdAt[0], responseModel[0],
                systemFp[0], usage, containStreamUsage);

        return usage;
    }

    // ======================== 非流式处理 ========================

    /**
     * OpenAI Chat 非流式处理      * <p>
     * 流程：读取完整上游响应体 → 解析 body → 补全 usage → 格式转换 → 写回客户端
     */
    @SuppressWarnings("unchecked")
    public static Usage openaiHandler(RelayInfo info, byte[] responseBody, Map<String, Object> rawResponseMap) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) {
            return new Usage();
        }

        // 1. 解析为 Map（避免 OpenAITextResponse.Usage 与 pojo.Usage 的类型冲突）
        Map<String, Object> bodyMap;
        if (rawResponseMap != null) {
            bodyMap = rawResponseMap;
        } else {
            bodyMap = Convert.toJSONObject(responseBody);
        }

        // 2. 检测 OpenAI API 错误
        Map<String, Object> errorObj = (Map<String, Object>) bodyMap.get("error");
        if (errorObj != null) {
            String errorType = errorObj.get("type") != null ? errorObj.get("type").toString() : "";
            if (!errorType.isEmpty()) {
                throw new Exception("Upstream OpenAI error: " + errorType);
            }
        }

        // 3. content_filter finish reason 检测
        List<Map<String, Object>> choices = (List<Map<String, Object>>) bodyMap.get("choices");
        if (choices != null) {
            for (Map<String, Object> choice : choices) {
                if ("content_filter".equals(choice.get("finish_reason"))) {
                    log.warn("OpenAI upstream returned finish_reason=content_filter");
                    break;
                }
            }
        }

        // 4. ForceFormat 标记
        boolean forceFormat = info.getChannelSetting() != null
                && Boolean.TRUE.equals(info.getChannelSetting().getForceFormat());

        // 5. Usage 补全：优先使用上游返回的 usage，否则通过 Token 估算
        Map<String, Object> usageMap = (Map<String, Object>) bodyMap.get("usage");
        boolean usageModified = false;
        Usage usage = new Usage();

        if (usageMap != null) {
            usage.setPromptTokens(toInt(usageMap.get("prompt_tokens")));
            usage.setCompletionTokens(toInt(usageMap.get("completion_tokens")));
            usage.setTotalTokens(toInt(usageMap.get("total_tokens")));
        }

        if (usage.getPromptTokens() == 0) {
            int completionTokens = usage.getCompletionTokens();
            if (completionTokens == 0 && choices != null) {
                for (Map<String, Object> choice : choices) {
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null && message.get("content") != null) {
                        String text = message.get("content").toString();
                        completionTokens += TokenCounterServiceHolder.countTextToken(text,
                                info.getUpstreamModelName());
                    }
                }
            }
            usage.setPromptTokens(info.getEstimatePromptTokens());
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(info.getEstimatePromptTokens() + completionTokens);
            usageModified = true;
        }

        // 6. Usage 后处理
        UsagePostProcessor.applyUsagePostProcessing(info, usage, responseBody);

        // 7. 格式转换并写回客户端
        byte[] finalBody;
        String relayFormat = info.getRelayFormat();
        if ("claude".equals(relayFormat)) {
            // Claude 格式：转换为 OpenAITextResponse → ConvertService
            OpenAITextResponse simpleResp = Convert.toJavaBean(
                    new String(responseBody, java.nio.charset.StandardCharsets.UTF_8),
                    OpenAITextResponse.class);
            if (simpleResp != null) {
                ClaudeResponse claudeResp = ConvertService.responseOpenAI2Claude(simpleResp, info);
                finalBody = Convert.toJSONString(claudeResp).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                finalBody = responseBody;
            }
        } else if ("gemini".equals(relayFormat)) {
            OpenAITextResponse simpleResp = Convert.toJavaBean(
                    new String(responseBody, java.nio.charset.StandardCharsets.UTF_8),
                    OpenAITextResponse.class);
            if (simpleResp != null) {
                GeminiChatResponse geminiResp = ConvertService.responseOpenAI2Gemini(simpleResp, info);
                finalBody = Convert.toJSONString(geminiResp).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                finalBody = responseBody;
            }
        } else {
            // OpenAI 格式
            if (usageModified) {
                bodyMap.put("usage", usage);
                finalBody = Convert.toJSONString(bodyMap).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if (forceFormat) {
                finalBody = Convert.toJSONString(bodyMap).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                finalBody = responseBody;
            }
        }

        // 写入客户端
        response.getOutputStream().write(finalBody);
        response.getOutputStream().flush();

        return usage;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * TokenCounterService 静态持有（避免循环依赖，通过 lazy holder 模式）
     */
    static class TokenCounterServiceHolder {
        static yaoshu.token.service.TokenCounterService instance;

        static int countTextToken(String text, String model) {
            if (instance != null) {
                return instance.countTextToken(text, model);
            }
            // 回退：简单字符估算（不能依赖 Spring Bean 时）
            return text != null ? text.length() / 4 : 0;
        }

        static void setInstance(yaoshu.token.service.TokenCounterService svc) {
            instance = svc;
        }
    }

    /**
     * 通过 TokenCounterService 估算 completion token 数，回退到字符估算
     */
    private static int estimateCompletionTokens(String text, String model) {
        if (text == null || text.isEmpty()) return 0;
        return TokenCounterServiceHolder.countTextToken(text, model);
    }
}
