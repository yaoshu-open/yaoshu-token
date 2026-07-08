package yaoshu.token.config;

import lombok.Data;

/**
 * 支付平台配置（Stripe / Creem / Waffo / WaffoPancake），
 */
public final class PaymentConfig {

    private PaymentConfig() {
    }

    @Data
    public static class StripeConfig {
        private boolean enabled;
        private String publicKey;
        private String secretKey;
        private String webhookSecret;
        private String currency = "USD";
    }

    @Data
    public static class CreemConfig {
        private boolean enabled;
        private String apiKey;
        private String webhookSecret;
    }

    @Data
    public static class WaffoConfig {
        private boolean enabled;
        private String apiKey;
        private String endpoint;
    }
}
