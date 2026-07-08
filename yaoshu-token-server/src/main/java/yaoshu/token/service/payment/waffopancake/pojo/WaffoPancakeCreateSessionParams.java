package yaoshu.token.service.payment.waffopancake.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake checkout 会话创建入参（对齐 Go WaffoPancakeCreateSessionParams）。
 * <p>
 * 业务层组装后传给 WaffoPancakeApiClient 完成 Authenticated Checkout 两步调用
 *（issue-session-token + create-session）。tradeNo 通过 orderMerchantExternalId 传入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeCreateSessionParams {

    /** 商品 ID（必填，PROD_xxx） */
    private String productId;

    /** 买家身份（必填，yaoshu-user-{userId}，webhook 二次校验用） */
    private String buyerIdentity;

    /** 买家邮箱（可选，预填 checkout 表单） */
    private String buyerEmail;

    /** 商户外部订单号（必填，=tradeNo，Pancake 原样回传作为反查键） */
    private String orderMerchantExternalId;

    /** 会话有效期秒数（可选，默认 2700=45 分钟） */
    private Integer expiresInSeconds;

    /** 价格覆盖（可选，动态定价场景） */
    private WaffoPancakePriceSnapshot priceSnapshot;
}
