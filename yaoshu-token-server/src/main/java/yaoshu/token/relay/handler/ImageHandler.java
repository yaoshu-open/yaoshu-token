package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import java.nio.charset.StandardCharsets;
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
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

import java.io.InputStream;

/**
 * 图像生成中转处理器  * <p>
 * 编排图像生成 API 中转（DALL-E / Midjourney / Stable Diffusion 等）：
 * DeepCopy → ModelMapped → ConvertImageRequest → ParamOverride → DoRequest → DoResponse → 计费结算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageHandler {    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * 图像生成中转编排入口      */
    public void imageHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // 1. 请求对象获取
        OpenAIImageDTO imageReq = (OpenAIImageDTO) info.getRequest();
        if (imageReq == null) {
            throw CompatibleHandler.newApiError("request is null", "invalid_request", 400, true);
        }

        // 2. DeepCopy
        OpenAIImageDTO request = RelayUtils.deepCopy(imageReq, OpenAIImageDTO.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request",
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

        // 5. 构建上游请求体（PassThrough / ConvertImage）
        InputStream requestBody;
        boolean passThroughGlobal = GlobalModelSettingConfig.getInstance().isPassThroughRequestEnabled();
        ChannelSettingsDTO channelSetting = info.getChannelSetting();
        boolean channelPassThrough = channelSetting != null
                && Boolean.TRUE.equals(channelSetting.getPassThroughBodyEnabled());

        if (passThroughGlobal || channelPassThrough) {
            try {
                requestBody = req.getInputStream();
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "read_request_body_failed", 400, true);
            }
        } else {
            Object convertedRequest;
            try {
                convertedRequest = adaptor.convertImageRequest(info, request);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
            }
            RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

            // Go: 若转换为 BytesBuffer 则直接用作 Reader；否则 JSON 序列化
            byte[] jsonData;
            if (convertedRequest instanceof InputStream) {
                // 二进制内容直接透传
                requestBody = (InputStream) convertedRequest;
                // 跳过 JSON 处理
                // 7. DoRequest
                Object rawResp;
                try {
                    rawResp = adaptor.doRequest(info, requestBody);
                } catch (Exception e) {
                    throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
                }
                handleImageResponse(req, info, adaptor, rawResp);
                return;
            }

            try {
                jsonData = Convert.toJSONString(convertedRequest).getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "json_marshal_failed", 400, true);
            }

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

            log.debug("image request body: {}", new String(jsonData));

            OutboundBodyHelper.OutboundBodyResult bodyResult;
            try {
                bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
            }
            info.setUpstreamRequestBodySize(bodyResult.getSize());
            requestBody = bodyResult.getBody();
        }

        // 6. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, requestBody);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        handleImageResponse(req, info, adaptor, rawResp);
    }

    /**
     * 图像响应处理（状态码校验 + DoResponse + 计费）
     */
    private void handleImageResponse(HttpServletRequest req, RelayInfo info,
                                      IAdaptor adaptor, Object rawResp) {
        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        if (rawResp instanceof java.net.http.HttpResponse<?> httpResp) {
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
