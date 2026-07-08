package yaoshu.token.service;

import ai.yue.library.base.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * TOTP（Time-based One-Time Password）双因素认证服务  * <p>
 * 生成 TOTP 密钥、校验验证码、管理备用恢复码。
 * 翻译说明：Go 使用 github.com/pquerna/otp 库，Java 端简化实现 TOTP 核心算法。
 */
@Slf4j
public final class TotpService {

    private TotpService() {
    }

    /** 备用码长度 */
    private static final int BACKUP_CODE_LENGTH = 8;
    /** 备用码数量 */
    private static final int BACKUP_CODE_COUNT = 4;
    /** 最大失败尝试次数 */
    public static final int MAX_FAIL_ATTEMPTS = 5;
    /** 锁定时间（秒） */
    public static final int LOCKOUT_DURATION = 300;

    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 获取 2FA 发行者名称（即系统名称） */
    public static String get2FAIssuer() {
        return CommonConstants.systemName;
    }

    /**
     * 校验 TOTP 验证码（基于 HMAC-SHA1）
     * <p>
     * 翻译说明：Go 使用 totp.Validate()，Java 用 TOTP 标准算法实现。
     * 30 秒周期、6 位数字、SHA1 算法。同时校验当前和前一个时间窗口（±30s 容差）。
     */
    public static boolean validateTOTPCode(String secret, String code) {
        String cleanCode = code.replace(" ", "");
        if (cleanCode.length() != 6) {
            return false;
        }
        if (!cleanCode.matches("\\d{6}")) {
            return false;
        }

        long currentWindow = System.currentTimeMillis() / 1000 / 30;
        // 校验当前窗口 + 前一个窗口（±30s 容差）
        return generateTOTP(secret, currentWindow).equals(cleanCode)
                || generateTOTP(secret, currentWindow - 1).equals(cleanCode);
    }

    /** 生成指定时间窗口的 TOTP 码（RFC 6238） */
    private static String generateTOTP(String secret, long timeWindow) {
        try {
            byte[] key = base32Decode(secret);
            byte[] timeBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                timeBytes[i] = (byte) (timeWindow & 0xFF);
                timeWindow >>= 8;
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            log.error("TOTP generation failed", e);
            return "";
        }
    }

    /** Base32 解码（RFC 4648） */
    private static byte[] base32Decode(String input) {
        String upper = input.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        for (char c : upper.toCharArray()) {
            int value;
            if (c >= 'A' && c <= 'Z') {
                value = c - 'A';
            } else {
                value = c - '2' + 26;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    /** 生成备用恢复码列表 */
    public static List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(generateRandomBackupCode());
        }
        return codes;
    }

    /** 生成单个备用码（XXXX-XXXX 格式） */
    private static String generateRandomBackupCode() {
        char[] code = new char[BACKUP_CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = CHARSET.charAt(SECURE_RANDOM.nextInt(CHARSET.length()));
        }
        String s = new String(code);
        return s.substring(0, 4) + "-" + s.substring(4);
    }

    /** 校验备用码格式 */
    public static boolean validateBackupCode(String code) {
        String clean = code.replace("-", "").toUpperCase();
        if (clean.length() != BACKUP_CODE_LENGTH) {
            return false;
        }
        for (char c : clean.toCharArray()) {
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    /** 标准化备用码格式（XXXX-XXXX） */
    public static String normalizeBackupCode(String code) {
        String clean = code.replace("-", "").toUpperCase();
        if (clean.length() == BACKUP_CODE_LENGTH) {
            return clean.substring(0, 4) + "-" + clean.substring(4);
        }
        return code;
    }

    /** 对备用码进行哈希（SHA-256） */
    public static String hashBackupCode(String code) {
        return CryptoService.password2Hash(normalizeBackupCode(code));
    }

    /** 校验数字验证码格式（6 位纯数字） */
    public static String validateNumericCode(String code) {
        code = code.replace(" ", "");
        if (code.length() != 6) {
            throw new IllegalArgumentException(I18nUtils.get("twofa.code_digits_only"));
        }
        if (!code.matches("\\d+")) {
            throw new IllegalArgumentException(I18nUtils.get("twofa.code_numeric_only"));
        }
        return code;
    }

    /** 生成二维码数据 URL（otpauth 格式） */
    public static String generateQRCodeData(String secret, String username) {
        String issuer = get2FAIssuer();
        String accountName = username + " (" + issuer + ")";
        return "otpauth://totp/" + issuer + ":" + accountName
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&digits=6&period=30";
    }

    /** 生成随机 TOTP 密钥（Base32 编码，长度 32 字符 = 20 bytes） */
    public static String generateSecret() {
        byte[] random = new byte[20];
        SECURE_RANDOM.nextBytes(random);
        return base32Encode(random);
    }

    /** Base32 编码（RFC 4648，大写，无填充） */
    private static String base32Encode(byte[] data) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(alphabet.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            sb.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }
}
