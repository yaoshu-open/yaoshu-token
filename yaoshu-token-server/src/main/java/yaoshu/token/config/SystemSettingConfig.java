package yaoshu.token.config;

import lombok.Data;

/**
 * 系统设置聚合器  * <p>
 * 包含 Fetch、Discord、OIDC、Passkey、Legal、Theme、旧系统设置。
 */
public final class SystemSettingConfig {

    private SystemSettingConfig() {
    }

    @Data
    public static class FetchSetting {
        private boolean enableSSRFProtection;
        private boolean allowPrivateIp;
        private String domainFilterMode;
        private String ipFilterMode;
        private java.util.List<String> domainList;
        private java.util.List<String> ipList;
        private java.util.List<Integer> allowedPorts;
        private boolean applyIPFilterForDomain;
    }

    @Data
    public static class DiscordSetting {
        private boolean enabled;
        private String botToken;
        private String channelId;
    }

    @Data
    public static class OidcSetting {
        private boolean enabled;
        private String issuer;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope = "openid profile email";
    }

    @Data
    public static class PasskeySetting {
        private boolean enabled;
        private String rpName;
        private String rpId;
        /** Origins 列表（逗号分隔） */
        private String origin;
        /** 用户验证要求：required / preferred / discouraged */
        private String userVerification = "preferred";
        /** 认证器附着偏好：platform / cross-platform */
        private String attachmentPreference;
        /** 是否允许非安全 Origin（开发环境） */
        private boolean allowInsecureOrigin;

        /** 全局实例 */
        private static final PasskeySetting INSTANCE = new PasskeySetting();

        public static PasskeySetting current() { return INSTANCE; }
    }

    @Data
    public static class LegalSetting {
        private String termsOfService;
        private String privacyPolicy;
        private boolean requireAgreement;
    }

    @Data
    public static class ThemeSetting {
        private String theme = "classic";
        private String customCSS;
        private String logo;
        private String favicon;
    }

    @Data
    public static class SystemSettingOld {
        private boolean enabled;
        private String deprecatedField;
    }
}
