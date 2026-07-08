package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.model.GeminiModelConfig;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.*;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

/**
 * Gemini 中转处理器  * <p>
 * 编排 Google AI / Vertex AI Gemini API 中转：
 * DeepCopy → ModelMapped → Thinking Preprocess → ConvertGeminiRequest
 * → RemoveDisabledFields → ParamOverride → DoRequest → DoResponse → 计费结算
 * <p>
 * 特殊处理：
 * <ul>
 * <li>thinking adapter enabled 时 -thinking / -nothinking suffix 处理</li>
 * <li>isNoThinkingRequest：ThinkingConfig.ThinkingBudget=0 → 非思考模式定价切换</li>
 * <li>trimModelThinking：去除 -nothinking / -thinking 后缀</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiHandler {    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * Gemini 中转编排入口      */
    public void geminiHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // 1. 类型断言
        if (!(info.getRequest() instanceof GeminiDTO.GeminiChatRequest geminiReq)) {
            throw CompatibleHandler.newApiError("invalid request type, expected GeminiDTO.GeminiChatRequest, got "
                    + (info.getRequest() != null ? info.getRequest().getClass().getName() : "null"),
                    "invalid_request", 400, true);
        }

        // 2. DeepCopy
        GeminiDTO.GeminiChatRequest request = RelayUtils.deepCopy(geminiReq, GeminiDTO.GeminiChatRequest.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request to GeminiDTO",
                    "invalid_request", 400, true);
        }

        // 3. ModelMappedHelper
        try {
            ModelMappedHelper.apply(info, null, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "channel_model_mapped_error", 400, true);
        }

        // 4. Thinking adapter preprocess
        applyGeminiThinkingAdapter(info, request);

        // 5. GetAdaptor + init
        IAdaptor adaptor = RelayAdaptor.getAdaptor(info.getApiType());
        if (adaptor == null) {
            throw CompatibleHandler.newApiError("invalid api type: " + info.getApiType(),
                    "invalid_api_type", 400, true);
        }
        adaptor.init(info);

        // 6. ConvertGeminiRequest
        Object convertedRequest;
        try {
            convertedRequest = adaptor.convertGeminiRequest(info, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

        // 7. JSON 序列化 + RemoveDisabledFields + ParamOverride + OutboundJSONBody
        byte[] jsonData;
        try {
            jsonData = Convert.toJSONString(convertedRequest).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "json_marshal_failed", 400, true);
        }

        ChannelOtherSettingsDTO otherSettings = info.getChannelOtherSettings();
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        boolean channelPassThrough = channelSetting != null
                && Boolean.TRUE.equals(channelSetting.getPassThroughBodyEnabled());
        jsonData = RelayUtils.removeDisabledFields(jsonData, otherSettings, channelPassThrough);

        if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
            try {
                jsonData = OverrideUtils.applyParamOverrideWithRelayInfo(jsonData, info);
            } catch (OverrideUtils.ParamOverrideReturnError e) {
                Object errResult = OverrideUtils.newApiErrorFromParamOverride(e);
                throw CompatibleHandler.newApiError(
                        CompatibleHandler.extractErrorMsg(errResult),
                        "channel_param_override_invalid",
                        CompatibleHandler.extractStatusCode(errResult), true);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "param_override_failed", 400, true);
            }
        }

        log.debug("gemini request body: {}", new String(jsonData));

        OutboundBodyHelper.OutboundBodyResult bodyResult;
        try {
            bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        info.setUpstreamRequestBodySize(bodyResult.getSize());

        // 8. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, bodyResult.getBody());
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        // 9. 响应处理
        processGeminiResponse(req, info, adaptor, rawResp);
    }

    /**
     * Gemini Thinking Adapter 预处理器      */
    private void applyGeminiThinkingAdapter(RelayInfo info, GeminiDTO.GeminiChatRequest request) {
        String model = info.getOriginModelName();
        if (model == null) return;

        GeminiModelConfig geminiConfig = GeminiModelConfig.getInstance();

        // 1. thinking adapter enabled 时的特殊处理
        if (geminiConfig.isThinkingAdapterEnabled()) {
            GeminiDTO.GeminiChatGenerationConfig genConfig = request.getGenerationConfig();
            if (genConfig != null && genConfig.getThinkingConfig() != null) {
                // 无思考请求：ThinkingBudget=0 → 追加 "-nothinking" 定价后缀
                Integer thinkingBudget = genConfig.getThinkingConfig().getThinkingBudget();
                if (thinkingBudget != null && thinkingBudget == 0) {
                    info.setOriginModelName(model + "-nothinking");
                    // 无实际思考内容 → 清除 thinkingConfig 避免 Gemini API 报错
                    genConfig.setThinkingConfig(null);
                }
            }
        }

        // 2. trimModelThinking：去除 -nothinking / -thinking 后缀保持上游模型名纯净
        if (model.endsWith("-nothinking")) {
            info.setUpstreamModelName(model.substring(0, model.length() - "-nothinking".length()));
        } else if (model.endsWith("-thinking")) {
            info.setUpstreamModelName(model.substring(0, model.length() - "-thinking".length()));
        } else {
            info.setUpstreamModelName(model);
        }
    }

    /**
     * Gemini 响应处理
     */
    private void processGeminiResponse(HttpServletRequest req, RelayInfo info,
                                        IAdaptor adaptor, Object rawResp) {
        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        if (rawResp instanceof java.net.http.HttpResponse<?> httpResp) {
            String contentType = httpResp.headers().firstValue("Content-Type").orElse("");
            info.setStream(info.isStream() || contentType.startsWith("text/event-stream"));

            if (httpResp.statusCode() != 200) {
                RelayException relayError = CompatibleHandler.handleHttpResponseError(httpResp, false);
                ErrorHandlingService.applyStatusCodeMapping(relayError, statusCodeMappingStr);
                throw relayError;
            }

            IAdaptor.DoResponseResult result;
            try {
                result = adaptor.doResponse(info, httpResp);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "do_response_failed", 500, false);
            }
            if (result != null && result.isError()) {
                ErrorHandlingService.applyStatusCodeMapping(result.getError(), statusCodeMappingStr);
                throw result.getError();
            }
            if (result != null && result.getUsage() != null) {
                CompatibleHandler.postConsumeQuota(quotaService, billingService, info, result.getUsage());
            }
        }
    }
}
