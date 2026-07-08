package yaoshu.token.relay.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.model.GlobalModelSettingConfig;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.*;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Chat Completions 文本/对话中转处理器  * <p>
 * 编排 Chat Completions API 中转——最核心的中转链路：
 * <ol>
 * <li>类型断言 dto.GeneralOpenAIRequest</li>
 * <li>DeepCopy 请求对象</li>
 * <li>ModelMappedHelper — 模型名映射</li>
 * <li>StreamOptions 处理（IncludeUsage + ForceStreamOption）</li>
 * <li>GetAdaptor + adaptor.Init — 获取并初始化适配器</li>
 * <li>PassThrough / Responses 模式判断</li>
 * <li>adaptor.ConvertRequest — 转换请求为上游格式 + SystemPrompt 注入</li>
 * <li>RemoveDisabledFields + ParamOverride — 请求体精修</li>
 * <li>adaptor.DoRequest — 发起 HTTP 请求</li>
 * <li>Stream 判断 + DoResponse 处理</li>
 * <li>PostTextConsumeQuota / PostAudioConsumeQuota — 计费结算</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibleHandler {    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    private final QuotaService quotaService;
    private final BillingService billingService;
    private final ChatViaResponsesHandler chatViaResponsesHandler;


    /** true */
    private static final boolean FORCE_STREAM_OPTION = true;

    /**
     * Chat Completions 中转编排入口      * <p>
     * 前置条件：info.InitChannelMeta 已在 DistributorFilter 中完成
     *
     * @param req  HttpServletRequest
     * @param resp HttpServletResponse
     * @param info Relay 上下文（含渠道/Token/用户/计费信息）
     */
    public void textHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // ====== 1. 类型断言 ======
        GeneralOpenAIRequest textReq;
        if (info.getRequest() instanceof GeneralOpenAIRequest g) {
            textReq = g;
        } else if (info.getRequest() instanceof Map) {
            // 兼容 parseRequestBody 暂存的 Map，转换为目标 DTO
            textReq = Convert.toJavaBean(info.getRequest(), GeneralOpenAIRequest.class);
        } else {
            throw newApiError("invalid request type, expected GeneralOpenAIRequest, got "
                    + (info.getRequest() != null ? info.getRequest().getClass().getName() : "null"),
                    "invalid_request", 400, true);
        }

        // ====== 2. DeepCopy 请求对象 ======
        GeneralOpenAIRequest request = RelayUtils.deepCopy(textReq, GeneralOpenAIRequest.class);
        if (request == null) {
            throw newApiError("failed to copy request to GeneralOpenAIRequest",
                    "invalid_request", 400, true);
        }

        // ====== 3. WebSearchOptions 上下文 ======
        if (request.getWebSearchOptions() != null) {
            String searchContextSize = request.getWebSearchOptions().getSearchContextSize();
            if (searchContextSize != null && !searchContextSize.isEmpty()) {
                info.getExtraData().put("chat_completion_web_search_context_size", searchContextSize);
            }
        }

        // ====== 4. ModelMappedHelper — 模型名映射 ======
        try {
            // modelMappingJson 从请求参数或 distributor context 获取，当前传 null（无映射）
            ModelMappedHelper.apply(info, null, request);
        } catch (Exception e) {
            throw newApiError(e, "channel_model_mapped_error", 400, true);
        }

        // ====== 5. StreamOptions：IncludeUsage 提取 ======
        boolean includeUsage = extractIncludeUsage(request.getStreamOptions());

        // ====== 6. StreamOptions 兼容性处理 ======
        if (!info.isSupportStreamOptions() || !request.isStream()) {
            request.setStreamOptions(null);
        } else if (FORCE_STREAM_OPTION) {
            // 强制返回 usage：若支持 StreamOptions 且请求为 stream，设置 IncludeUsage=true
            Map<String, Boolean> forcedOptions = new LinkedHashMap<>();
            forcedOptions.put("include_usage", true);
            request.setStreamOptions(forcedOptions);
        }

        info.setShouldIncludeUsage(includeUsage);

        // ====== 7. GetAdaptor + adaptor.Init ======
        IAdaptor adaptor = RelayAdaptor.getAdaptor(info.getApiType());
        if (adaptor == null) {
            throw newApiError("invalid api type: " + info.getApiType(),
                    "invalid_api_type", 400, true);
        }
        adaptor.init(info);

        boolean passThroughGlobal = GlobalModelSettingConfig.getInstance().isPassThroughRequestEnabled();
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        boolean channelPassThrough = channelSetting != null
                && Boolean.TRUE.equals(channelSetting.getPassThroughBodyEnabled());

        // ====== 8. Responses 模式短路 ======
        if (info.getRelayMode() == RelayModeEnum.CHAT_COMPLETIONS
                && !passThroughGlobal
                && !channelPassThrough
                && shouldChatCompletionsUseResponsesGlobal(info)) {
            applySystemPrompt(request, info);
            try {
                info.setClientHeaders(extractClientHeaders(req));
                info.setResponse(resp);
                Usage usage = chatViaResponsesHandler.chatCompletionsViaResponses(req, resp, request, info, adaptor);
                if (usage != null) {
                    postConsumeQuota(quotaService, billingService, info, usage);
                }
                return;
            } catch (RelayException e) {
                throw e;
            } catch (Exception e) {
                throw newApiError(e, "do_response_failed", 500, false);
            }
        }

        // ====== 9. 构建上游请求体 ======
        InputStream requestBody;

        if (passThroughGlobal || channelPassThrough) {
            // PassThrough 模式：使用原始请求体
            requestBody = buildPassThroughBody(req, info);
        } else {
            // 正常模式：转换请求 + SystemPrompt + 精修
            requestBody = buildConvertedBody(info, adaptor, request);
        }

        // ====== 10. DoRequest — 发起 HTTP 请求 ======
        Object rawResp;
        try {
            // 设置请求上下文
            info.setClientHeaders(extractClientHeaders(req));
            info.setResponse(resp);
            rawResp = adaptor.doRequest(info, requestBody);
        } catch (Exception e) {
            throw newApiError(e, "do_request_failed", 500, false);
        }

        if (rawResp == null) {
            throw newApiError("doRequest returned null response",
                    "do_request_failed", 500, false);
        }

        // ====== 11. Stream 检测 + 状态码校验 ======
        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        if (!(rawResp instanceof HttpResponse<?> httpResp)) {
            // 非标准 HTTP 响应（如 WebSocket），直接交给 DoResponse
            IAdaptor.DoResponseResult result;
            try {
                result = adaptor.doResponse(info, null);
            } catch (Exception e) {
                throw newApiError(e, "do_response_failed", 500, false);
            }
            if (result != null && result.isError()) {
                handleDoResponseError(result.getError(), statusCodeMappingStr);
            }
            // 非 HTTP 响应由 adaptor 自行写入 resp，此处仅计费
            if (result != null && result.getUsage() != null) {
                postConsumeQuota(quotaService, billingService, info, result.getUsage());
            }
            return;
        }

        // Stream 检测：Content-Type 以 text/event-stream 开头
        String contentType = httpResp.headers().firstValue("Content-Type").orElse("");
        info.setStream(info.isStream() || contentType.startsWith("text/event-stream"));

        // 状态码校验
        if (httpResp.statusCode() != 200) {
            RelayException relayError = handleHttpResponseError(httpResp, false);
            ErrorHandlingService.applyStatusCodeMapping(relayError, statusCodeMappingStr);
            throw relayError;
        }

        // ====== 12. DoResponse — 处理上游响应 ======
        IAdaptor.DoResponseResult result;
        try {
            result = adaptor.doResponse(info, httpResp);
        } catch (Exception e) {
            throw newApiError(e, "do_response_failed", 500, false);
        }

        if (result == null) {
            throw newApiError("doResponse returned null",
                    "do_response_failed", 500, false);
        }
        if (result.isError()) {
            handleDoResponseError(result.getError(), statusCodeMappingStr);
        }

        // ====== 13. 计费结算 ======
        if (result.getUsage() != null) {
            postConsumeQuota(quotaService, billingService, info, result.getUsage());
        }
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 从 streamOptions（Object 类型）中提取 include_usage 值
     */
    @SuppressWarnings("unchecked")
    private boolean extractIncludeUsage(Object streamOptions) {
        if (streamOptions == null) return true;
        if (streamOptions instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) streamOptions;
            Object val = map.get("include_usage");
            if (val instanceof Boolean b) return b;
        }
        return true;
    }

    /**
     * PassThrough 模式：读取原始请求体
     * <p>
     * 通过 HttpServletRequest.getInputStream() 读取（yue-library enabled-repeatedly-read-servlet-request
     * 已确保可重复读取）
     */
    private InputStream buildPassThroughBody(HttpServletRequest req, RelayInfo info) {
        try {
            return req.getInputStream();
        } catch (Exception e) {
            throw newApiError(e, "read_request_body_failed", 400, true);
        }
    }

    /**
     * 正常模式：构建转换后的上游请求体
     * <p>
     * 流程：ConvertOpenAIRequest → SystemPrompt 注入 → JSON 序列化
     * → RemoveDisabledFields → ParamOverride → OutboundJSONBody
     */
    private InputStream buildConvertedBody(RelayInfo info, IAdaptor adaptor, GeneralOpenAIRequest request) {
        // 9a. ConvertRequest
        Object convertedRequest;
        try {
            convertedRequest = adaptor.convertOpenAIRequest(info, request);
        } catch (Exception e) {
            throw newApiError(e, "convert_request_failed", 400, true);
        }

        // 9b. 追加格式转换链
        RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

        // 9c. SystemPrompt 注入（渠道级别）
        applySystemPrompt(convertedRequest, info);

        // 9d. JSON 序列化
        byte[] jsonData;
        try {
            jsonData = Convert.toJSONString(convertedRequest).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw newApiError(e, "json_marshal_failed", 400, true);
        }

        // 9e. RemoveDisabledFields
        ChannelOtherSettingsDTO otherSettings = info.getChannelOtherSettings();
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        boolean channelPassThrough = channelSetting != null
                && Boolean.TRUE.equals(channelSetting.getPassThroughBodyEnabled());
        jsonData = RelayUtils.removeDisabledFields(jsonData, otherSettings, channelPassThrough);

        // 9f. ParamOverride
        if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
            try {
                jsonData = OverrideUtils.applyParamOverrideWithRelayInfo(jsonData, info);
            } catch (OverrideUtils.ParamOverrideReturnError e) {
                Object errorResult = OverrideUtils.newApiErrorFromParamOverride(e);
                @SuppressWarnings("unchecked")
                Map<String, Object> errMap = (Map<String, Object>) errorResult;
                String message = (String) ((Map<String, Object>) errMap.get("error")).get("message");
                int statusCode = (int) errMap.get("status_code");
                throw newApiError(message != null ? message : "request blocked by param override",
                        "channel_param_override_invalid", statusCode, true);
            } catch (Exception e) {
                throw newApiError(e, "param_override_failed", 400, true);
            }
        }

        log.debug("text request body: {}", new String(jsonData));

        // 9g. OutboundJSONBody
        OutboundBodyHelper.OutboundBodyResult bodyResult;
        try {
            bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
        } catch (Exception e) {
            throw newApiError(e, "convert_request_failed", 400, true);
        }

        info.setUpstreamRequestBodySize(bodyResult.getSize());
        return bodyResult.getBody();
    }

    /**
     * 渠道 SystemPrompt 注入      * <p>
     * 支持两种模式：
     * <ol>
     * <li>无 SystemPrompt 时追加一条 system message 在 messages 头部</li>
     * <li>SystemPromptOverride 时覆盖/拼接已有 system message</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private void applySystemPrompt(Object convertedRequest, RelayInfo info) {
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        if (channelSetting == null) return;

        String systemPrompt = channelSetting.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isEmpty()) return;

        if (!(convertedRequest instanceof GeneralOpenAIRequest openAIRequest)) return;
        List<GeneralOpenAIRequest.Message> messages = openAIRequest.getMessages();
        if (messages == null) return;

        boolean containSystemPrompt = false;
        for (GeneralOpenAIRequest.Message message : messages) {
            if ("system".equals(message.getRole())) {
                containSystemPrompt = true;
                break;
            }
        }

        if (!containSystemPrompt) {
            // 无系统提示 → 在头部追加 system message
            GeneralOpenAIRequest.Message systemMessage = GeneralOpenAIRequest.Message.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build();
            messages.add(0, systemMessage);
        } else if (Boolean.TRUE.equals(channelSetting.getSystemPromptOverride())) {
            // SystemPromptOverride → 拼接/覆盖已有 system message
            for (GeneralOpenAIRequest.Message message : messages) {
                if ("system".equals(message.getRole())) {
                    if (message.getContent() instanceof String strContent) {
                        message.setContent(systemPrompt + "\n" + strContent);
                    } else if (message.getContent() instanceof List) {
                        // 多媒体 content 数组 → 在头部插入 text content
                        List<Map<String, Object>> contents = (List<Map<String, Object>>) message.getContent();
                        Map<String, Object> textContent = new LinkedHashMap<>();
                        textContent.put("type", "text");
                        textContent.put("text", systemPrompt);
                        contents.add(0, textContent);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 处理上游 HTTP 错误响应，构造 RelayException（供所有 Handler 共享）
     * <p>
     * 当前直接处理 java.net.http.HttpResponse，无需 urlconnection 适配
     */
    public static RelayException handleHttpResponseError(HttpResponse<?> httpResp, boolean showBodyWhenFail) {
        int statusCode = httpResp.statusCode();
        String body = "";
        try {
            if (httpResp.body() instanceof String s) {
                body = s;
            }
        } catch (Exception e) {
            log.debug("读取响应体失败", e);
        }

        String errorType = mapStatusToType(statusCode);
        String errorMessage = extractErrorMessage(body, statusCode);

        if (showBodyWhenFail && !body.isEmpty()) {
            String displayBody = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            errorMessage = "upstream returned " + statusCode + ": " + displayBody;
        }

        if (statusCode >= 500) {
            log.error("Relay upstream 5xx: status={}, body={}", statusCode, body.length() > 200 ? body.substring(0, 200) : body);
        } else {
            log.debug("Relay upstream error: status={}, body={}", statusCode, body.length() > 200 ? body.substring(0, 200) : body);
        }

        return newApiErrorWithoutStackTrace(errorMessage, errorType, statusCode, false);
    }

    /**
     * 处理 DoResponse 返回的错误
     */
    private void handleDoResponseError(RelayException error, String statusCodeMappingStr) {
        if (error == null) return;
        ErrorHandlingService.applyStatusCodeMapping(error, statusCodeMappingStr);
        throw error;
    }

    /**
     * 从错误 body 中提取人类可读的错误消息
     */
    private static String extractErrorMessage(String body, int statusCode) {
        if (body == null || body.isEmpty()) {
            return "upstream returned status " + statusCode;
        }
        try {
            Map<String, Object> json = Convert.toJSONObject(body);
            Object errorObj = json.get("error");
            if (errorObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                Object message = errorMap.get("message");
                if (message != null) return message.toString();
            }
            Object message = json.get("message");
            if (message != null) return message.toString();
        } catch (Exception e) {
            log.debug("解析错误响应 JSON 失败，状态码: {}", statusCode, e);
        }
        return "upstream returned status " + statusCode;
    }

    /**
     * 从 HttpServletRequest 提取所有请求头为 Map（用于 Header Override / 透传）
     */
    private static Map<String, String> extractClientHeaders(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        java.util.Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, req.getHeader(name));
        }
        return headers;
    }

    /**
     * 将 HTTP 状态码映射为错误类型字符串
     */
    private static String mapStatusToType(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return "authentication_error";
        if (statusCode == 429) return "rate_limit_error";
        if (statusCode >= 500) return "upstream_error";
        return "api_error";
    }

    /**
     * 计费结算：根据 usage 选择文本/音频计费路径（供所有 Handler 共享）
     */
    @SuppressWarnings("unchecked")
    public static void postConsumeQuota(QuotaService quotaService, BillingService billingService,
                                         RelayInfo info, Object usageObj) {
        int actualQuota;
        Usage usageForLog;
        PriceData priceData = info.getPriceData();
        if (usageObj instanceof Usage usage) {
            usageForLog = usage;
            if (hasAudioTokens(usage) && hasAudioRatios(priceData)) {
                actualQuota = quotaService.calculateAudioQuota(
                        usage.getPromptTokensDetails() != null ? usage.getPromptTokensDetails().getTextTokens() : 0,
                        usage.getCompletionTokenDetails() != null ? usage.getCompletionTokenDetails().getTextTokens() : 0,
                        usage.getPromptTokensDetails() != null ? usage.getPromptTokensDetails().getAudioTokens() : 0,
                        usage.getCompletionTokenDetails() != null ? usage.getCompletionTokenDetails().getAudioTokens() : 0,
                        priceData);
            } else {
                // 缓存感知计价：缓存命中部分按 cacheRatio 折扣
                actualQuota = quotaService.calculateTextQuotaWithCache(info, usage);
            }
        } else if (usageObj instanceof Map) {
            Map<String, Object> usageMap = (Map<String, Object>) usageObj;
            // 从 Map 统一构造 Usage（供计费与日志记录共用）
            Usage usageFromMap = new Usage();
            // Claude 格式兼容：input_tokens / output_tokens
            int promptTokens = toInt(usageMap.get("prompt_tokens"));
            boolean isClaudeFormat = false;
            if (promptTokens == 0) {
                promptTokens = toInt(usageMap.get("input_tokens"));
                if (promptTokens > 0) {
                    isClaudeFormat = true;
                }
            }
            usageFromMap.setPromptTokens(promptTokens);
            int completionTokens = toInt(usageMap.get("completion_tokens"));
            if (completionTokens == 0) {
                completionTokens = toInt(usageMap.get("output_tokens"));
            }
            usageFromMap.setCompletionTokens(completionTokens);
            int cachedTokens = toNestedInt(usageMap, "prompt_tokens_details", "cached_tokens");
            int cachedCreationTokens = toNestedInt(usageMap, "prompt_tokens_details", "cached_creation_tokens");
            // Claude 格式：cache_read_input_tokens / cache_creation_input_tokens
            if (cachedTokens == 0) {
                cachedTokens = toInt(usageMap.get("cache_read_input_tokens"));
            }
            if (cachedCreationTokens == 0) {
                cachedCreationTokens = toInt(usageMap.get("cache_creation_input_tokens"));
            }
            if (isClaudeFormat) {
                usageFromMap.setUsageSemantic("anthropic");
            }
            if (cachedTokens > 0 || cachedCreationTokens > 0) {
                usageFromMap.setPromptTokensDetails(new Usage.PromptTokensDetails());
                usageFromMap.getPromptTokensDetails().setCachedTokens(cachedTokens);
                usageFromMap.getPromptTokensDetails().setCachedCreationTokens(cachedCreationTokens);
            }
            usageForLog = usageFromMap;
            if (hasAudioTokens(usageMap) && hasAudioRatios(priceData)) {
                actualQuota = quotaService.calculateAudioQuota(
                        toNestedInt(usageMap, "prompt_tokens_details", "text_tokens"),
                        toNestedInt(usageMap, "completion_token_details", "text_tokens"),
                        toNestedInt(usageMap, "prompt_tokens_details", "audio_tokens"),
                        toNestedInt(usageMap, "completion_token_details", "audio_tokens"),
                        priceData);
            } else {
                actualQuota = quotaService.calculateTextQuotaWithCache(info, usageFromMap);
            }
        } else {
            log.warn("Unsupported usage type: {}", usageObj != null ? usageObj.getClass().getName() : "null");
            return;
        }

        // 性能采样：将 outputTokens 写入 info 供 RelayController 成功路径采样
        info.setPerfOutputTokens(usageForLog.getCompletionTokens());

        // 计费结算 + 记录主消费日志（含 cached_tokens / key_index）
        billingService.settleBillingAndLog(info, actualQuota, usageForLog);
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static int toNestedInt(Map<String, Object> map, String parentKey, String childKey) {
        Object nested = map.get(parentKey);
        if (nested instanceof Map<?, ?> nestedMap) {
            return toInt(((Map<String, Object>) nestedMap).get(childKey));
        }
        return 0;
    }

    private static boolean hasAudioTokens(Usage usage) {
        if (usage == null) {
            return false;
        }
        int promptAudioTokens = usage.getPromptTokensDetails() != null
                ? usage.getPromptTokensDetails().getAudioTokens() : 0;
        int completionAudioTokens = usage.getCompletionTokenDetails() != null
                ? usage.getCompletionTokenDetails().getAudioTokens() : 0;
        return promptAudioTokens > 0 || completionAudioTokens > 0;
    }

    private static boolean hasAudioTokens(Map<String, Object> usageMap) {
        return toNestedInt(usageMap, "prompt_tokens_details", "audio_tokens") > 0
                || toNestedInt(usageMap, "completion_token_details", "audio_tokens") > 0;
    }

    private static boolean hasAudioRatios(PriceData priceData) {
        if (priceData == null) {
            return false;
        }
        return Double.compare(priceData.getAudioRatio(), 1.0) != 0
                || Double.compare(priceData.getAudioCompletionRatio(), 1.0) != 0;
    }

    private boolean shouldChatCompletionsUseResponsesGlobal(RelayInfo info) {
        GlobalModelSettingConfig.ChatCompletionsToResponsesPolicy policy =
                GlobalModelSettingConfig.getInstance().getChatCompletionsToResponsesPolicy();
        if (policy == null || !policy.isChannelEnabled(info.getChannelId(), info.getChannelType())) {
            return false;
        }
        return matchAnyRegex(policy.getModelPatterns(), info.getOriginModelName());
    }

    private boolean matchAnyRegex(List<String> patterns, String value) {
        if (patterns == null || patterns.isEmpty() || value == null || value.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            try {
                Pattern compiled = REGEX_CACHE.computeIfAbsent(pattern, Pattern::compile);
                if (compiled.matcher(value).find()) {
                    return true;
                }
            } catch (Exception e) {
                // 非法正则按不匹配处理，保持运行时流量可继续。
                log.warn("非法正则模式，按不匹配处理: {}", pattern, e);
            }
        }
        return false;
    }

    // ======================== 共享工具方法 ========================

    /**
     * 从 ParamOverrideReturnError 结果中提取错误消息
     */
    @SuppressWarnings("unchecked")
    public static String extractErrorMsg(Object errorResult) {
        if (errorResult instanceof Map) {
            Map<String, Object> errMap = (Map<String, Object>) errorResult;
            Object error = errMap.get("error");
            if (error instanceof Map) {
                Object msg = ((Map<String, Object>) error).get("message");
                if (msg != null) return msg.toString();
            }
        }
        return "request blocked by param override";
    }

    /**
     * 从 ParamOverrideReturnError 结果中提取状态码
     */
    @SuppressWarnings("unchecked")
    public static int extractStatusCode(Object errorResult) {
        if (errorResult instanceof Map) {
            Map<String, Object> errMap = (Map<String, Object>) errorResult;
            Object sc = errMap.get("status_code");
            if (sc instanceof Number n) return n.intValue();
        }
        return 400;
    }

    // ======================== 错误工厂方法（所有 Handler 共享） ========================

    public static RelayException newApiError(String message, String errorCode, int statusCode, boolean skipRetry) {
        RelayException error = new RelayException(message, errorCode);
        error.setStatusCode(statusCode);
        error.setSkipRetry(skipRetry);
        return error;
    }

    public static RelayException newApiError(Throwable cause, String errorCode, int statusCode, boolean skipRetry) {
        RelayException error = new RelayException(cause, errorCode);
        error.setStatusCode(statusCode);
        error.setSkipRetry(skipRetry);
        return error;
    }

    /**
     * 构造不带 stack trace 的轻量 RelayException（避免大对象开销）
     */
    private static RelayException newApiErrorWithoutStackTrace(String message, String errorCode, int statusCode, boolean skipRetry) {
        RelayException error = newApiError(message, errorCode, statusCode, skipRetry);
        // 清空 stack trace 以减少内存开销（上游错误栈无意义）
        error.setStackTrace(new StackTraceElement[0]);
        return error;
    }
}
