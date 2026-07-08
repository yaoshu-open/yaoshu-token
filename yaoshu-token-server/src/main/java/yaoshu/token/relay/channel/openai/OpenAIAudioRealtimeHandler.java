package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.RealtimeEvent;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;
import yaoshu.token.service.AudioService;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.QuotaService;
import yaoshu.token.service.TokenCounterService;
import yaoshu.token.service.AudioHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Audio / Realtime 处理器（TTS/STT + WebSocket Realtime），
 */
@Slf4j
public final class OpenAIAudioRealtimeHandler {    /** PCM 音频参数：采样率 24000 Hz, 16-bit (2 bytes), 单声道 */
    private static final int PCM_SAMPLE_RATE = 24000;
    private static final int PCM_BYTES_PER_SAMPLE = 2;
    private static final int PCM_CHANNELS = 1;

    private OpenAIAudioRealtimeHandler() {
    }

    // ======================== TTS（Text-to-Speech） ========================

    /**
     * OpenAI TTS 处理（流式 + 非流式）      * <p>
     * 流式：StreamScanner 提取 usage + 透传 SSE 事件
     * 非流式：全量读取 → 计算音频时长 → usage 估计
     *
     * @param info           Relay 上下文
     * @param inputStream    上游 HTTP 响应 InputStream
     * @param isStream       是否流式
     * @param audioFormat    音频格式（mp3/pcm/opus/aac/flac）
     */
    public static Usage openaiTTSHandler(RelayInfo info, InputStream inputStream,
                                         boolean isStream, String audioFormat) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) {
            Usage fallback = new Usage();
            fallback.setPromptTokens(info.getEstimatePromptTokens());
            fallback.setTotalTokens(info.getEstimatePromptTokens());
            return fallback;
        }

        Usage usage = new Usage();
        usage.setPromptTokens(info.getEstimatePromptTokens());
        usage.setTotalTokens(info.getEstimatePromptTokens());

        if (isStream) {
            // ====== 流式 TTS：StreamScanner 扫描 SSE events ======
            final Usage[] streamUsage = {usage};
            StreamScanner.scan(inputStream, info, data -> {
                // 提取 usage
                if (data.contains("usage")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataMap = Convert.toJSONObject(data);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usageMap = (Map<String, Object>) dataMap.get("usage");
                        if (usageMap != null) {
                            Number totalTokens = (Number) usageMap.get("total_tokens");
                            if (totalTokens != null && totalTokens.intValue() > 0) {
                                streamUsage[0] = Convert.toJavaBean(usageMap, Usage.class);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse TTS stream usage: {}", e.getMessage());
                    }
                }
                // 透传 SSE data
                RelayCommonHelper.stringData(response, data);
                return true;
            }, response);
            usage = streamUsage[0];
        } else {
            // ====== 非流式 TTS：全量读取 → 计算音频时长 ======
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = inputStream.read(buf)) != -1) {
                buffer.write(buf, 0, n);
            }
            byte[] bodyBytes = buffer.toByteArray();

            // 写入客户端
            response.getOutputStream().write(bodyBytes);
            response.getOutputStream().flush();

            // 计算音频时长
            String format = audioFormat != null ? audioFormat : "mp3";
            double duration;
            if ("pcm".equalsIgnoreCase(format)) {
                // PCM：= 字节数 / (采样率 * 字节/采样 * 声道数)
                duration = (double) bodyBytes.length
                        / (PCM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE * PCM_CHANNELS);
            } else {
                // 非 PCM：通过 MP3/WAV header 解析时长（简化估算）
                duration = estimateAudioDuration(bodyBytes, format);
            }

            // Token 估计：completionTokens = ceil(duration) / 60.0 * 1000（每分钟 1000 tokens）
            int completionTokens;
            if (duration > 0) {
                completionTokens = (int) Math.round(Math.ceil(duration) / 60.0 * 1000);
            } else {
                // 无法获取时长：按 body 大小粗略估算（每 KB ≈ 1 token）
                completionTokens = Math.max(1, (int) Math.ceil(bodyBytes.length / 1000.0));
            }
            usage.setCompletionTokens(completionTokens);
            usage.getCompletionTokenDetails().setAudioTokens(completionTokens);
            usage.setTotalTokens(usage.getPromptTokens() + completionTokens);
        }

        return usage;
    }

    // ======================== STT（Speech-to-Text） ========================

    /**
     * OpenAI STT 处理（Whisper 转录/翻译）      * <p>
     * 读取完整响应体 → 解析 usage → write 回客户端
     */
    public static Usage openaiSTTHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) {
            return new Usage();
        }

        // 写入客户端
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();

        // 解析 usage
        Usage usage = new Usage();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = Convert.toJSONObject(responseBody);
            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) bodyMap.get("usage");
            if (usageMap != null) {
                Number totalTokens = (Number) usageMap.get("total_tokens");
                if (totalTokens != null && totalTokens.intValue() > 0) {
                    usage = Convert.toJavaBean(usageMap, Usage.class);
                    // 兼容 input/output 映射到 prompt/completion
                    if (usage.getPromptTokens() == 0) {
                        Object inputTokens = usageMap.get("input_tokens");
                        if (inputTokens instanceof Number) {
                            usage.setPromptTokens(((Number) inputTokens).intValue());
                        }
                    }
                    if (usage.getCompletionTokens() == 0) {
                        Object outputTokens = usageMap.get("output_tokens");
                        if (outputTokens instanceof Number) {
                            usage.setCompletionTokens(((Number) outputTokens).intValue());
                        }
                    }
                    return usage;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse STT response usage: {}", e.getMessage());
        }

        // 回退：使用估计值
        usage.setPromptTokens(info.getEstimatePromptTokens());
        usage.setCompletionTokens(0);
        usage.setTotalTokens(usage.getPromptTokens());
        return usage;
    }

    // ======================== Realtime (WebSocket) ========================

    /**
     * OpenAI Realtime WebSocket 中继      */
    public static Usage openaiRealtimeHandler(RelayInfo info) throws Exception {
        if (info.getRealtimeUsage() == null) {
            info.setRealtimeUsage(new Usage());
        }
        return info.getRealtimeUsage();
    }

    public static void handleClientEvent(RelayInfo info, String payload,
                                         TokenCounterService tokenCounterService) throws Exception {
        RealtimeEvent event = parseEvent(payload);
        if (event == null) {
            return;
        }
        Usage pendingUsage = getOrInitPendingUsage(info);
        String model = info.getUpstreamModelName() != null ? info.getUpstreamModelName() : info.getOriginModelName();
        switch (event.getType()) {
            case RealtimeEvent.TYPE_SESSION_UPDATE -> {
                if (event.getSession() != null) {
                    if (event.getSession().getTools() != null) {
                        info.setRealtimeTools(new ArrayList<>(event.getSession().getTools()));
                    }
                    if (event.getSession().getInstructions() != null) {
                        int textTokens = tokenCounterService.countTextToken(event.getSession().getInstructions(), model);
                        addInputTextTokens(pendingUsage, textTokens);
                    }
                }
            }
            case RealtimeEvent.TYPE_INPUT_AUDIO_BUFFER_APPEND -> {
                int audioTokens = countAudioTokens(event.getAudio(), info.getInputAudioFormat(), tokenCounterService, true);
                addInputAudioTokens(pendingUsage, audioTokens);
            }
            case RealtimeEvent.TYPE_CONVERSATION_ITEM_CREATED -> {
                if (event.getItem() != null && "message".equals(event.getItem().getType())
                        && event.getItem().getContent() != null) {
                    for (RealtimeEvent.RealtimeContent content : event.getItem().getContent()) {
                        if ("input_text".equals(content.getType()) && content.getText() != null) {
                            addInputTextTokens(pendingUsage,
                                    tokenCounterService.countTextToken(content.getText(), model));
                        }
                    }
                }
            }
            default -> {
            }
        }
        refreshTotals(pendingUsage);
    }

    public static void handleUpstreamEvent(RelayInfo info, String payload,
                                           TokenCounterService tokenCounterService,
                                           QuotaService quotaService,
                                           BillingService billingService) throws Exception {
        RealtimeEvent event = parseEvent(payload);
        if (event == null) {
            return;
        }
        Usage pendingUsage = getOrInitPendingUsage(info);
        String model = info.getUpstreamModelName() != null ? info.getUpstreamModelName() : info.getOriginModelName();
        switch (event.getType()) {
            case RealtimeEvent.TYPE_SESSION_UPDATED, RealtimeEvent.TYPE_SESSION_CREATED -> {
                if (event.getSession() != null) {
                    if (event.getSession().getInputAudioFormat() != null) {
                        info.setInputAudioFormat(event.getSession().getInputAudioFormat());
                    }
                    if (event.getSession().getOutputAudioFormat() != null) {
                        info.setOutputAudioFormat(event.getSession().getOutputAudioFormat());
                    }
                }
            }
            case RealtimeEvent.TYPE_RESPONSE_AUDIO_DELTA -> {
                int audioTokens = countAudioTokens(event.getDelta(), info.getOutputAudioFormat(), tokenCounterService, false);
                addOutputAudioTokens(pendingUsage, audioTokens);
            }
            case RealtimeEvent.TYPE_RESPONSE_AUDIO_TRANSCRIPTION_DELTA,
                    RealtimeEvent.TYPE_RESPONSE_FUNCTION_CALL_ARGUMENTS_DELTA -> {
                int textTokens = tokenCounterService.countTextToken(event.getDelta(), model);
                addOutputTextTokens(pendingUsage, textTokens);
            }
            case RealtimeEvent.TYPE_RESPONSE_DONE -> {
                if (event.getResponse() != null && event.getResponse().getUsage() != null
                        && event.getResponse().getUsage().getTotalTokens() > 0) {
                    mergeUsage(pendingUsage, normalizeRealtimeUsage(event.getResponse().getUsage()));
                } else {
                    appendRealtimeToolTokens(info, pendingUsage, tokenCounterService, model);
                }
                refreshTotals(pendingUsage);
                consumePendingUsage(info, quotaService, billingService);
                info.setFirstRequest(false);
            }
            default -> {
            }
        }
        refreshTotals(pendingUsage);
    }

    public static void flushPendingUsage(RelayInfo info, QuotaService quotaService, BillingService billingService) {
        Usage pendingUsage = getOrInitPendingUsage(info);
        refreshTotals(pendingUsage);
        if (pendingUsage.getTotalTokens() > 0) {
            consumePendingUsage(info, quotaService, billingService);
        }
    }

    // ======================== 音频时长估算 ========================

    /**
     * 估算音频时长（秒），通过 MP3/WAV 文件头信息解析
     * <p>
     * 简化实现：通过文件大小和比特率估算。
     * 准确时长计算需要 FFmpeg 或 javax.sound 库，当前阶段用公式粗略估算。
     */
    private static double estimateAudioDuration(byte[] audioData, String format) {
        if (audioData == null || audioData.length == 0 || format == null) {
            return 0;
        }
        String fmt = format.toLowerCase();

        // MP3 文件头解析
        if ("mp3".equals(fmt) || "mpeg".equals(fmt)) {
            // MP3 典型比特率 128kbps = 16KB/s
            return audioData.length / 16000.0;
        }
        // WAV 文件头解析（44 字节 Header）
        if ("wav".equals(fmt) && audioData.length > 44) {
            // byteRate = sampleRate * channels * bitsPerSample / 8
            // 默认 CD 品质: 44100 * 2 * 16 / 8 = 176400
            return (double) (audioData.length - 44) / 176400.0;
        }
        // Opus 默认 48kbps = 6KB/s
        if ("opus".equals(fmt) || "ogg".equals(fmt)) {
            return audioData.length / 6000.0;
        }
        // AAC 典型 128kbps = 16KB/s
        if ("aac".equals(fmt) || "flac".equals(fmt)) {
            return audioData.length / 16000.0;
        }

        // 回退：常见格式平均 16KB/s
        return audioData.length / 16000.0;
    }

    private static RealtimeEvent parseEvent(String payload) throws Exception {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        return Convert.toJavaBean(payload, RealtimeEvent.class);
    }

    private static Usage getOrInitPendingUsage(RelayInfo info) {
        if (info.getRealtimePendingUsage() == null) {
            info.setRealtimePendingUsage(new Usage());
        }
        if (info.getRealtimeUsage() == null) {
            info.setRealtimeUsage(new Usage());
        }
        return info.getRealtimePendingUsage();
    }

    private static void consumePendingUsage(RelayInfo info, QuotaService quotaService, BillingService billingService) {
        Usage pendingUsage = getOrInitPendingUsage(info);
        if (pendingUsage.getTotalTokens() <= 0) {
            return;
        }
        mergeUsage(info.getRealtimeUsage(), pendingUsage);
        int actualQuota = calculateRealtimeQuota(info, quotaService, pendingUsage);
        if (actualQuota > 0) {
            // 额度预检查：不足时抛出异常中断 Realtime 会话
            billingService.preWssConsumeQuota(info, actualQuota);
            billingService.settleBillingAndLog(info, actualQuota, pendingUsage);
            // Realtime 多次结算：BillingSession 路径结算后清空，防止后续重复结算
            if (info.getBilling() != null) {
                info.setBilling(null);
            }
        }
        info.setRealtimePendingUsage(new Usage());
    }

    private static int calculateRealtimeQuota(RelayInfo info, QuotaService quotaService, Usage usage) {
        if (usage == null) {
            return 0;
        }
        boolean hasAudio = usage.getPromptTokensDetails() != null
                && (usage.getPromptTokensDetails().getAudioTokens() > 0
                || usage.getCompletionTokenDetails().getAudioTokens() > 0);
        if (hasAudio) {
            return quotaService.calculateAudioQuota(
                    usage.getPromptTokensDetails().getTextTokens(),
                    usage.getCompletionTokenDetails().getTextTokens(),
                    usage.getPromptTokensDetails().getAudioTokens(),
                    usage.getCompletionTokenDetails().getAudioTokens(),
                    info.getPriceData()
            );
        }
        return quotaService.calculateTextQuotaWithCache(info, usage);
    }

    private static Usage normalizeRealtimeUsage(Usage usage) {
        if (usage == null) {
            return new Usage();
        }
        Usage normalized = new Usage();
        normalized.setPromptTokens(usage.getInputTokens() > 0 ? usage.getInputTokens() : usage.getPromptTokens());
        normalized.setCompletionTokens(usage.getOutputTokens() > 0 ? usage.getOutputTokens() : usage.getCompletionTokens());
        normalized.setTotalTokens(usage.getTotalTokens());
        normalized.setPromptTokensDetails(new Usage.PromptTokensDetails(
                usage.getInputTokensDetails() != null ? usage.getInputTokensDetails().getCachedTokens() : usage.getPromptTokensDetails().getCachedTokens(),
                usage.getPromptTokensDetails().getCachedCreationTokens(),
                usage.getInputTokensDetails() != null ? usage.getInputTokensDetails().getAudioTokens() : usage.getPromptTokensDetails().getAudioTokens(),
                usage.getInputTokensDetails() != null ? usage.getInputTokensDetails().getTextTokens() : usage.getPromptTokensDetails().getTextTokens(),
                usage.getInputTokensDetails() != null ? usage.getInputTokensDetails().getImageTokens() : usage.getPromptTokensDetails().getImageTokens()
        ));
        normalized.setCompletionTokenDetails(new Usage.CompletionTokenDetails(
                usage.getCompletionTokenDetails().getReasoningTokens(),
                usage.getCompletionTokenDetails().getAudioTokens(),
                usage.getCompletionTokenDetails().getTextTokens(),
                usage.getCompletionTokenDetails().getImageTokens()
        ));
        normalized.setInputTokens(normalized.getPromptTokens());
        normalized.setOutputTokens(normalized.getCompletionTokens());
        refreshTotals(normalized);
        return normalized;
    }

    private static void mergeUsage(Usage target, Usage delta) {
        if (target == null || delta == null) {
            return;
        }
        target.setPromptTokens(target.getPromptTokens() + delta.getPromptTokens());
        target.setCompletionTokens(target.getCompletionTokens() + delta.getCompletionTokens());
        target.setTotalTokens(target.getTotalTokens() + delta.getTotalTokens());
        target.setInputTokens(target.getInputTokens() + delta.getInputTokens());
        target.setOutputTokens(target.getOutputTokens() + delta.getOutputTokens());
        target.getPromptTokensDetails().setCachedTokens(
                target.getPromptTokensDetails().getCachedTokens() + delta.getPromptTokensDetails().getCachedTokens());
        target.getPromptTokensDetails().setAudioTokens(
                target.getPromptTokensDetails().getAudioTokens() + delta.getPromptTokensDetails().getAudioTokens());
        target.getPromptTokensDetails().setTextTokens(
                target.getPromptTokensDetails().getTextTokens() + delta.getPromptTokensDetails().getTextTokens());
        target.getCompletionTokenDetails().setAudioTokens(
                target.getCompletionTokenDetails().getAudioTokens() + delta.getCompletionTokenDetails().getAudioTokens());
        target.getCompletionTokenDetails().setTextTokens(
                target.getCompletionTokenDetails().getTextTokens() + delta.getCompletionTokenDetails().getTextTokens());
        refreshTotals(target);
    }

    private static void appendRealtimeToolTokens(RelayInfo info, Usage usage,
                                                 TokenCounterService tokenCounterService, String model) {
        if (Boolean.TRUE.equals(info.isFirstRequest()) || info.getRealtimeTools() == null) {
            return;
        }
        int textTokens = 0;
        for (Object tool : info.getRealtimeTools()) {
            textTokens += 8;
            textTokens += tokenCounterService.countTokenInput(tool, model);
        }
        addInputTextTokens(usage, textTokens);
    }

    private static void addInputTextTokens(Usage usage, int tokens) {
        if (tokens <= 0) {
            return;
        }
        usage.setPromptTokens(usage.getPromptTokens() + tokens);
        usage.setInputTokens(usage.getInputTokens() + tokens);
        usage.getPromptTokensDetails().setTextTokens(usage.getPromptTokensDetails().getTextTokens() + tokens);
    }

    private static void addOutputTextTokens(Usage usage, int tokens) {
        if (tokens <= 0) {
            return;
        }
        usage.setCompletionTokens(usage.getCompletionTokens() + tokens);
        usage.setOutputTokens(usage.getOutputTokens() + tokens);
        usage.getCompletionTokenDetails().setTextTokens(usage.getCompletionTokenDetails().getTextTokens() + tokens);
    }

    private static void addInputAudioTokens(Usage usage, int tokens) {
        if (tokens <= 0) {
            return;
        }
        usage.setPromptTokens(usage.getPromptTokens() + tokens);
        usage.setInputTokens(usage.getInputTokens() + tokens);
        usage.getPromptTokensDetails().setAudioTokens(usage.getPromptTokensDetails().getAudioTokens() + tokens);
    }

    private static void addOutputAudioTokens(Usage usage, int tokens) {
        if (tokens <= 0) {
            return;
        }
        usage.setCompletionTokens(usage.getCompletionTokens() + tokens);
        usage.setOutputTokens(usage.getOutputTokens() + tokens);
        usage.getCompletionTokenDetails().setAudioTokens(usage.getCompletionTokenDetails().getAudioTokens() + tokens);
    }

    private static void refreshTotals(Usage usage) {
        usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
    }

    private static int countAudioTokens(String base64Audio, String audioFormat,
                                        TokenCounterService tokenCounterService,
                                        boolean input) {
        if (base64Audio == null || base64Audio.isBlank()) {
            return 0;
        }
        try {
            byte[] audioBytes = AudioService.decodeBase64AudioData(base64Audio);
            double duration = estimateRealtimeAudioDuration(audioBytes, audioFormat);
            if (input) {
                return tokenCounterService.countAudioTokenInput(duration);
            }
            return tokenCounterService.countAudioTokenOutput(duration);
        } catch (Exception e) {
            log.debug("Failed to count realtime audio tokens: {}", e.getMessage());
            return 0;
        }
    }

    private static double estimateRealtimeAudioDuration(byte[] audioBytes, String audioFormat) {
        if (audioBytes == null || audioBytes.length == 0) {
            return 0;
        }
        String format = audioFormat == null ? "" : audioFormat.toLowerCase();
        if ("pcm16".equals(format)) {
            return (double) audioBytes.length / (PCM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE * PCM_CHANNELS);
        }
        if ("g711_ulaw".equals(format) || "g711_alaw".equals(format)) {
            return (double) audioBytes.length / 8000.0;
        }
        try {
            return AudioHandler.getAudioDuration(new ByteArrayInputStream(audioBytes), ".wav");
        } catch (Exception ignored) {
            return 0;
        }
    }
}
