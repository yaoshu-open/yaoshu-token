package yaoshu.token.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付平台配置（Spring @ConfigurationProperties 绑定）
 * <p>
 * yml 配置前缀：payment
 * <pre>
 * payment:
 *   stripe:
 *     enabled: true
 *     public-key: pk_xxx
 *     secret-key: sk_xxx
 *     webhook-secret: whsec_xxx
 *     currency: USD
 *   creem:
 *     enabled: false
 *     api-key: xxx
 *     webhook-secret: xxx
 *   waffo:
 *     enabled: false
 *     api-key: xxx
 *     endpoint: https://...
 * </pre>
 * <p>
 * 说明：Waffo Pancake 配置不在此处声明。它沿用 Stripe/Creem 机制，
 * 全部从 options 表读取（见 TopupService#isWaffoPancakeTopupEnabled）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private StripeConfig stripe = new StripeConfig();
    private CreemConfig creem = new CreemConfig();
    private WaffoConfig waffo = new WaffoConfig();

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
