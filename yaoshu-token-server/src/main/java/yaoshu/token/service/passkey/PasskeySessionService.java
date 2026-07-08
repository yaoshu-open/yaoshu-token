package yaoshu.token.service.passkey;

import com.yubico.webauthn.data.ByteArray;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Passkey 会话管理  * <p>
 * 使用 HttpSession 存储 WebAuthn RegistrationData / AssertionRequest，替代 Go 的 Gin sessions。
 */
@Slf4j
public final class PasskeySessionService {

    public static final String REGISTRATION_SESSION_KEY = "passkey_registration_session";
    public static final String LOGIN_SESSION_KEY = "passkey_login_session";
    public static final String VERIFY_SESSION_KEY = "passkey_verify_session";
    /** Passkey 验证完成标记 key */
    public static final String PASSKEY_READY_KEY = "secure_passkey_ready_at";
    /** Passkey ready 标记有效期（秒） */
    public static final long PASSKEY_READY_TIMEOUT = 60;

    private PasskeySessionService() {}

    /** 保存 WebAuthn 会话数据（JSON 字符串转 ByteArray） */
    public static void saveSessionData(HttpServletRequest request, String key, String jsonData) {
        HttpSession session = request.getSession();
        if (jsonData == null) {
            session.removeAttribute(key);
        } else {
            session.setAttribute(key, jsonData);
        }
    }

    /** 取出并删除会话数据 */
    public static String popSessionData(HttpServletRequest request, String key) {
        HttpSession session = request.getSession();
        Object raw = session.getAttribute(key);
        if (raw == null) return null;
        session.removeAttribute(key);
        return raw instanceof String ? (String) raw : raw.toString();
    }

    /** 获取会话数据（不删除） */
    public static String getSessionData(HttpServletRequest request, String key) {
        HttpSession session = request.getSession();
        Object raw = session.getAttribute(key);
        return raw instanceof String ? (String) raw : null;
    }
}
