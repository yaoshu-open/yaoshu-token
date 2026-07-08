package yaoshu.token.relay.channel.codex;

import ai.yue.library.base.convert.Convert;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Codex OAuth key 管理工具  * <p>
 * Codex 渠道的 ApiKey 存储为 OAuth 凭证 JSON（含 access_token / account_id 等），
 * 本类负责解析该 JSON 并提供给 {@link CodexAdaptor} 注入请求头。
 */
@Slf4j
public final class CodexOAuthKeyHelper {

    private CodexOAuthKeyHelper() {
    }

    /**
     * Codex OAuth 凭证。      */
    @Data
    public static class OAuthKey {
        @JsonProperty("id_token")
        private String idToken;
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("account_id")
        private String accountId;
        @JsonProperty("last_refresh")
        private String lastRefresh;
        private String email;
        private String type;
        private String expired;
    }

    /**
     * 解析 OAuth 凭证 JSON。      *
     * @param raw 渠道 ApiKey（OAuth 凭证 JSON 字符串）
     * @return 解析后的 OAuthKey
     * @throws IllegalArgumentException raw 为空或非合法 JSON 对象
     */
    public static OAuthKey parseOAuthKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("codex channel: empty oauth key");
        }
        try {
            return Convert.toJavaBean(raw, OAuthKey.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("codex channel: invalid oauth key json");
        }
    }
}
