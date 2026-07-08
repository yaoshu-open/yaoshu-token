package yaoshu.token.service.payment.waffopancake.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PemKeyLoader 单测 —— 覆盖 PKCS#8 / PKCS#1 双格式 + 三种环境变量形态。
 * <p>
 * 设计考量：PemKeyLoader 手写了 PKCS#1→PKCS#8 DER 转换（约 30 行自定义加密代码），
 * 必须用独立 ASN.1 解析（DerCursor）作为测试 oracle 交叉验证，避免"用被测代码自己测自己"的循环。
 * <p>
 * 动态生成 RSA 2048 密钥对（@BeforeAll），所有用例共享，不硬编码测试密钥。
 */
@DisplayName("PemKeyLoader — PEM 私钥/公钥解析")
class PemKeyLoaderTest {

    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_FOOTER = "-----END PUBLIC KEY-----";

    private static KeyPair keyPair;
    private static byte[] pkcs8Der;
    private static byte[] pkcs1Der;  // 由独立 ASN.1 oracle 提取，非被测代码产物

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        pkcs8Der = keyPair.getPrivate().getEncoded();
        // 独立 ASN.1 解析提取 PKCS#1 DER（测试 oracle，不依赖被测 wrapPkcs1ToPkcs8）
        pkcs1Der = extractPkcs1FromPkcs8(pkcs8Der);
    }

    // ======================== 私钥解析（PKCS#8 系列） ========================

    @Test
    @DisplayName("PKCS#8 标准 PEM 解析成功")
    void loadPrivateKey_Pkcs8Pem_Success() {
        String pem = toPem(pkcs8Der, PKCS8_HEADER, PKCS8_FOOTER);
        PrivateKey parsed = PemKeyLoader.loadPrivateKey(pem);
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("PKCS#1 PEM 经 DER 转换解析成功（验证 wrapPkcs1ToPkcs8 自定义加密代码）")
    void loadPrivateKey_Pkcs1Pem_Success() {
        String pem = toPem(pkcs1Der, PKCS1_HEADER, PKCS1_FOOTER);
        PrivateKey parsed = PemKeyLoader.loadPrivateKey(pem);
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("直接调用 wrapPkcs1ToPkcs8：已知 PKCS#1 DER 包装后可被 JDK KeyFactory 解析")
    void wrapPkcs1ToPkcs8_ProducesValidPkcs8() throws Exception {
        // 独立验证 wrapPkcs1ToPkcs8（package-private static）的正确性
        byte[] wrapped = PemKeyLoader.wrapPkcs1ToPkcs8(pkcs1Der);
        // JDK 原生解析包装后的 PKCS#8
        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(wrapped);
        PrivateKey parsed = java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("Base64 编码整体 PEM（CI/CD 推荐形态）解析成功")
    void loadPrivateKey_Base64EntirePem_Success() {
        String standardPem = toPem(pkcs8Der, PKCS8_HEADER, PKCS8_FOOTER);
        // 整体 PEM 再做一次 Base64 编码（CI/CD 形态：一行 Base64 串）
        String entireBase64 = Base64.getEncoder().encodeToString(standardPem.getBytes(StandardCharsets.UTF_8));
        PrivateKey parsed = PemKeyLoader.loadPrivateKey(entireBase64);
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("字面量 \\n 转义形态（环境变量常见）解析成功")
    void loadPrivateKey_LiteralNewline_Success() {
        String standardPem = toPem(pkcs8Der, PKCS8_HEADER, PKCS8_FOOTER);
        // 将实际换行替换为字面量 \n（环境变量注入常见形态）
        String literalNewline = standardPem.replace("\n", "\\n");
        PrivateKey parsed = PemKeyLoader.loadPrivateKey(literalNewline);
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("文件路径形态（本地开发）解析成功")
    void loadPrivateKey_FilePath_Success(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("test_private.pem");
        String standardPem = toPem(pkcs8Der, PKCS8_HEADER, PKCS8_FOOTER);
        Files.writeString(keyFile, standardPem, StandardCharsets.UTF_8);
        PrivateKey parsed = PemKeyLoader.loadPrivateKey(keyFile.toString());
        assertPrivateKeyEquals(keyPair.getPrivate(), parsed);
    }

    @Test
    @DisplayName("空白输入抛 IllegalArgumentException")
    void loadPrivateKey_Blank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.loadPrivateKey(""));
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.loadPrivateKey("   "));
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.loadPrivateKey(null));
    }

    @Test
    @DisplayName("无效 PEM 内容抛异常")
    void loadPrivateKey_Invalid_Throws() {
        // 缺少 header 的裸 Base64，但内容非有效 PKCS#8 DER
        String invalid = Base64.getEncoder().encodeToString("not a real key".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> PemKeyLoader.loadPrivateKey(invalid));
    }

    // ======================== 公钥解析 ========================

    @Test
    @DisplayName("X.509 SubjectPublicKeyInfo PEM 公钥解析成功")
    void loadPublicKey_X509Pem_Success() {
        byte[] x509Der = keyPair.getPublic().getEncoded();
        String pem = toPem(x509Der, PUBLIC_HEADER, PUBLIC_FOOTER);
        PublicKey parsed = PemKeyLoader.loadPublicKey(pem);
        assertNotNull(parsed);
        assertEquals("RSA", parsed.getAlgorithm());
        assertArrayEquals(x509Der, parsed.getEncoded());
    }

    @Test
    @DisplayName("公钥空白输入抛 IllegalArgumentException")
    void loadPublicKey_Blank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.loadPublicKey(""));
        assertThrows(IllegalArgumentException.class, () -> PemKeyLoader.loadPublicKey(null));
    }

    // ======================== 测试 helper ========================

    /** 构造标准 PEM（含 BEGIN/END 标记 + 64 字符换行） */
    private static String toPem(byte[] der, String header, String footer) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append(header).append('\n');
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        sb.append(footer).append('\n');
        return sb.toString();
    }

    /**
     * 断言两个 RSA 私钥等价（比较 modulus + privateExponent，不比较 DER 编码——
     * DER 编码可能因 padding 差异不同，但数学参数一致即同一密钥）。
     */
    private static void assertPrivateKeyEquals(PrivateKey expected, PrivateKey actual) {
        assertNotNull(actual);
        assertEquals("RSA", actual.getAlgorithm());
        RSAPrivateKey e = (RSAPrivateKey) expected;
        RSAPrivateKey a = (RSAPrivateKey) actual;
        assertEquals(e.getModulus(), a.getModulus(), "modulus mismatch");
        assertEquals(e.getPrivateExponent(), a.getPrivateExponent(), "privateExponent mismatch");
    }

    /**
     * 独立 ASN.1 解析：从 PKCS#8 PrivateKeyInfo DER 中提取 PKCS#1 RSAPrivateKey DER。
     * <p>
     * 测试 oracle，不依赖被测代码的 wrapPkcs1ToPkcs8。
     * PKCS#8 结构：SEQUENCE { version INTEGER, algId SEQUENCE, privateKey OCTET STRING }
     */
    private static byte[] extractPkcs1FromPkcs8(byte[] pkcs8) {
        DerCursor c = new DerCursor(pkcs8);
        assertEquals(0x30, c.readTag(), "outer SEQUENCE tag");
        c.readLength();  // outer SEQUENCE length，内部顺序解析不需要
        assertEquals(0x02, c.readTag(), "version INTEGER tag");
        c.readContent();  // skip version
        assertEquals(0x30, c.readTag(), "algId SEQUENCE tag");
        c.readContent();  // skip AlgorithmIdentifier
        assertEquals(0x04, c.readTag(), "OCTET STRING tag");
        return c.readContent();  // PKCS#1 DER
    }

    /** 最小 DER 解析游标（测试 oracle，仅支持 PKCS#8 结构所需的 tag） */
    private static final class DerCursor {
        private final byte[] data;
        private int offset;

        DerCursor(byte[] data) {
            this.data = data;
            this.offset = 0;
        }

        int readTag() {
            return data[offset++] & 0xff;
        }

        int readLength() {
            int b = data[offset++] & 0xff;
            if (b < 0x80) {
                return b;
            }
            int numBytes = b & 0x7f;
            int len = 0;
            for (int i = 0; i < numBytes; i++) {
                len = (len << 8) | (data[offset++] & 0xff);
            }
            return len;
        }

        byte[] readContent() {
            int len = readLength();
            byte[] content = Arrays.copyOfRange(data, offset, offset + len);
            offset += len;
            return content;
        }
    }
}
