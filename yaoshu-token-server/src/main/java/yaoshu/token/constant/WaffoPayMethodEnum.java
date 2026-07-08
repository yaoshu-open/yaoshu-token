package yaoshu.token.constant;

import java.util.List;

/**
 * Waffo 支付方式常量  */
public final class WaffoPayMethodEnum {

    private WaffoPayMethodEnum() {
    }

    /**
     * Waffo 支付方式定义
     */
    public record WaffoPayMethod(
            String name,
            String icon,
            String payMethodType,
            String payMethodName
    ) {
    }

    /** 默认支持的支付方式列表 */
    public static final List<WaffoPayMethod> DEFAULT_WAFFO_PAY_METHODS = List.of(
            new WaffoPayMethod("Card", "/pay-card.png", "CREDITCARD,DEBITCARD", ""),
            new WaffoPayMethod("Apple Pay", "/pay-apple.png", "APPLEPAY", "APPLEPAY"),
            new WaffoPayMethod("Google Pay", "/pay-google.png", "GOOGLEPAY", "GOOGLEPAY")
    );
}
