package yaoshu.token.factory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token 测试数据工厂。
 * <p>
 * 所有测试数据使用 test_ 前缀标识，遵循集成测试规范 §二。
 */
public final class TokenFactory {

    private TokenFactory() {}

    /** 默认测试 Token 名称 */
    public static final String TEST_TOKEN_NAME = "test_tok_001";

    /**
     * 构建创建 Token 的请求体（最小必填字段）。
     */
    public static Map<String, Object> createRequest() {
        return createRequest(TEST_TOKEN_NAME);
    }

    /**
     * 构建创建 Token 的请求体（指定名称）。
     */
    public static Map<String, Object> createRequest(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        return body;
    }

    /**
     * 构建创建 Token 的请求体（指定名称 + 限额 + 无限额标记）。
     */
    public static Map<String, Object> createRequestFull(String name, int remainQuota, boolean unlimited) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("remainQuota", remainQuota);
        body.put("unlimitedQuota", unlimited);
        return body;
    }

    /**
     * 构建更新 Token 的请求体（修改名称）。
     */
    public static Map<String, Object> updateRequest(int id, String newName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", newName);
        return body;
    }

    /**
     * 构建更新 Token 状态的请求体（只改状态）。
     */
    public static Map<String, Object> statusOnlyRequest(int id, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("status", status);
        return body;
    }

    /**
     * 构建批量删除的请求体。
     */
    public static Map<String, Object> batchDeleteRequest(int... ids) {
        Map<String, Object> body = new LinkedHashMap<>();
        java.util.List<Integer> idList = new java.util.ArrayList<>();
        for (int id : ids) idList.add(id);
        body.put("ids", idList);
        return body;
    }

    /**
     * 构建批量获取密钥的请求体。
     */
    public static Map<String, Object> batchKeysRequest(int... ids) {
        return batchDeleteRequest(ids); // 同样的 ids 字段格式
    }
}
