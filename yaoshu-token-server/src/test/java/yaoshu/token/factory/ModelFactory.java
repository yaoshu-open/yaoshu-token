package yaoshu.token.factory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型测试数据工厂。
 * <p>
 * 所有测试数据使用 test_ 前缀标识，遵循集成测试规范 §二。
 */
public final class ModelFactory {

    private ModelFactory() {}

    /** 默认测试模型名 */
    public static final String TEST_MODEL_NAME = "test_model_001";

    /**
     * 构建创建模型的请求体（最小必填字段）。
     */
    public static Map<String, Object> createRequest() {
        return createRequest(TEST_MODEL_NAME);
    }

    /**
     * 构建创建模型的请求体（指定名称）。
     */
    public static Map<String, Object> createRequest(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelName", name);
        body.put("description", "Test model for integration test");
        body.put("vendorId", 1);
        body.put("status", 1);
        body.put("syncOfficial", 0);
        body.put("nameRule", 0);
        return body;
    }

    /**
     * 构建创建模型的请求体（带标签和图标）。
     */
    public static Map<String, Object> createRequestWithMeta(String name, String tags, String icon) {
        Map<String, Object> body = createRequest(name);
        body.put("tags", tags);
        body.put("icon", icon);
        return body;
    }

    /**
     * 构建更新模型的请求体。
     */
    public static Map<String, Object> updateRequest(int id, String newName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("modelName", newName);
        return body;
    }
}
