package yaoshu.token.service.payment.waffopancake.client;

/**
 * Waffo Pancake REST API 调用异常（HTTP 失败、响应解析失败、签名错误等）。
 */
public class WaffoPancakeApiException extends RuntimeException {

    private final int statusCode;

    public WaffoPancakeApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public WaffoPancakeApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public WaffoPancakeApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
