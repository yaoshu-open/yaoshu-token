package yaoshu.token.relay.channel.openai;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.ReasoningSuffixConfig;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.relay.channel.ai360.AI360Constant;
import yaoshu.token.relay.channel.openrouter.OpenRouterConstant;
import yaoshu.token.relay.channel.xinference.XinferenceConstant;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponseChoice;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.IAdaptor.DoResponseResult;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;
import yaoshu.token.service.ConvertService;

import static yaoshu.token.relay.handler.CompatibleHandler.newApiError;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OpenAI 渠道适配器  * <p>
 * 兼容 45+ OpenAI 兼容厂商（Azure/OpenRouter/Custom/Xinference/360/LingYiWanWu 等），
 * 通过 ChannelType 切换模型列表和渠道名称。实现 IAdaptor 接口。
 */
@Slf4j
public class OpenAIAdaptor implements IAdaptor {    /** 渠道类型（用于模型列表/渠道名分发） */
    private int channelType;
    /** Audio 响应的输出格式 */
    private String responseFormat;

    // ======================== IAdaptor 接口实现 ========================

    @Override
    public void init(RelayInfo info) {
        this.channelType = info.getChannelType();

        // thinking_to_content 开关：初始化 ThinkingContentInfo
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        if (channelSetting != null && channelSetting.isThinkingToContent()) {
            // Info 字段在 RelayInfo 构造时已设默认值：isFirstThinkingContent=true
        }
    }

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        // Realtime 模式：http→ws 协议转换
        if (info.getRelayMode() == RelayModeEnum.REALTIME) {
            String baseUrl = info.getChannelBaseUrl();
            if (baseUrl != null && baseUrl.startsWith("https://")) {
                info.setChannelBaseUrl("wss://" + baseUrl.substring("https://".length()));
            } else if (baseUrl != null && baseUrl.startsWith("http://")) {
                info.setChannelBaseUrl("ws://" + baseUrl.substring("http://".length()));
            }
        }

