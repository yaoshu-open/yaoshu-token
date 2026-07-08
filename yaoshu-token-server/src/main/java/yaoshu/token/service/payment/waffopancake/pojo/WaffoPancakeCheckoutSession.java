package yaoshu.token.service.payment.waffopancake.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake checkout 会话创建响应（对齐 Go WaffoPancakeCheckoutSession）。
 * <p>
 * 业务层用 checkoutUrl 引导前端跳转，sessionId/expiresAt 用于跟踪会话状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeCheckoutSession {

    /** 会话 ID（必填，非空校验） */
    private String sessionId;

    /** Checkout 跳转 URL（必填，非空校验，前端跳转目标） */
    private String checkoutUrl;

    /** 会话过期时间（ISO 8601） */
    private String expiresAt;

    /** Buyer session token（嵌入 checkoutUrl fragment，供前端预填表单） */
    private String token;

    /** Token 过期时间（ISO 8601） */
    private String tokenExpiresAt;
}
