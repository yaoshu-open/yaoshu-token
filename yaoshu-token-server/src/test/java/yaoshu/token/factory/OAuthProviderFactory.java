package yaoshu.token.factory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth Provider 测试数据工厂。
 * <p>
 * 所有测试数据使用 test_ 前缀标识，遵循集成测试规范 §二。
 */
public final class OAuthProviderFactory {

    private OAuthProviderFactory() {}

    private static int seq = 0;

    /** 生成唯一的测试 slug */
    public static synchronized String uniqueSlug() {
        seq++;
        return "test-oauth-prov-" + seq;
    }

    /**
     * 构建创建 Provider 的请求体（最小必填字段）。
     */
    public static Map<String, Object> createRequest() {
        String slug = uniqueSlug();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Test OAuth Provider " + slug);
        body.put("slug", slug);
        body.put("clientId", "test-client-id-" + slug);
        body.put("clientSecret", "test-client-secret-" + slug);
        body.put("authorizationEndpoint", "https://example.com/oauth/authorize");
        body.put("tokenEndpoint", "https://example.com/oauth/token");
        body.put("userInfoEndpoint", "https://example.com/oauth/userinfo");
        return body;
    }

    /**
     * 构建更新 Provider 的请求体（仅修改名称）。
     */
    public static Map<String, Object> updateRequest(String newName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", newName);
        return body;
    }
}
