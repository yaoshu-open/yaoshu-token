package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.ChannelConstants;
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

/**
 * Responses API 中转处理器  * <p>
 * 编排 OpenAI Responses API 中转：类型断言 → DeepCopy → ModelMapped → ConvertResponsesRequest
 * → RemoveDisabledFields → ParamOverride → DoRequest → DoResponse → 计费结算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponsesHandler {    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * Responses API 中转编排入口      */
    public void responsesHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // 1. Compact 模式 API 类型校验
        if (info.getRelayMode() == RelayModeEnum.RESPONSES_COMPACT) {
            int apiType = info.getApiType();
            // OpenAI / Codex 以外的类型拒绝（Go: compact 仅限 OpenAI/Codex）
            // info.getApiType() 实际返回 channelType（CHANNEL_TYPE_* 编码，OpenAI=1, Codex=57），
            // 与 ApiTypeEnum.code（OpenAI=0, Codex=34）是不同编码体系，禁止混用。
            if (apiType != ChannelConstants.CHANNEL_TYPE_OPENAI
                    && apiType != ChannelConstants.CHANNEL_TYPE_CODEX) {
                throw CompatibleHandler.newApiError(
                        "unsupported endpoint /v1/responses/compact for api type " + info.getApiType(),
                        "invalid_request", 400, true);
            }
        }

        // 2. 类型断言
        OpenAIResponsesRequest responsesReq = (OpenAIResponsesRequest) info.getRequest();
        if (responsesReq == null) {
            throw CompatibleHandler.newApiError("request is null", "invalid_request", 400, true);
        }

        // 3. DeepCopy
        OpenAIResponsesRequest request = RelayUtils.deepCopy(responsesReq, OpenAIResponsesRequest.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request",
                    "invalid_request", 400, true);
        }

        // 4. ModelMappedHelper
        try {
            ModelMappedHelper.apply(info, null, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "channel_model_mapped_error", 400, true);
        }

        // 5. GetAdaptor + init
        IAdaptor adaptor = RelayAdaptor.getAdaptor(info.getApiType());
        if (adaptor == null) {
            throw CompatibleHandler.newApiError("invalid api type: " + info.getApiType(),
                    "invalid_api_type", 400, true);
        }
        adaptor.init(info);

        // 6. PassThrough / ConvertResponsesRequest
        boolean passThroughGlobal = GlobalModelSettingConfig.getInstance().isPassThroughRequestEnabled();
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        boolean channelPassThrough = channelSetting != null
                && Boolean.TRUE.equals(channelSetting.getPassThroughBodyEnabled());

        if (passThroughGlobal || channelPassThrough) {
            // PassThrough：使用原始请求体
            Object rawResp;
            try {
                rawResp = adaptor.doRequest(info, req.getInputStream());
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
            }
            processResponsesResponse(req, info, adaptor, rawResp);
            return;
        }

        // 7. ConvertResponsesRequest
        Object convertedRequest;
        try {
            convertedRequest = adaptor.convertOpenAIResponsesRequest(info, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

        // 8. JSON 序列化
        byte[] jsonData;
        try {
            jsonData = Convert.toJSONString(convertedRequest).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "json_marshal_failed", 400, true);
        }

        // 9. RemoveDisabledFields
        ChannelOtherSettingsDTO otherSettings = info.getChannelOtherSettings();
        jsonData = RelayUtils.removeDisabledFields(jsonData, otherSettings, channelPassThrough);

        // 10. ParamOverride
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

        log.debug("responses request body: {}", new String(jsonData));

        // 11. OutboundJSONBody
        OutboundBodyHelper.OutboundBodyResult bodyResult;
        try {
            bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        info.setUpstreamRequestBodySize(bodyResult.getSize());

        // 12. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, bodyResult.getBody());
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        processResponsesResponse(req, info, adaptor, rawResp);
    }

    /**
     * Responses 响应处理（状态码校验 + DoResponse + 计费）
     */
    private void processResponsesResponse(HttpServletRequest req, RelayInfo info,
                                           IAdaptor adaptor, Object rawResp) {
        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        if (rawResp instanceof java.net.http.HttpResponse<?> httpResp) {
            // Stream 检测
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
