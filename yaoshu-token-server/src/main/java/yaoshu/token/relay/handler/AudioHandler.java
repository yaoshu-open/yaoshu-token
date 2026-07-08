package yaoshu.token.relay.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.*;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

import java.io.Reader;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 音频中转处理器  * <p>
 * 编排音频 API 中转（TTS / Speech / Transcription / Translation）：
 * DeepCopy → ModelMapped → ConvertAudioRequest → DoRequest → DoResponse → 计费结算
 * <p>
 * Transcription/Translation 使用 multipart/form-data，请求体直接透传原始字节流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioHandler {

    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * 音频中转编排入口      */
    @SuppressWarnings("unchecked")
    public void audioHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        int relayMode = info.getRelayMode();
        boolean isMultipart = relayMode == RelayModeEnum.AUDIO_TRANSCRIPTION
                || relayMode == RelayModeEnum.AUDIO_TRANSLATION;

        // 1. 构建 AudioDTO
        AudioDTO audioReq = resolveAudioRequest(info, req, isMultipart);

        // 2. DeepCopy
        AudioDTO request = RelayUtils.deepCopy(audioReq, AudioDTO.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request to AudioDTO",
                    "invalid_request", 400, true);
        }

        // 3. TTS: 更新 model 为上游模型名（model mapping 后）
        if (!isMultipart && info.getRequest() instanceof Map) {
            Map<String, Object> requestMap = (Map<String, Object>) info.getRequest();
            if (info.getUpstreamModelName() != null && !info.getUpstreamModelName().isEmpty()) {
                requestMap.put("model", info.getUpstreamModelName());
            }
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

        // 6. 设置 clientHeaders + response（doRequest/doResponse 依赖）
        info.setClientHeaders(extractClientHeaders(req));
        info.setResponse(resp);

        // 7. ConvertAudioRequest（设置 adaptor.responseFormat；TTS 返回 JSON Reader）
        Reader ioReader;
        try {
            ioReader = adaptor.convertAudioRequest(info, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }

        // 8. 构建上游请求体
        Object requestBody;
        if (isMultipart) {
            // multipart/form-data: 直接透传原始请求体字节（CachingBodyFilter 确保可重复读取）
            try {
                requestBody = req.getInputStream().readAllBytes();
            } catch (Exception e) {
                throw CompatibleHandler.newApiError(e, "read_request_body_failed", 400, true);
            }
        } else {
            // TTS: 使用 convertAudioRequest 返回的 JSON Reader
            requestBody = ioReader;
        }

        // 9. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, requestBody);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        String statusCodeMappingStr = (String) req.getAttribute("status_code_mapping");

        // 10. 状态码校验 + DoResponse
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

            // 11. 计费结算（统一走共享分流逻辑：音频 tokens 存在时切到音频计费）
            if (result != null && result.getUsage() != null) {
                CompatibleHandler.postConsumeQuota(quotaService, billingService, info, result.getUsage());
            }
        }
    }

    /**
     * 从请求上下文构建 AudioDTO
     * <ul>
     * <li>TTS (JSON): info.getRequest() 为 Map，转换后取 responseFormat</li>
     * <li>Transcription/Translation (multipart): 从 form 参数提取 responseFormat</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private AudioDTO resolveAudioRequest(RelayInfo info, HttpServletRequest req, boolean isMultipart) {
        if (info.getRequest() instanceof AudioDTO dto) {
            return dto;
        }
        if (isMultipart) {
            AudioDTO dto = new AudioDTO();
            String responseFormat = req.getParameter("response_format");
            dto.setResponseFormat(responseFormat);
            return dto;
        }
        if (info.getRequest() instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) info.getRequest();
            AudioDTO dto = new AudioDTO();
            Object rf = map.get("response_format");
            if (rf != null) {
                dto.setResponseFormat(rf.toString());
            }
            return dto;
        }
        throw CompatibleHandler.newApiError("invalid request type, expected AudioDTO or Map, got "
                + (info.getRequest() != null ? info.getRequest().getClass().getName() : "null"),
                "invalid_request", 400, true);
    }

    /**
     * 提取客户端请求头（含 Content-Type / Accept 等），用于 doFormRequest 透传 multipart Content-Type
     */
    private static Map<String, String> extractClientHeaders(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, req.getHeader(name));
        }
        return headers;
    }
}
