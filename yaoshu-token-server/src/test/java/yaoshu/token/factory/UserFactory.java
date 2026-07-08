package yaoshu.token.factory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户测试数据工厂。
 * <p>
 * 所有测试数据使用 test_ 前缀标识，遵循集成测试规范 §二。
 */
public final class UserFactory {

    private UserFactory() {}

    /** 默认测试用户名 */
    public static final String TEST_USER_NAME = "test_usr_001";

    /**
     * 构建创建用户的请求体（最小必填字段）。
     */
    public static Map<String, Object> createRequest() {
        return createRequest(TEST_USER_NAME);
    }

    /**
     * 构建创建用户的请求体（指定名称）。
     */
    public static Map<String, Object> createRequest(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", name);
        body.put("password", "test_pwd_123");
        body.put("displayName", "Test User");
        body.put("role", 1);
        return body;
    }

    /**
     * 构建创建用户的请求体（指定名称 + 角色 + 配额）。
     */
    public static Map<String, Object> createRequestWith(String name, int role, int quota) {
        Map<String, Object> body = createRequest(name);
        body.put("role", role);
        body.put("quota", quota);
        return body;
    }

    /**
     * 构建更新用户的请求体。
     */
    public static Map<String, Object> updateRequest(int id, String newDisplayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("displayName", newDisplayName);
        return body;
    }

    /**
     * 构建 manage 操作的请求体（disable/enable/delete/quota 等）。
     */
    public static Map<String, Object> manageRequest(int id, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("action", action);
        return body;
    }

    /**
     * 构建 manage 配额操作的请求体。
     */
    public static Map<String, Object> manageQuotaRequest(int id, String mode, int value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("action", "add_quota");
        body.put("mode", mode);
        body.put("value", value);
        return body;
    }
}
