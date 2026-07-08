package yaoshu.token.service.payment.waffopancake.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Waffo Pancake 私钥/公钥 PEM 加载器（对齐 Pancake 官方 SDK 自动归一化能力）。
 * <p>
 * 支持三种环境变量形态：
 * 1. 标准 PEM（含 BEGIN/END 标记）
 * 2. Base64 编码整体 PEM（CI/CD 推荐）
 * 3. 文件路径（本地开发）
 * <p>
 * 支持两种私钥格式：
 * - PKCS#8（BEGIN PRIVATE KEY）：JDK 原生支持
 * - PKCS#1（BEGIN RSA PRIVATE KEY）：JDK 不原生支持，通过 DER 包装转 PKCS#8
 * <p>
 * 设计考量：JDK 不原生支持 PKCS#1，引入 BouncyCastle 会增加 pom.xml 依赖。
 * 本类通过手写 PKCS#1→PKCS#8 DER 转换（约 30 行）保持零依赖，符合项目"绝不碰 pom.xml"红线。
 */
public final class PemKeyLoader {

    private static final String PKCS8_PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS1_PRIVATE_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";

    private PemKeyLoader() {
    }

    /**
     * 加载 RSA 私钥（自动识别 PKCS#8 / PKCS#1 / Base64 整体 PEM / 文件路径）。
     */
    public static PrivateKey loadPrivateKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Private key is blank");
        }
        String normalized = normalize(raw);
        if (normalized.contains(PKCS8_PRIVATE_HEADER)) {
            return parsePkcs8Private(stripPemMarkers(normalized, PKCS8_PRIVATE_HEADER, "-----END PRIVATE KEY-----"));
        }
        if (normalized.contains(PKCS1_PRIVATE_HEADER)) {
            // PKCS#1 → 转 PKCS#8 DER → Base64 → 解析
            String pkcs1Base64 = stripPemMarkers(normalized, PKCS1_PRIVATE_HEADER, "-----END RSA PRIVATE KEY-----");
            // 清理空白（PEM 的 64 字符换行），对齐 parsePkcs8Private 的清理逻辑
            byte[] pkcs1Der = Base64.getDecoder().decode(pkcs1Base64.replaceAll("\\s+", ""));
            byte[] pkcs8Der = wrapPkcs1ToPkcs8(pkcs1Der);
            return parsePkcs8Private(Base64.getEncoder().encodeToString(pkcs8Der));
        }
        // 未识别 header：尝试当作裸 Base64（PKCS#8）直接解析
        return parsePkcs8Private(normalized.replaceAll("\\s+", ""));
    }

    /**
     * 加载 RSA 公钥（X.509 SubjectPublicKeyInfo，对齐 Pancake Dashboard 导出格式）。
     */
    public static PublicKey loadPublicKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Public key is blank");
        }
        String normalized = normalize(raw);
        String b64 = stripPemMarkers(normalized, PUBLIC_HEADER, "-----END PUBLIC KEY-----").replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(b64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RSA public key: " + e.getMessage(), e);
        }
    }

    /**
     * 归一化 PEM 字符串：
     * 1. 文件路径（短字符串 + 文件存在）→ 读文件内容
     * 2. Base64 整体 PEM（无 BEGIN 标记 + 无换行）→ 解码后还原为 PEM
     * 3. 字面量 \n 转为实际换行
     */
    private static String normalize(String raw) {
        String trimmed = raw.trim();
        // 文件路径检测
        if (!trimmed.contains("\n") && !trimmed.contains(" ")) {
            try {
                Path path = Path.of(trimmed);
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // 不是文件路径或读失败，继续按字符串处理
            }
        }
        // Base64 整体 PEM 检测
        if (!trimmed.contains("-----BEGIN") && !trimmed.contains("\n") && !trimmed.contains("\\n")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                if (decodedStr.contains("-----BEGIN")) {
                    return decodedStr;
                }
            } catch (IllegalArgumentException ignored) {
                // 不是 Base64 串，按原样处理
            }
        }
        // 字面量 \n 转换为实际换行
        return trimmed.replace("\\n", "\n").replace("\\r", "");
    }

    private static String stripPemMarkers(String pem, String beginHeader, String endHeader) {
        return pem.replace(beginHeader, "").replace(endHeader, "");
    }

    private static PrivateKey parsePkcs8Private(String base64Der) {
        try {
            String cleaned = base64Der.replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PKCS#8 RSA private key: " + e.getMessage(), e);
        }
    }

    /**
     * 将 PKCS#1 RSA 私钥 DER 包装为 PKCS#8 PrivateKeyInfo DER。
     * <p>
     * PKCS#8 结构：
     * <pre>
     * PrivateKeyInfo ::= SEQUENCE {
     *   version Version (INTEGER 0),
     *   privateKeyAlgorithm AlgorithmIdentifier (rsaEncryption + NULL params),
     *   privateKey OCTET STRING (PKCS#1 DER bytes)
     * }
     * </pre>
     * 参考实现：BouncyCastle PrivateKeyInfoFactory 的简化版。
     */
    static byte[] wrapPkcs1ToPkcs8(byte[] pkcs1) {
        // AlgorithmIdentifier for rsaEncryption: OID 1.2.840.113549.1.1.1 + NULL params
        byte[] algId = {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] version = {0x02, 0x01, 0x00};  // INTEGER 0
        byte[] octetString = derWrap((byte) 0x04, pkcs1);  // OCTET STRING containing PKCS#1 DER
        byte[] content = new byte[version.length + algId.length + octetString.length];
        System.arraycopy(version, 0, content, 0, version.length);
        System.arraycopy(algId, 0, content, version.length, algId.length);
        System.arraycopy(octetString, 0, content, version.length + algId.length, octetString.length);
        return derWrap((byte) 0x30, content);  // outer SEQUENCE
    }

    private static byte[] derWrap(byte tag, byte[] content) {
        int len = content.length;
        byte[] lengthBytes;
        if (len < 128) {
            lengthBytes = new byte[]{(byte) len};
        } else if (len < 256) {
            lengthBytes = new byte[]{(byte) 0x81, (byte) len};
        } else if (len < 65536) {
            lengthBytes = new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
        } else {
            throw new IllegalArgumentException("DER content too long: " + len);
        }
        byte[] result = new byte[1 + lengthBytes.length + content.length];
        result[0] = tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
        return result;
    }
}
