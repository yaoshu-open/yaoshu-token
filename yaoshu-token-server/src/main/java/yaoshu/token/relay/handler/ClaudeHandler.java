package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.ReasoningSuffixConfig;
import yaoshu.token.config.ReasoningSuffixConfig.EffortTrim;
import yaoshu.token.config.model.ClaudeModelConfig;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.*;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.QuotaService;

/**
 * Claude 中转处理器  * <p>
 * 编排 Anthropic Messages API 中转：
 * DeepCopy → ModelMapped → Thinking/Effort 预处理器 → ConvertClaudeRequest
 * → RemoveDisabledFields → ParamOverride → DoRequest → DoResponse → 计费结算
 * <p>
 * 特殊处理：
 * <ul>
 * <li>effort suffix（-effort-LOW/MEDIUM/HIGH）→ thinking.adaptive + outputConfig.effort</li>
 * <li>Opus 4.7/4.8 清空 temperature/top_p/top_k（拒绝非默认值）</li>
 * <li>thinking adapter enabled 时 -thinking 后缀 → thinking.enabled + thinkingBudget</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeHandler {    private final QuotaService quotaService;
    private final BillingService billingService;

    /**
     * Claude 中转编排入口      */
    public void claudeHelper(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        // 1. 类型断言
        if (!(info.getRequest() instanceof ClaudeDTO.ClaudeRequest claudeReq)) {
            throw CompatibleHandler.newApiError("invalid request type, expected ClaudeDTO.ClaudeRequest, got "
                    + (info.getRequest() != null ? info.getRequest().getClass().getName() : "null"),
                    "invalid_request", 400, true);
        }

        // 2. DeepCopy
        ClaudeDTO.ClaudeRequest request = RelayUtils.deepCopy(claudeReq, ClaudeDTO.ClaudeRequest.class);
        if (request == null) {
            throw CompatibleHandler.newApiError("failed to copy request to ClaudeDTO",
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

        // 5. MaxTokens 默认值（Go: claude_settings.getDefaultMaxTokens）
        if (request.getMaxTokens() == null || request.getMaxTokens() == 0) {
            int defaultMaxTokens = ClaudeModelConfig.getInstance().getDefaultMaxTokens(request.getModel());
            request.setMaxTokens(defaultMaxTokens);
        }

        // 6. Thinking / Effort 预处理
        applyClaudeThinkingAdapter(info, request);

        // 7. ConvertClaudeRequest
        Object convertedRequest;
        try {
            convertedRequest = adaptor.convertClaudeRequest(info, request);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

        // 8. JSON 序列化 + RemoveDisabledFields + ParamOverride + OutboundJSONBody
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

        log.debug("claude request body: {}", new String(jsonData));

        OutboundBodyHelper.OutboundBodyResult bodyResult;
        try {
            bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }
        info.setUpstreamRequestBodySize(bodyResult.getSize());

        // 9. DoRequest
        Object rawResp;
        try {
            rawResp = adaptor.doRequest(info, bodyResult.getBody());
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "do_request_failed", 500, false);
        }

        // 10. 响应处理
        processClaudeResponse(req, info, adaptor, rawResp);
    }

    /**
     * Claude Thinking / Effort 预处理器      */
    private void applyClaudeThinkingAdapter(RelayInfo info, ClaudeDTO.ClaudeRequest request) {
        String model = request.getModel();
        if (model == null) return;

        ClaudeModelConfig claudeConfig = ClaudeModelConfig.getInstance();

        // 1. Effort suffix 处理（Go: reasoning.TrimEffortSuffix → thinking.adaptive + outputConfig.effort）
        EffortTrim trim = ReasoningSuffixConfig.trimEffortSuffix(info.getOriginModelName());
        if (trim.found) {
            String effort = trim.effort;
            // 设置 thinking.adaptive + outputConfig.effort
            ClaudeDTO.Thinking thinking = new ClaudeDTO.Thinking();
            thinking.setType("adaptive");
            // 将 effort 大写至 Claude 标准（low→LOW, medium→MEDIUM, high→HIGH, max→MAX 等）
            thinking.setDisplay(effort.toUpperCase());
            request.setThinking(thinking);
            request.setModel(trim.baseModel);
            info.setUpstreamModelName(trim.baseModel);
            model = trim.baseModel;
        }

        // 2. Thinking adapter enabled 时 -thinking 后缀处理
        if (claudeConfig.isThinkingAdapterEnabled()) {
            if (model.endsWith("-thinking")) {
                String baseModel = model.substring(0, model.length() - "-thinking".length());
                // 追加 thinking.enabled + thinkingBudget
                ClaudeDTO.Thinking thinking = request.getThinking();
                if (thinking == null) {
                    thinking = new ClaudeDTO.Thinking();
                    request.setThinking(thinking);
                }
                if (thinking.getType() == null) {
                    thinking.setType("enabled");
                    thinking.setBudgetTokens((int) (claudeConfig.getThinkingAdapterBudgetTokensPercentage()
                            * claudeConfig.getDefaultMaxTokens(baseModel)));
                }
                request.setModel(baseModel);
                info.setUpstreamModelName(baseModel);
                model = baseModel;
            }
        }

        // 3. Opus 4.7/4.8 清空 temperature/top_p/top_k（拒绝非默认值）
        if (model != null && (model.contains("claude-3-opus") || model.contains("claude-opus-4"))) {
            // Opus 模型禁止覆盖 temperature/top_p/top_k
            request.setTemperature(null);
            request.setTopP(null);
            request.setTopK(null);
        }

        // 4. 设置默认 MaxTokens
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(claudeConfig.getDefaultMaxTokens(model != null ? model : "default"));
        }
    }

    /**
     * Claude 响应处理
     */
    private void processClaudeResponse(HttpServletRequest req, RelayInfo info,
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
