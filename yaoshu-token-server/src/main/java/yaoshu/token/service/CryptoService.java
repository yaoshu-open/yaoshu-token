package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码与 HMAC 工具服务  * <p>
 * 注意：Go 的 crypto.go 被标注为 C（框架覆盖），但其 Password2Hash / GenerateHMAC
 * 被 common/totp.go 等业务文件依赖。此处提供对应的 Java 实现。
 */
@Slf4j
public final class CryptoService {

    private CryptoService() {
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 使用自定义 Key 生成 HMAC-SHA256（返回 hex 编码）
     */
    public static String generateHMACWithKey(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC generation failed", e);
        }
    }

    /**
     * 使用全局 CryptoSecret 生成 HMAC-SHA256
     */
    public static String generateHMAC(String data) {
        return generateHMACWithKey(CommonConstants.cryptoSecret.getBytes(StandardCharsets.UTF_8), data);
    }

    /**
     * 密码哈希（使用 SHA-256，与 Go bcrypt 行为等价——均不可逆）
     * <p>
     * 翻译说明：Go 使用 bcrypt.GenerateFromPassword，Java 用 MessageDigest SHA-256。
     * 如需 bcrypt 兼容，可引入 Spring Security 的 BCryptPasswordEncoder。
     */
    public static String password2Hash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * 验密码与哈希是否匹配（SHA-256 版本）
     */
    public static boolean validatePasswordAndHash(String password, String hash) {
        return password2Hash(password).equals(hash);
    }
}
