package yaoshu.token.service.payment.waffopancake.pojo;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake Webhook 事件数据（对齐 Go WaffoPancakeWebhookData）。
 * <p>
 * 仅保留首期 order.completed / subscription.activated 必需字段。
 * 其他字段（billingDetail / taxRate / paymentFailureReason 等）由 Pancake 按事件类型条件性返回，
 * 第二阶段处理续费/退款事件时按需补全。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeWebhookData {

    /** 订单 ID（ORD_xxx） */
    private String orderId;

    /** 订单状态（completed / active / canceling 等） */
    private String orderStatus;

    /** 商户外部订单号（=tradeNo，反查本地 top_ups 的键） */
    private String orderMerchantExternalId;

    /** 商户提供的买家身份（webhook 二次校验：必须等于 yaoshu-user-{userId}） */
    private String merchantProvidedBuyerIdentity;

    /** 买家邮箱 */
    private String buyerEmail;

    /** 货币代码（USD / JPY 等） */
    private String currency;

    /** 交易金额（含税，2 位小数字符串） */
    private String amount;

    /** 税额（2 位小数字符串） */
    private String taxAmount;

    /** 商品名称 */
    private String productName;

    public static WaffoPancakeWebhookData fromJson(JSONObject data) {
        if (data == null) {
            return null;
        }
        return WaffoPancakeWebhookData.builder()
                .orderId(data.getString("orderId"))
                .orderStatus(data.getString("orderStatus"))
                .orderMerchantExternalId(data.getString("orderMerchantExternalId"))
                .merchantProvidedBuyerIdentity(data.getString("merchantProvidedBuyerIdentity"))
                .buyerEmail(data.getString("buyerEmail"))
                .currency(data.getString("currency"))
                .amount(data.getString("amount"))
                .taxAmount(data.getString("taxAmount"))
                .productName(data.getString("productName"))
                .build();
    }
}
