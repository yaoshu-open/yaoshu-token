package yaoshu.token.relay.channel.jimeng;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 即梦（火山引擎 CV）API 签名工具  * <p>
 * 实现火山引擎 HMAC-SHA256 V4 签名算法：构造规范请求 → 计算签名 → 生成 Authorization 头。
 * API Key 格式为 {@code accessKey|secretKey}。
 */
@Slf4j
public final class JimengSignHelper {

    private JimengSignHelper() {
    }

    private static final String REGION = "cn-north-1";
    private static final String SERVICE_NAME = "cv";
    private static final DateTimeFormatter X_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 对即梦请求签名。      * <p>
     * 计算并返回需要写入请求的签名头（Host/X-Date/X-Content-Sha256/Content-Type/Authorization），
     * 调用方将这些头设置到实际 HTTP 请求中。
     *
     * @param method    HTTP 方法（GET/POST）
     * @param url       完整请求 URL
     * @param bodyBytes 请求体字节（可为 null）
     * @param apiKey    格式 "accessKey|secretKey"
     * @param contentType 请求 Content-Type（为空时默认 application/json）
     * @return 待写入请求的签名头
     */
    public static Map<String, String> sign(String method, String url, byte[] bodyBytes,
                                           String apiKey, String contentType) throws Exception {
        String[] keyParts = apiKey == null ? new String[0] : apiKey.split("\\|");
        if (keyParts.length != 2) {
            throw new IllegalArgumentException("invalid api key format for jimeng: expected 'ak|sk'");
        }
        String accessKey = keyParts[0].trim();
        String secretKey = keyParts[1].trim();

        byte[] body = bodyBytes != null ? bodyBytes : new byte[0];
        String hexPayloadHash = sha256Hex(body);

        URI uri = URI.create(url);
        String host = uri.getHost();
        if (uri.getPort() != -1) {
            host = host + ":" + uri.getPort();
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String xDate = now.format(X_DATE_FMT);
        String shortDate = now.format(SHORT_DATE_FMT);

        String ct = (contentType == null || contentType.isEmpty()) ? "application/json" : contentType;

        // 规范查询字符串（按 key 排序，key/value 编码）
        String canonicalQueryString = buildCanonicalQueryString(uri.getRawQuery());

        // 参与签名的头（小写键名，已排序）
        Map<String, String> headersToSign = new TreeMap<>();
        headersToSign.put("host", host);
        headersToSign.put("x-date", xDate);
        headersToSign.put("x-content-sha256", hexPayloadHash);
        headersToSign.put("content-type", ct);

        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> e : headersToSign.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(":").append(e.getValue().trim()).append("\n");
        }
        String signedHeaders = String.join(";", headersToSign.keySet());

        String canonicalPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String canonicalRequest = String.join("\n",
                method,
                canonicalPath,
                canonicalQueryString,
                canonicalHeaders.toString(),
                signedHeaders,
                hexPayloadHash);

        String hexHashedCanonicalRequest = sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        String credentialScope = String.format("%s/%s/%s/request", shortDate, REGION, SERVICE_NAME);
        String stringToSign = String.join("\n",
                "HMAC-SHA256",
                xDate,
                credentialScope,
                hexHashedCanonicalRequest);

        // 派生签名密钥
        byte[] kDate = hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] kRegion = hmacSha256(kDate, REGION);
        byte[] kService = hmacSha256(kRegion, SERVICE_NAME);
        byte[] kSigning = hmacSha256(kService, "request");
        String signature = toHex(hmacSha256(kSigning, stringToSign));

        String authorization = String.format(
                "HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                accessKey, credentialScope, signedHeaders, signature);

        // 返回需要写入请求的头
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Host", host);
        result.put("X-Date", xDate);
        result.put("X-Content-Sha256", hexPayloadHash);
        result.put("Content-Type", ct);
        result.put("Authorization", authorization);
        return result;
    }

    /** 计算请求体的 SHA-256 十六进制摘要 */
    public static String payloadHash(byte[] body) throws Exception {
        return sha256Hex(body != null ? body : new byte[0]);
    }

    /** 构造规范查询字符串（按 key 排序，每个 key 的多值也排序） */
    private static String buildCanonicalQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        TreeMap<String, List<String>> params = new TreeMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            params.computeIfAbsent(urlDecode(k), kk -> new ArrayList<>()).add(urlDecode(v));
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            List<String> values = e.getValue();
            Collections.sort(values);
            for (String v : values) {
                parts.add(urlEncode(e.getKey()) + "=" + urlEncode(v));
            }
        }
        return String.join("&", parts);
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /** RFC3986 URL 编码（火山引擎要求 %20 而非 +） */
    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return toHex(md.digest(data));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
