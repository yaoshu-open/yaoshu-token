package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 验证码服务（生成、注册、校验）  */
@Slf4j
public final class VerificationService {

    private VerificationService() {
    }

    /** 邮箱验证用途 */
    public static final String EMAIL_VERIFICATION_PURPOSE = "v";
    /** 密码重置用途 */
    public static final String PASSWORD_RESET_PURPOSE = "r";

    /** 验证码有效期（分钟） */
    public static int verificationValidMinutes = 10;

    // Go 使用 map[string]verificationValue + sync.Mutex，Java 直接用 ConcurrentHashMap
    private static final java.util.Map<String, VerificationValue> verificationMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SIZE = 10;

    private record VerificationValue(String code, LocalDateTime time) {
    }

    /**
     * 生成指定长度的验证码（基于 UUID，去掉连字符）
     */
    public static String generateVerificationCode(int length) {
        String code = UUID.randomUUID().toString().replace("-", "");
        if (length <= 0) {
            return code;
        }
        return code.substring(0, Math.min(length, code.length()));
    }

    /**
     * 注册验证码（purpose + key 作为复合键）
     */
    public static void registerCode(String key, String code, String purpose) {
        verificationMap.put(purpose + key, new VerificationValue(code, LocalDateTime.now()));
        if (verificationMap.size() > MAX_SIZE) {
            removeExpiredPairs();
        }
    }

    /**
     * 校验验证码
     */
    public static boolean verifyCode(String key, String code, String purpose) {
        VerificationValue value = verificationMap.get(purpose + key);
        if (value == null) {
            return false;
        }
        long elapsedMinutes = java.time.Duration.between(value.time(), LocalDateTime.now()).toMinutes();
        if (elapsedMinutes >= verificationValidMinutes) {
            return false;
        }
        return code.equals(value.code());
    }

    /**
     * 删除验证码
     */
    public static void deleteKey(String key, String purpose) {
        verificationMap.remove(purpose + key);
    }

    /** 移除过期的验证码对（由 registerCode 触发） */
    private static void removeExpiredPairs() {
        LocalDateTime now = LocalDateTime.now();
        verificationMap.entrySet().removeIf(entry -> {
            long elapsedMinutes = java.time.Duration.between(entry.getValue().time(), now).toMinutes();
            return elapsedMinutes >= verificationValidMinutes;
        });
    }
}
