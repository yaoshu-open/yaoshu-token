package yaoshu.token.relay.channel.claude;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.model.ClaudeModelConfig;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;
import java.util.Map;

/**
 * Claude 渠道适配器  * <p>
 * 关键差异：使用 x-api-key（非 Bearer）、anthropic-version 头、
 * Beta Query 追加、ClaudeSettings 请求头注入。
 */
@Slf4j
public class ClaudeAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String requestURL = info.getChannelBaseUrl() + "/v1/messages";

        // 追加 beta=true 查询参数
        boolean shouldAppendBeta = info.isClaudeBetaQuery();
        if (!shouldAppendBeta) {
            var otherSettings = info.getChannelOtherSettings();
            if (otherSettings != null && otherSettings.isClaudeBetaQuery()) {
                shouldAppendBeta = true;
            }
        }

        if (shouldAppendBeta) {
            if (requestURL.contains("?")) {
                requestURL += "&beta=true";
            } else {
                requestURL += "?beta=true";
            }
        }
        return requestURL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);

        // Claude 使用 x-api-key（非 Bearer Authorization）
        headers.put("x-api-key", info.getApiKey());

        // anthropic-version：优先使用客户端传入的版本，默认 2023-06-01
        Map<String, String> clientHeaders = info.getClientHeaders();
        String anthropicVersion = clientHeaders != null ? clientHeaders.get("anthropic-version") : null;
        headers.put("anthropic-version", anthropicVersion != null ? anthropicVersion : "2023-06-01");

        // CommonClaudeHeadersOperation：透传 anthropic-beta + ClaudeSettings 注入头
        if (clientHeaders != null) {
            String anthropicBeta = clientHeaders.get("anthropic-beta");
            if (anthropicBeta != null && !anthropicBeta.isEmpty()) {
                headers.put("anthropic-beta", anthropicBeta);
            }
        }
        ClaudeModelConfig.getInstance().writeHeaders(info.getOriginModelName(), headers);

        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        // Claude channels: RequestOpenAI2ClaudeMessage 转换 — OpenAI messages → Claude messages
        // 当前 Claude 请求走 CompatibleHandler 流程，由 super.convertOpenAIRequest() 透传
        // 原生 Claude 请求由 ClaudeHandler.claudeHelper() → ConvertService.claudeToOpenAIRequest() 处理
        return super.convertOpenAIRequest(info, request);
    }

    @Override
    public List<String> getModelList() { return ClaudeConstant.MODEL_LIST; }
    @Override
    public String getChannelName() { return ClaudeConstant.CHANNEL_NAME; }
}
