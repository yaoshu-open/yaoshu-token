package yaoshu.token.service.oauthruntime;

import jakarta.servlet.http.HttpServletRequest;
import yaoshu.token.pojo.entity.User;

/**
 * OAuth Provider 运行时抽象。  */
public interface OAuthProvider {

    String getName();

    boolean isEnabled();

    OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request);

    OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request);

    boolean isUserIdTaken(String providerUserId);

    User fillUserByProviderId(String providerUserId);

    void setProviderUserId(User user, String providerUserId);

    String getProviderPrefix();

    default boolean isGenericProvider() {
        return false;
    }

    default Integer getProviderId() {
        return null;
    }
}
