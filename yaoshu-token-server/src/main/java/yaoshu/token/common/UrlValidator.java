package yaoshu.token.common;

import yaoshu.token.constant.EnvConstants;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 重定向 URL 安全校验  */
public final class UrlValidator {

    private UrlValidator() {
    }

    /**
     * 验重定向 URL 是否安全可信
     * <ul>
     *   <li>URL 格式正确</li>
     *   <li>协议为 http 或 https</li>
     *   <li>域名在可信域名列表中（精确匹配或子域名匹配）</li>
     * </ul>
     *
     * @param rawUrl 待校验的 URL
     * @throws IllegalArgumentException URL 不合法或不可信
     */
    public static void validateRedirectURL(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid URL format: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("invalid URL scheme: only http and https are allowed");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("invalid URL: missing host");
        }
        String domain = host.toLowerCase();

        String[] trustedDomains = EnvConstants.trustedRedirectDomains;
        if (trustedDomains != null) {
            for (String trustedDomain : trustedDomains) {
                if (domain.equals(trustedDomain) || domain.endsWith("." + trustedDomain)) {
                    return;
                }
            }
        }

        throw new IllegalArgumentException("domain " + domain + " is not in the trusted domains list");
    }
}