        switch (this.channelType) {
            case ChannelConstants.CHANNEL_TYPE_AZURE: {
                String apiVersion = info.getApiVersion();
                if (apiVersion == null || apiVersion.isEmpty()) {
                    apiVersion = ChannelConstants.AZURE_DEFAULT_API_VERSION;
                }
                String requestURL = info.getRequestURLPath().split("\\?")[0];
                requestURL = requestURL + "?api-version=" + apiVersion;
                String task = requestURL.replaceFirst("^/v1/", "");

                if ("claude".equals(info.getRelayFormat())) {
                    task = task.replaceFirst("^messages", "chat/completions");
                }

                // Responses API（含 compact）
                if (info.getRelayMode() == RelayModeEnum.RESPONSES
                        || info.getRelayMode() == RelayModeEnum.RESPONSES_COMPACT) {
                    String responsesApiVersion = "preview";
                    String subUrl = "/openai/v1/responses";
                    if (info.getChannelBaseUrl() != null && info.getChannelBaseUrl().contains("cognitiveservices.azure.com")) {
                        subUrl = "/openai/responses";
                        responsesApiVersion = apiVersion;
                    }
                    ChannelOtherSettingsDTO otherSettings = info.getChannelOtherSettings();
                    if (otherSettings != null && otherSettings.getAzureResponsesVersion() != null
                            && !otherSettings.getAzureResponsesVersion().isEmpty()) {
                        responsesApiVersion = otherSettings.getAzureResponsesVersion();
                    }
                    if (info.getRelayMode() == RelayModeEnum.RESPONSES_COMPACT) {
                        subUrl = subUrl + "/compact";
                    }
                    requestURL = subUrl + "?api-version=" + responsesApiVersion;
                    return RelayUtils.getFullRequestURL(info.getChannelBaseUrl(), requestURL, info.getChannelType());
                }

                String model = info.getUpstreamModelName();
                // 2025年5月10日后创建的渠道不替换 .
                if (info.getChannelCreateTime() < ChannelConstants.AZURE_NO_REMOVE_DOT_TIME) {
                    model = model.replace(".", "");
                }
                if (info.getRelayMode() == RelayModeEnum.REALTIME) {
                    requestURL = "/openai/realtime?deployment=" + model + "&api-version=" + apiVersion;
                } else {
                    requestURL = "/openai/deployments/" + model + "/" + task;
                }
                return RelayUtils.getFullRequestURL(info.getChannelBaseUrl(), requestURL, info.getChannelType());
            }

            case ChannelConstants.CHANNEL_TYPE_CUSTOM: {
                String url = info.getChannelBaseUrl();
                if (url != null) {
                    url = url.replace("{model}", info.getUpstreamModelName());
                }
                return url;
            }

            default: {
                // Claude / Gemini 格式 → 固定 /v1/chat/completions
                if (("claude".equals(info.getRelayFormat()) || "gemini".equals(info.getRelayFormat()))
                        && info.getRelayMode() != RelayModeEnum.RESPONSES
                        && info.getRelayMode() != RelayModeEnum.RESPONSES_COMPACT) {
                    return info.getChannelBaseUrl() + "/v1/chat/completions";
                }
                return RelayUtils.getFullRequestURL(info.getChannelBaseUrl(), info.getRequestURLPath(), info.getChannelType());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();

        // Client headers（Content-Type/Accept）由 ApiRequestExecutor.setupApiRequestHeader 统一设置
        // 此处仅处理渠道特有的安全头（Authorization / api-key 等）

        // Azure
        if (this.channelType == ChannelConstants.CHANNEL_TYPE_AZURE) {
            headers.put("api-key", info.getApiKey());
            return headers;
        }

        // OpenAI Organization
        if (this.channelType == ChannelConstants.CHANNEL_TYPE_OPENAI && info.getOrganization() != null
                && !info.getOrganization().isEmpty()) {
            headers.put("OpenAI-Organization", info.getOrganization());
        }

        // Authorization（优先 Header Override，若已设则不重复）
        boolean hasAuthOverride = false;
        Map<String, Object> headersOverride = info.getHeadersOverride();
        if (headersOverride != null) {
            for (String k : headersOverride.keySet()) {
                if ("authorization".equalsIgnoreCase(k)) {
                    hasAuthOverride = true;
                    break;
                }
            }
        }

        // Realtime 模式特殊头（WebSocket 握手协议头由 ApiRequestExecutor.doWssRequest 统一设置）
        if (info.getRelayMode() == RelayModeEnum.REALTIME) {
            if (!hasAuthOverride) {
                headers.put("Authorization", "Bearer " + info.getApiKey());
            }
        } else {
            if (!hasAuthOverride) {
                headers.put("Authorization", "Bearer " + info.getApiKey());
            }
        }

        // OpenRouter
        if (this.channelType == ChannelConstants.CHANNEL_TYPE_OPENROUTER) {
            headers.putIfAbsent("HTTP-Referer", "https://token.yaoshu.cc");
            headers.putIfAbsent("X-OpenRouter-Title", "Yaoshu Token");
        }

        return headers;
    }

    // ======================== 请求转换 ========================

    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");

        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;

        // 上游模型名
        String upstreamModel = info.getUpstreamModelName();

        // OpenRouter 适配
        if (this.channelType == ChannelConstants.CHANNEL_TYPE_OPENROUTER) {
            // 添加 usage 参数
            if (openAIRequest.getUsage() == null) {
                openAIRequest.setUsage(Map.of("include", true));
            }
            // OpenRouter thinking 模式：检测 upstreamModel 后缀 -thinking，追加 Anthropic THINKING 字段
            if (upstreamModel != null && upstreamModel.endsWith("-thinking")) {
                // 去除 -thinking 后缀并写入 Anthropic thinking header
                info.setUpstreamModelName(upstreamModel.substring(0, upstreamModel.length() - "-thinking".length()));
                // Anthropic THINKING 字段通过 HeaderOverride 注入，不修改请求体
            }
        }

        // o 系列 / GPT-5 模型预处理
        boolean isOModel = isOModel(upstreamModel);
        boolean isGPT5Model = isGPT5Model(upstreamModel);

        if (isOModel || isGPT5Model) {
            // max_completion_tokens 优先于 max_tokens
            if ((openAIRequest.getMaxCompletionTokens() == null || openAIRequest.getMaxCompletionTokens() == 0)
                    && openAIRequest.getMaxTokens() != null && openAIRequest.getMaxTokens() > 0) {
                openAIRequest.setMaxCompletionTokens(openAIRequest.getMaxTokens());
                openAIRequest.setMaxTokens(null);
            }
            if (isOModel) {
                openAIRequest.setTemperature(null);
            }
            if (isGPT5Model) {
                openAIRequest.setTemperature(null);
                openAIRequest.setTopP(null);
                openAIRequest.setLogProbs(null);
            }

            // 解析推理力度后缀（Go: reasoning.ParseOpenAIReasoningEffortFromModelSuffix）
            String[] parsed = ReasoningSuffixConfig.parseOpenAIReasoningEffortFromModelSuffix(upstreamModel);
            String effort = parsed[0];
            String baseModel = parsed[1];
            if (effort != null && !effort.isEmpty()) {
                openAIRequest.setReasoningEffort(effort);
                info.setUpstreamModelName(baseModel);
            }

            // system → developer 转换已移除：developer role 仅 OpenAI 官方 API 支持，
            // 第三方聚合器普遍不兼容（返回 400 unknown variant `developer`）。
            // system role 被所有 OpenAI 兼容 API 通用支持（含官方 o-series/GPT-5 向后兼容）。
        }

        return openAIRequest;
    }

