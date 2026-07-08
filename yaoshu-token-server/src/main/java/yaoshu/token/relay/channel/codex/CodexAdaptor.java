package yaoshu.token.relay.channel.codex;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.util.List;
import java.util.Map;

/**
 * Codex (OpenAI 内部 CLI 后端) 渠道适配器  * <p>
 * 仅支持 /v1/responses 和 /v1/responses/compact 端点。
 * 使用 /backend-api/ 路径前缀 + OAuth 认证。
 */
@Slf4j
public class CodexAdaptor extends OpenAIAdaptor {

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        int mode = info.getRelayMode();
        if (mode != RelayModeEnum.RESPONSES && mode != RelayModeEnum.RESPONSES_COMPACT)
            throw new IllegalArgumentException("codex only supports /v1/responses and /v1/responses/compact");
        String path = "/backend-api/codex/responses";
        if (mode == RelayModeEnum.RESPONSES_COMPACT) path += "/compact";
        return RelayUtils.getFullRequestURL(info.getChannelBaseUrl(), path, info.getChannelType());
    }

    @Override @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);

        // Codex 的 ApiKey 是 OAuth 凭证 JSON，解析后注入 Bearer token 与 account-id
        String key = info.getApiKey() != null ? info.getApiKey().trim() : "";
        if (!key.startsWith("{")) {
            throw new IllegalArgumentException("codex channel: key must be a JSON object");
        }
        CodexOAuthKeyHelper.OAuthKey oauthKey = CodexOAuthKeyHelper.parseOAuthKey(key);
        String accessToken = oauthKey.getAccessToken() != null ? oauthKey.getAccessToken().trim() : "";
        String accountId = oauthKey.getAccountId() != null ? oauthKey.getAccountId().trim() : "";
        if (accessToken.isEmpty()) {
            throw new IllegalArgumentException("codex channel: access_token is required");
        }
        if (accountId.isEmpty()) {
            throw new IllegalArgumentException("codex channel: account_id is required");
        }

        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("chatgpt-account-id", accountId);
        headers.putIfAbsent("OpenAI-Beta", "responses=experimental");
        headers.putIfAbsent("originator", "codex_cli_rs");
        // 上游对 Content-Type 严格，强制精确媒体类型
        headers.put("Content-Type", "application/json");
        if (info.isStream()) {
            headers.put("Accept", "text/event-stream");
        } else {
            headers.putIfAbsent("Accept", "application/json");
        }
        return headers;
    }

    @Override @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        throw new IllegalArgumentException("codex: /v1/chat/completions not supported");
    }

    /**
     * Codex Responses 预处理      * <p>
     * 关键转换：
     * <ul>
     * <li>store=false：Codex 不支持 store 参数</li>
     * <li>instructions：从 system message 提取并设置</li>
     * </ul>
     */
    @Override @SuppressWarnings("unchecked")
    public Object convertOpenAIResponsesRequest(RelayInfo info, OpenAIResponsesRequest responsesReq) throws Exception {
        // Codex 不支持 store，强制 false
        responsesReq.setStore(Boolean.FALSE);

        // 从 input messages 中提取 system message 作为 instructions
        if (responsesReq.getInstructions() == null) {
            Object inputObj = responsesReq.getInput();
            if (inputObj instanceof List) {
                List<OpenAIResponsesRequest.Input> inputs = (List<OpenAIResponsesRequest.Input>) inputObj;
                for (int i = 0; i < inputs.size(); i++) {
                    OpenAIResponsesRequest.Input input = inputs.get(i);
                    if ("message".equals(input.getType()) && "system".equals(input.getRole())) {
                        responsesReq.setInstructions(input.getContent());
                        inputs.remove(i);
                        break;
                    }
                }
            }
        }

        return responsesReq;
    }

    @Override
    public List<String> getModelList() { return CodexConstant.MODEL_LIST; }
    @Override
    public String getChannelName() { return CodexConstant.CHANNEL_NAME; }
}
