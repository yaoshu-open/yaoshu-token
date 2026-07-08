package yaoshu.token.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 充值信息响应 VO  * <p>
 * 全部字段使用 camelCase，满足全局 API 契约。替换原 TopupService.getTopupInfo 返回的 snake_case Map。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopupInfoVO {

    /** 是否启用在线充值（易支付） */
    private Boolean enableOnlineTopup;

    /** 是否启用 Stripe 充值 */
    private Boolean enableStripeTopup;

    /** 是否启用 Creem 充值 */
    private Boolean enableCreemTopup;

    /** 是否启用 Waffo 充值 */
    private Boolean enableWaffoTopup;

    /** 是否启用 Waffo Pancake 充值 */
    private Boolean enableWaffoPancakeTopup;

    /** 是否启用兑换码（依赖合规确认） */
    private Boolean enableRedemption;

    /** 支付合规是否已确认 */
    private Boolean paymentComplianceConfirmed;

    /** 支付合规条款版本 */
    private String paymentComplianceTermsVersion;

    /**
     * Waffo 支付方式列表。
     * <p>
     * 数据库 options.WaffoPayMethods 已使用 camelCase key（payMethodType/payMethodName），
     * 透传即可满足契约，无需 POJO 化。
     */
    private List<Object> waffoPayMethods;

    /**
     * Creem 商品列表。
     * <p>
     * 数据库 options.CreemProducts 已使用 camelCase key（productId/name/price/currency/quota），
     * 透传即可满足契约，无需 POJO 化。
     */
    private List<Object> creemProducts;

    /** 支付方式列表（含数据库配置 + 动态注入的 Stripe/Waffo 等） */
    private List<PayMethodVO> payMethods;

    /** 最小充值额度（按配额显示模式换算后的 long） */
    private Long minTopup;

    /** Stripe 最小充值额度 */
    private Integer stripeMinTopup;

    /** Waffo 最小充值额度 */
    private Integer waffoMinTopup;

    /** Waffo Pancake 最小充值额度 */
    private Integer waffoPancakeMinTopup;

    /** 预设充值金额选项 */
    private List<Integer> amountOptions;

    /** 折扣配置（当前为空对象占位，预留扩展） */
    private Map<String, Object> discount;

    /** 充值链接（当前为空串占位，预留扩展） */
    private String topupLink;
}
