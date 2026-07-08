package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.pojo.dto.RelayFormat;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIResponsesHandler;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.relay.common.OutboundBodyHelper;
import yaoshu.token.relay.common.OverrideUtils;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;
import yaoshu.token.relay.common.RequestConversion;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.service.ErrorHandlingService;
import yaoshu.token.service.openaicompat.ChatToResponseService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 * Chat Completions via Responses 中转处理器  * <p>
 * 当渠道配置为 Responses API 模式时，将 Chat Completions 请求转换为 Responses API 格式后发出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatViaResponsesHandler {    /**
     * Chat Completions via Responses 中转编排入口      * <p>
     * 编排流程：
     * 1. Chat→Responses 请求转换
     * 2. 设置 RelayMode = Responses，重写 RequestURLPath
     * 3. Adaptor 转换 + 发起上游请求
     * 4. Responses→Chat 响应转换（流式/非流式分流）
     *
     * @param request  Chat Completions 请求
     * @param info     RelayInfo
     * @param adaptor  渠道适配器
     * @return Usage 计费信息
     */
    @SuppressWarnings("unchecked")
    public Usage chatCompletionsViaResponses(
            HttpServletRequest requestContext,
            HttpServletResponse response,
            GeneralOpenAIRequest request,
            RelayInfo info,
            IAdaptor adaptor) throws Exception {

        byte[] chatJson;
        try {
            chatJson = Convert.toJSONString(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }

        try {
            chatJson = RelayUtils.removeDisabledFields(
                    chatJson, info.getChannelOtherSettings(),
                    info.getChannelSetting() != null && Boolean.TRUE.equals(info.getChannelSetting().getPassThroughBodyEnabled()));
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "convert_request_failed", 400, true);
        }

        if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
            try {
                chatJson = OverrideUtils.applyParamOverrideWithRelayInfo(chatJson, info);
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

        GeneralOpenAIRequest overriddenChatReq;
        try {
            overriddenChatReq = Convert.toJavaBean(chatJson, GeneralOpenAIRequest.class);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "channel_param_override_invalid", 400, true);
        }

        // 1. Chat → Responses 请求转换
        OpenAIResponsesRequest responsesReq;
        try {
            responsesReq = ChatToResponseService.chatCompletionsRequestToResponsesRequest(overriddenChatReq);
        } catch (Exception e) {
            throw CompatibleHandler.newApiError(e, "invalid_request", 400, true);
        }

        // 记录请求格式转换
        info.appendRequestConversion(RelayFormat.OPENAI_RESPONSES);

        // 2. 临时切换 RelayMode → Responses
        int savedRelayMode = info.getRelayMode();
        String savedRequestURLPath = info.getRequestURLPath();
        try {
            info.setRelayMode(RelayModeEnum.RESPONSES);
            info.setRequestURLPath("/v1/responses");

            // 3. Adaptor 转换 Responses 请求 + 发起上游 HTTP 请求
            Object convertedRequest = adaptor.convertOpenAIResponsesRequest(info, responsesReq);
            RequestConversion.appendRequestConversionFromRequest(info, convertedRequest);

            byte[] jsonData = Convert.toJSONString(convertedRequest).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            jsonData = RelayUtils.removeDisabledFields(
                    jsonData, info.getChannelOtherSettings(),
                    info.getChannelSetting() != null && Boolean.TRUE.equals(info.getChannelSetting().getPassThroughBodyEnabled()));

            OutboundBodyHelper.OutboundBodyResult bodyResult = OutboundBodyHelper.createOutboundJSONBody(jsonData);
            info.setUpstreamRequestBodySize(bodyResult.getSize());

            Object rawResp = adaptor.doRequest(info, bodyResult.getBody());
            if (!(rawResp instanceof HttpResponse<?> httpResp)) {
                throw CompatibleHandler.newApiError("bad upstream response type", "bad_response", 500, false);
            }

            String contentType = httpResp.headers().firstValue("Content-Type").orElse("");
            info.setStream(info.isStream() || contentType.startsWith("text/event-stream"));

            String statusCodeMappingStr = requestContext != null
                    ? (String) requestContext.getAttribute("status_code_mapping") : null;
            if (httpResp.statusCode() != 200) {
                RelayException relayError = CompatibleHandler.handleHttpResponseError(httpResp, false);
                ErrorHandlingService.applyStatusCodeMapping(relayError, statusCodeMappingStr);
                throw relayError;
            }

            // 4. Responses → Chat 响应转换（流式/非流式分流）
            if (info.isStream()) {
                return handleStreamingResponse(info, httpResp, response);
            } else {
                return handleNonStreamingResponse(info, httpResp, response);
            }

        } finally {
            info.setRelayMode(savedRelayMode);
            info.setRequestURLPath(savedRequestURLPath);
        }
    }

    /**
     * 非流式 Responses → Chat 转换
     * <p>
     * 从上游 HTTP 响应读取完整 JSON body → OpenAIResponsesHandler 转换 →
     * 返回 OpenAITextResponse（格式转换由外层统一处理）。
     */
    private Usage handleNonStreamingResponse(RelayInfo info, HttpResponse<?> httpResp, HttpServletResponse response) throws Exception {
        InputStream inputStream = extractResponseBodyStream(httpResp);
        byte[] responseBody = readAllBytes(inputStream);

        Usage usage = OpenAIResponsesHandler.oaiResponsesToChatHandler(info, responseBody, response);

        log.debug("ChatViaResponsesHandler: non-streaming response converted, format={}", info.getRelayFormat());

        return usage;
    }

    /**
     * 流式 Responses → Chat 转换
     * <p>
     * 从上游 HTTP 响应获取 SSE InputStream → OpenAIResponsesHandler 流式扫描 →
     * 逐行转换为 Chat Chunk → 发送到客户端。
     */
    private Usage handleStreamingResponse(RelayInfo info, HttpResponse<?> httpResp, HttpServletResponse response) throws Exception {
        InputStream inputStream = extractResponseBodyStream(httpResp);

        // 设置 SSE 响应头
        if (response != null) {
            RelayCommonHelper.setEventStreamHeaders(response);
        }
        return OpenAIResponsesHandler.oaiResponsesToChatStreamHandler(info, inputStream, response);
    }

    private static InputStream extractResponseBodyStream(HttpResponse<?> httpResp) {
        Object body = httpResp.body();
        if (body instanceof InputStream inputStream) {
            return inputStream;
        }
        if (body instanceof byte[] bytes) {
            return new java.io.ByteArrayInputStream(bytes);
        }
        throw CompatibleHandler.newApiError("unexpected response body type", "bad_response", 500, false);
    }

    /** 从 InputStream 读取全部字节 */
    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}
