package yaoshu.token.service.payment.waffopancake.client;

/**
 * Waffo Pancake Webhook 验签异常。
 * <p>
 * 验签失败的 4 种语义化原因，便于上层决定响应码与日志级别：
 * <ul>
 *   <li>{@link #MALFORMED_SIGNATURE_HEADER} — X-Waffo-Signature header 格式错误</li>
 *   <li>{@link #REPLAY_WINDOW_EXCEEDED} — t 时间戳超出 5 分钟重放窗口</li>
 *   <li>{@link #SIGNATURE_INVALID} — RSA-SHA256 验签失败（公钥与签名不匹配）</li>
 *   <li>{@link #MODE_KEY_MISMATCH} — body.mode 与验签成功公钥的环境不一致</li>
 * </ul>
 */
public class WaffoPancakeWebhookException extends RuntimeException {

    public enum Reason {
        MALFORMED_SIGNATURE_HEADER,
        REPLAY_WINDOW_EXCEEDED,
        SIGNATURE_INVALID,
        MODE_KEY_MISMATCH
    }

    private final Reason reason;

    public WaffoPancakeWebhookException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
