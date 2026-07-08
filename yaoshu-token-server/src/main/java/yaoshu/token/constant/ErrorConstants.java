package yaoshu.token.constant;

/**
 * 错误常量  *
 * @author yaoshu
 */
public final class ErrorConstants {

    private ErrorConstants() {
    }

    // ===== 通用错误 =====
    public static final String ERR_DATABASE = "database error";

    // ===== 用户认证错误 =====
    public static final String ERR_INVALID_CREDENTIALS = "invalid credentials";
    public static final String ERR_USER_EMPTY_CREDENTIALS = "empty credentials";

    // ===== Token 认证错误 =====
    public static final String ERR_TOKEN_NOT_PROVIDED = "token not provided";
    public static final String ERR_TOKEN_INVALID = "token invalid";

    // ===== 兑换码错误 =====
    public static final String ERR_REDEEM_FAILED = "redeem.failed";

    // ===== 2FA 错误 =====
    public static final String ERR_TWO_FA_NOT_ENABLED = "2fa not enabled";
}