    @Override
    public Object convertRerankRequest(RelayInfo info, int relayMode, RerankRequest rerankRequest) throws Exception {
        return rerankRequest; // passthrough
    }

    @Override
    public Object convertEmbeddingRequest(RelayInfo info, EmbeddingDTO embeddingRequest) throws Exception {
        return embeddingRequest; // passthrough
    }

    @Override
    @SuppressWarnings("unchecked")
    public Reader convertAudioRequest(RelayInfo info, AudioDTO audioRequest) throws Exception {
        this.responseFormat = audioRequest.getResponseFormat();

        if (info.getRelayMode() == RelayModeEnum.AUDIO_SPEECH) {
            // TTS: serialize the original request body (Map with model/input/voice etc.)
            Object originalRequest = info.getRequest();
            String json = Convert.toJSONString(originalRequest != null ? originalRequest : audioRequest);
            return new StringReader(json);
        }
        // Transcription/Translation: the multipart body is passed through directly
        // by AudioHandler as raw bytes. Return empty reader as placeholder — the
        // actual upstream body is set by AudioHandler before calling doRequest.
        return new StringReader("");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convertImageRequest(RelayInfo info, OpenAIImageDTO imageRequest) throws Exception {
        int relayMode = info.getRelayMode();
        if (relayMode == RelayModeEnum.IMAGES_EDITS) {
            // 图片编辑暂按 JSON 请求透传；multipart 文件上传由外层 AudioHandler 统一处理
        }
        return imageRequest;
    }

    @Override
    public Object convertOpenAIResponsesRequest(RelayInfo info, OpenAIResponsesRequest responsesRequest) throws Exception {
        // 推理力度后缀由 convertOpenAIRequest 统一解析，此处透传
        return responsesRequest;
    }

    @Override
    public Object convertGeminiRequest(RelayInfo info, GeminiDTO.GeminiChatRequest request) throws Exception {
        OpenAIRequestDTO converted = ConvertService.geminiToOpenAIRequest(request, info);
        return convertOpenAIRequest(info, converted);
    }

    @Override
    public Object convertClaudeRequest(RelayInfo info, ClaudeDTO.ClaudeRequest request) throws Exception {
        OpenAIRequestDTO converted = ConvertService.claudeToOpenAIRequest(request, info);
        return convertOpenAIRequest(info, converted);
    }

    // ======================== 请求/响应 ========================

    @Override
    @SuppressWarnings("unchecked")
    public Object doRequest(RelayInfo info, Object requestBody) throws Exception {
        int relayMode = info.getRelayMode();

        // 客户端请求头（由 Handler 设置到 RelayInfo）
        Map<String, String> clientHeaders = info.getClientHeaders();
        if (clientHeaders == null) {
            clientHeaders = Collections.emptyMap();
        }

        // 将 requestBody 转换为 InputStream
        InputStream bodyStream;
        if (requestBody instanceof InputStream is) {
            bodyStream = is;
        } else if (requestBody instanceof byte[] bytes) {
            bodyStream = new java.io.ByteArrayInputStream(bytes);
        } else if (requestBody instanceof Reader reader) {
            java.io.CharArrayWriter cw = new java.io.CharArrayWriter();
            reader.transferTo(cw);
            bodyStream = new java.io.ByteArrayInputStream(
                    new String(cw.toCharArray()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else if (requestBody instanceof String str) {
            bodyStream = new java.io.ByteArrayInputStream(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("Unsupported request body type: "
                    + (requestBody != null ? requestBody.getClass().getName() : "null"));
        }

        // multipart/form（Audio 转录/翻译、Image 编辑）
        if (relayMode == RelayModeEnum.AUDIO_TRANSCRIPTION
                || relayMode == RelayModeEnum.AUDIO_TRANSLATION) {
            return ApiRequestExecutor.doFormRequest(this, info, bodyStream, clientHeaders, "POST");
        }

        // WebSocket（Realtime API）
        if (relayMode == RelayModeEnum.REALTIME) {
            return ApiRequestExecutor.doWssRequest(this, info, clientHeaders);
        }

        // 默认：标准 API 请求
        String method = "POST";
        // Images Generations 使用 GET 方法（部分渠道）
        // 其他 relayMode 默认 POST
        return ApiRequestExecutor.doApiRequest(this, info, bodyStream, clientHeaders, method);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        int relayMode = info.getRelayMode();
        HttpServletResponse response = info.getResponse();

        // 非流式响应
        if (!info.isStream()) {
            return handleNonStreamResponse(info, (HttpResponse<InputStream>) resp, response);
        }

        // 流式响应
        return handleStreamResponse(info, (HttpResponse<InputStream>) resp, response);
    }

    /**
     * 非流式响应处理      */
    private DoResponseResult handleNonStreamResponse(
            RelayInfo info, HttpResponse<InputStream> resp, HttpServletResponse response) throws Exception {

        // 读取响应体
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        String bodyStr = new String(responseBody, java.nio.charset.StandardCharsets.UTF_8);
        log.debug("upstream response body ({} bytes): {}", responseBody.length,
                bodyStr.length() > 500 ? bodyStr.substring(0, 500) + "..." : bodyStr);

        // 解析为 OpenAITextResponse
        OpenAITextResponse simpleResponse;
        try {
            simpleResponse = Convert.toJavaBean(bodyStr, OpenAITextResponse.class);
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt("Failed to parse upstream response: " + e.getMessage()));
        }

        // 检查 OpenAI 错误
        if (simpleResponse.getError() != null) {
            OpenAIError openAIError = Convert.toJavaBean(simpleResponse.getError(), OpenAIError.class);
            if (openAIError != null && openAIError.getType() != null && !openAIError.getType().isEmpty()) {
                RelayException apiError = newApiError(openAIError.getMessage() != null
                        ? openAIError.getMessage() : "upstream error",
                        openAIError.getType(), resp.statusCode(), false);
                return DoResponseResult.failure(apiError);
            }
        }

        // 检查 content_filter
        if (simpleResponse.getChoices() != null) {
            for (OpenAITextResponseChoice choice : simpleResponse.getChoices()) {
                if ("content_filter".equals(choice.getFinishReason())) {
                    log.warn("Upstream returned finish_reason=content_filter");
                }
            }
        }

        // 处理 Usage：如果 promptTokens==0，从响应文本估算
        yaoshu.token.pojo.dto.Usage usage = convertUsage(simpleResponse.getUsage());
        boolean usageModified = false;
        if (usage == null || usage.getPromptTokens() == 0) {
            int completionTokens = (usage != null) ? usage.getCompletionTokens() : 0;
            if (completionTokens == 0 && simpleResponse.getChoices() != null) {
                for (OpenAITextResponseChoice choice : simpleResponse.getChoices()) {
                    if (choice.getMessage() != null) {
                        Object content = choice.getMessage().getContent();
                        if (content instanceof String str) {
                            completionTokens += str.length() / 4; // 粗略估算
                        }
                    }
                }
            }
            int promptTokens = info.getEstimatePromptTokens();
            usage = new yaoshu.token.pojo.dto.Usage();
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usageModified = true;
        }

        // 写入 HttpServletResponse
        if (response != null) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            // 如果 usage 被修改，重写 JSON
            if (usageModified) {
                try {
                    Map<String, Object> bodyMap = Convert.toJSONObject(responseBody);
                    bodyMap.put("usage", usage);
                    byte[] modifiedBody = Convert.toJSONString(bodyMap).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    response.getOutputStream().write(modifiedBody);
                } catch (Exception e) {
                    // 回退：直接写入原始响应
                    response.getOutputStream().write(responseBody);
                }
            } else {
                response.getOutputStream().write(responseBody);
            }
            response.getOutputStream().flush();
        }

        return DoResponseResult.success(usage);
    }

    /**
     * 流式响应处理      */
    private DoResponseResult handleStreamResponse(
            RelayInfo info, HttpResponse<InputStream> resp, HttpServletResponse response) throws Exception {

        if (resp == null || resp.body() == null) {
            throw new ResultException(R.errorPrompt("invalid upstream response"));
        }

        // 设置 SSE 响应头
        if (response != null) {
            RelayCommonHelper.setEventStreamHeaders(response);
        }

        // 流状态变量
        String[] lastStreamData = {""};
        StringBuilder responseTextBuilder = new StringBuilder();
        int[] toolCount = {0};
        yaoshu.token.pojo.dto.Usage[] usageHolder = {new yaoshu.token.pojo.dto.Usage()};
        boolean[] containStreamUsage = {false};

        // 流扫描 + 数据回调
        StreamScanner.scan(resp.body(), info, data -> {
            // 处理上一条数据（延迟一行，Go 相同逻辑）
            if (!lastStreamData[0].isEmpty()) {
                OpenAIStreamHelper.handleStreamFormat(info, lastStreamData[0],
                        info.getChannelSetting() != null && Boolean.TRUE.equals(info.getChannelSetting().getForceFormat()),
                        info.getChannelSetting() != null && info.getChannelSetting().isThinkingToContent());
            }

            if (data != null && !data.isEmpty() && !"[DONE]".equals(data)) {
                lastStreamData[0] = data;
                // Token 累积
                OpenAIStreamHelper.processTokenData(info.getRelayMode(), data,
                        responseTextBuilder, toolCount);
            }
            return true; // 继续扫描
        }, response);

        // 处理最后一条流数据 + 提取 responseId/createAt/model/usage
        String[] responseIdHolder = {""};
        long[] createdAtHolder = {0};
        String[] systemFpHolder = {null};
        String[] modelHolder = {info.getUpstreamModelName()};
        boolean[] shouldSendLastResp = {true};

        OpenAIStreamHelper.handleLastResponse(lastStreamData[0], responseIdHolder, createdAtHolder,
                systemFpHolder, modelHolder, usageHolder, containStreamUsage, info, shouldSendLastResp);

        // 发送最后一条数据
        if (shouldSendLastResp[0] && "openai".equals(info.getRelayFormat())) {
            OpenAIStreamHelper.sendStreamData(info, lastStreamData[0],
                    info.getChannelSetting() != null && Boolean.TRUE.equals(info.getChannelSetting().getForceFormat()),
                    info.getChannelSetting() != null && info.getChannelSetting().isThinkingToContent());
        }

        // 如果没有从流中提取到 usage，估算
        yaoshu.token.pojo.dto.Usage usage = usageHolder[0];
        if (!containStreamUsage[0]) {
            int completionTokens = responseTextBuilder.length() / 4 + toolCount[0] * 7;
            usage = new yaoshu.token.pojo.dto.Usage();
            usage.setPromptTokens(info.getEstimatePromptTokens());
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(info.getEstimatePromptTokens() + completionTokens);
        }

        // 最终响应（usage chunk + [DONE]）
        OpenAIStreamHelper.handleFinalResponse(info, lastStreamData[0],
                responseIdHolder[0], createdAtHolder[0], modelHolder[0],
                systemFpHolder[0], usage, containStreamUsage[0]);

        return DoResponseResult.success(usage);
    }

    @Override
    public List<String> getModelList() {
        switch (this.channelType) {
            case ChannelConstants.CHANNEL_TYPE_360: return AI360Constant.MODEL_LIST;
            case ChannelConstants.CHANNEL_TYPE_XINFERENCE: return XinferenceConstant.MODEL_LIST;
            case ChannelConstants.CHANNEL_TYPE_OPENROUTER: return OpenRouterConstant.MODEL_LIST;
            default:
                return OpenAIChannelConstant.MODEL_LIST;
        }
    }

    @Override
    public String getChannelName() {
        switch (this.channelType) {
            case ChannelConstants.CHANNEL_TYPE_360: return AI360Constant.CHANNEL_NAME;
            case ChannelConstants.CHANNEL_TYPE_XINFERENCE: return XinferenceConstant.CHANNEL_NAME;
            case ChannelConstants.CHANNEL_TYPE_OPENROUTER: return OpenRouterConstant.CHANNEL_NAME;
            default:
                return OpenAIChannelConstant.CHANNEL_NAME;
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 转换 OpenAIResponseDTO.Usage → yaoshu.token.pojo.dto.Usage
     */
    private static yaoshu.token.pojo.dto.Usage convertUsage(OpenAIResponseDTO.Usage source) {
        if (source == null) return null;
        yaoshu.token.pojo.dto.Usage target = new yaoshu.token.pojo.dto.Usage();
        if (source.getPromptTokens() != null) target.setPromptTokens(source.getPromptTokens());
        if (source.getCompletionTokens() != null) target.setCompletionTokens(source.getCompletionTokens());
        if (source.getTotalTokens() != null) target.setTotalTokens(source.getTotalTokens());
        if (source.getPromptCacheHitTokens() != null) target.setPromptCacheHitTokens(source.getPromptCacheHitTokens());
        return target;
    }

    /** 判断是否为 o 系列推理模型 */
    static boolean isOModel(String model) {
        if (model == null) return false;
        return model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4");
    }

    /** 判断是否为 GPT-5 系列模型 */
    static boolean isGPT5Model(String model) {
        if (model == null) return false;
        return model.startsWith("gpt-5.");
    }
}
