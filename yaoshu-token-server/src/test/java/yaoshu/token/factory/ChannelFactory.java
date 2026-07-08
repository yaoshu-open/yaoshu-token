package yaoshu.token.factory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道测试数据工厂。
 * <p>
 * 所有测试数据使用 test_ 前缀标识，遵循集成测试规范 §二。
 */
public final class ChannelFactory {

    private ChannelFactory() {}

    /** 默认测试渠道名 */
    public static final String TEST_CHANNEL_NAME = "test_ch_001";

    /**
     * 构建创建渠道的请求体（最小必填字段）。
     */
    public static Map<String, Object> createRequest() {
        return createRequest(TEST_CHANNEL_NAME);
    }

    /**
     * 构建创建渠道的请求体（指定名称）。
     * <p>
     * 结构对齐 ChannelIPO.AddRequest：{mode, channel:{name,type,key,...}}
     */
    public static Map<String, Object> createRequest(String name) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("name", name);
        channel.put("type", 1);                    // OpenAI 兼容型
        channel.put("key", "test_sk-abc123def456");
        channel.put("status", 1);                  // 启用
        channel.put("baseUrl", "https://api.openai.com");
        channel.put("models", "gpt-4,gpt-3.5-turbo");
        channel.put("group", "test_group");
        channel.put("priority", 10L);
        channel.put("balance", 100.0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "single");
        body.put("channel", channel);
        return body;
    }

    /**
     * 构建创建渠道的请求体（带标签）。tag 写入 channel 内部。
     */
    public static Map<String, Object> createRequestWithTag(String name, String tag) {
        Map<String, Object> body = createRequest(name);
        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) body.get("channel");
        channel.put("tag", tag);
        return body;
    }

    /**
     * 构建更新渠道的请求体。
     */
    public static Map<String, Object> updateRequest(int id, String newName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", newName);
        return body;
    }

    /**
     * 构建更新渠道状态的请求体。
     */
    public static Map<String, Object> updateStatusRequest(int id, int status) {
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
}
