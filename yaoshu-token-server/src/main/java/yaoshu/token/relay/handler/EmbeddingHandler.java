package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.*;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

/**
 * Embedding 向量化中转处理器  * <p>
 * 编排 Embedding API 中转：DeepCopy → ModelMapped → ConvertEmbeddingRequest
 * → ParamOverride → DoRequest → DoResponse → 计费结算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingHandler {    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * Embedding 中转编排入口      */
    public void embeddingHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // 1. 类型断言
        if (!(info.getRequest() instanceof EmbeddingDTO embeddingReq)) {
            throw CompatibleHandler.newApiError("invalid request type, expected EmbeddingDTO, got "
                    + (info.getRequest() != null ? info.getRequest().getClass().getName() : "null"),
                    "invalid_request", 400, true);
        }

        // 2. DeepCopy
        EmbeddingDTO request = RelayUtils.deepCopy(embeddingReq, EmbeddingDTO.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request to EmbeddingDTO",
                    "invalid_request", 400, true);
        }

        // 3. ModelMappedHelper
        try {
            ModelMappedHelper.apply(info, null, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "channel_model_mapped_error", 400, true);
        }

        // 4. GetAdaptor + init
        IAdaptor adaptor = RelayAdaptor.getAdaptor(info.getApiType());
        if (adaptor == null) {
            throw CompatibleHandler.newApiError("invalid api type: " + info.getApiType(),
                    "invalid_api_type", 400, true);
        }
        adaptor.init(info);

        // 5. ConvertEmbeddingRequest
        Object convertedRequest;
        try {
            convertedRequest = adaptor.convertEmbeddingRequest(info, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

        // 6. JSON 序列化 + ParamOverride + OutboundJSONBody
        byte[] jsonData;
        try {
            jsonData = Convert.toJSONString(convertedRequest).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "json_marshal_failed", 400, true);
        }

        if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
            try {
                jsonData = OverrideUtils.applyParamOverrideWithRelayInfo(jsonData, info);
            } catch (OverrideUtils.ParamOverrideReturnError e) {
                throw CompatibleHandler.newApiError(
                        CompatibleHandler.extractErrorMsg(OverrideUtils.newApiErrorFromParamOverride(e)),
                        "channel_param_override_invalid",
                        CompatibleHandler.extractStatusCode(OverrideUtils.newApiErrorFromParamOverride(e)), true);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "param_override_failed", 400, true);
            }
        }

        log.debug("converted embedding request body: {}", new String(jsonData));

        OutboundBodyHelper.OutboundBodyResult bodyResult;
        try {
            bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        info.setUpstreamRequestBodySize(bodyResult.getSize());

        // 7. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, bodyResult.getBody());
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        // 8. 状态码校验
        if (rawResp instanceof java.net.http.HttpResponse<?> httpResp) {
            if (httpResp.statusCode() != 200) {
                RelayException relayError = CompatibleHandler.handleHttpResponseError(httpResp, false);
                ErrorHandlingService.applyStatusCodeMapping(relayError, statusCodeMappingStr);
                throw relayError;
            }
            // 9. DoResponse
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
            // 10. 计费结算
            if (result != null && result.getUsage() != null) {
                CompatibleHandler.postConsumeQuota(quotaService, billingService, info, result.getUsage());
            }
        } else {
            // 非 HTTP 响应由 adaptor 自行处理
            IAdaptor.DoResponseResult result;
            try {
                result = adaptor.doResponse(info, null);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "do_response_failed", 500, false);
            }
            if (result != null && result.getUsage() != null) {
                CompatibleHandler.postConsumeQuota(quotaService, billingService, info, result.getUsage());
            }
        }
    }
}
