package yaoshu.token.service.oauthruntime;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth 运行时模型，贴齐 Go oauth/types.go。
 */
public final class OAuthRuntimeModels {

    private OAuthRuntimeModels() {
    }

    @Data
    public static class OAuthToken {
        private String accessToken;
        private String tokenType;
        private String refreshToken;
        private Integer expiresIn;
        private String scope;
        private String idToken;
    }

    @Data
    public static class OAuthUser {
        private String providerUserId;
        private String username;
        private String displayName;
        private String email;
        private Map<String, Object> extra = new LinkedHashMap<>();
    }
}
