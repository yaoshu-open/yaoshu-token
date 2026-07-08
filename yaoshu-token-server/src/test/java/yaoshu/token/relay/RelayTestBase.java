package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import ai.yue.library.base.convert.Convert;

import yaoshu.token.BaseIntegrationTest;

/**
 * Relay 集成测试公共基类 —— 所有 relay/ 包下 *IT.java 继承本类。
 * <p>
 * 提供:
 * <ul>
 * <li>数据部署生命周期（users / channels / abilities / tokens — @BeforeAll 写入，@AfterAll 清理）</li>
 * <li>请求助手方法（{@link #buildChatBody} / {@link #relayPost} / {@link #streamPost}）</li>
 * <li>公共测试用例（{@link #relayInvalidToken} — 所有渠道通用）</li>
 * <li>格式断言（{@link #assertOpenAIResponseFormat}）</li>
 * </ul>
 * <p>
 * 子类只需声明渠道特定常量并实现 6 个抽象方法，无需再写数据部署逻辑。
 * 新增测试渠道 = 新建 ~30 行子类（常量 + 方法实现 + 渠道特定测试用例）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RelayTestBase extends BaseIntegrationTest {

    protected static final String TEST_GROUP = "default";
    /** OpenAI 兼容型渠道类型 */
    protected static final int CHANNEL_TYPE = 1;

    /** 运行时测试数据 ID（@BeforeAll 写入后赋值） */
    protected int testUserId;
    protected int testChannelId;

    // ======================== 子类抽象方法（渠道特定常量） ========================

    /** 测试用户前缀（直接作为 username） */
    protected abstract String testUser();
    /** 测试 Token Key（32位+，不含 sk- 前缀，存储时由基类拼接 sk- 前缀） */
    protected abstract String testTokenKey();
    /** 测试渠道名 */
    protected abstract String testChannelName();
    /** 测试模型名 */
    protected abstract String testModel();
    /** 渠道上游 base_url */
    protected abstract String channelBaseUrl();
    /** 渠道 API Key（从子类 @Value 注入后返回） */
    protected abstract String channelKey();

    // ======================== 数据部署生命周期 ========================

    @BeforeAll
    final void baseSetUpRelayData() {
        testUserId = upsertTestUser();
        testChannelId = insertTestChannel();
        insertTestAbility();
        insertTestToken();
    }

    @AfterAll
    final void baseCleanRelayData() {
        jdbcTemplate.execute("DELETE FROM abilities WHERE `group` = '" + TEST_GROUP
                + "' AND model = '" + testModel() + "'");
        jdbcTemplate.execute("DELETE FROM channels WHERE name = '" + testChannelName() + "'");
        jdbcTemplate.execute("DELETE FROM tokens WHERE `key` = 'sk-" + testTokenKey() + "'");
        jdbcTemplate.execute("DELETE FROM users WHERE username = '" + testUser() + "'");
    }

    // ======================== 公共测试用例 ========================

    /** 无效 Token — 应返回 4xx（所有渠道通用） */
    @Test
    void relayInvalidToken() {
        Map<String, Object> body = buildChatBody(testModel(), "hello");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("sk-invalidnotexist");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);

        assertThat(resp.getStatusCode().is4xxClientError())
                .as("无效 Token 应返回 4xx").isTrue();
    }

    // ======================== 请求助手方法 ========================

    /** Bearer Token（sk- 前缀 + DB key，key 含 sk- 作为整体存储与查询） */
    protected String bearerToken() {
        return "sk-" + testTokenKey();
    }

    /**
     * 构建 Chat Completions 请求体。
     * 字段 camelCase — Jackson 全局蛇形序列化到上游。
     */
    protected Map<String, Object> buildChatBody(String model, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("maxTokens", 50);
        body.put("temperature", 0.0);
        return body;
    }

    /** Bearer Token 鉴权的非流式 POST */
    protected ResponseEntity<String> relayPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(apiUrl(path), HttpMethod.POST, entity, String.class);
    }

    /** 流式 POST 结果 */
    protected record StreamResult(int status, String contentType, String body) {}

    /**
     * 使用原生 HttpURLConnection 发起 SSE 流式 POST 请求。
     * <p>
     * HttpURLConnection.getErrorStream() 可读取 HTTP>=400 的响应体；
     * RestTemplate + SimpleClientHttpRequestFactory.getBody() 会在 >=400 时抛 IOException。
     */
    protected StreamResult streamPost(String bearerToken, Map<String, Object> body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl("/v1/chat/completions"))
                .toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);

        conn.getOutputStream().write(Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8));

        int status;
        try {
            status = conn.getResponseCode();
        } catch (Exception e) {
            return new StreamResult(-1, "", "IO_ERROR: " + e.getMessage());
        }
        String contentType = conn.getHeaderField("Content-Type");
        if (contentType == null) contentType = "";

        InputStream is;
        try {
            is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        } catch (Exception e) {
            return new StreamResult(status, contentType, "STREAM_UNAVAILABLE: " + e.getMessage());
        }
        byte[] bytes;
        try {
            bytes = is != null ? is.readAllBytes() : new byte[0];
        } catch (Exception e) {
            return new StreamResult(status, contentType, "READ_ERROR: " + e.getMessage());
        }
        String respBody = new String(bytes, StandardCharsets.UTF_8);
        conn.disconnect();
        return new StreamResult(status, contentType, respBody);
    }

    // ======================== 响应断言 ========================

    /** 断言 OpenAI 非流式响应基础格式（id / object / model / choices / message.role） */
    @SuppressWarnings("unchecked")
    protected void assertOpenAIResponseFormat(Map<String, Object> result) {
        assertThat(result.get("id")).as("id").isNotNull();
        assertThat(result.get("object")).as("object").isEqualTo("chat.completion");
        assertThat(result.get("model")).as("model").isNotNull();

        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        assertThat(choices).as("choices").isNotEmpty();
        Map<String, Object> firstChoice = choices.get(0);
        assertThat(firstChoice.get("finish_reason")).as("choice.finish_reason").isNotNull();

        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        assertThat(message).as("choice.message").isNotNull();
        assertThat(message.get("role")).as("message.role").isEqualTo("assistant");
    }

    // ======================== 数据部署实现 ========================

    private int upsertTestUser() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", testUser());
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`) " +
                "VALUES (?, 'test_pwd_not_used', 'Test Relay User', 2, 1, ?)",
                testUser(), TEST_GROUP);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, testUser());
        return id != null ? id : 0;
    }

    @SuppressWarnings("null")
    private int insertTestChannel() {
        long now = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (?, ?, 1, ?, ?, ?, ?, ?, 1)",
                CHANNEL_TYPE, channelKey(), testChannelName(), now, channelBaseUrl(), testModel(), TEST_GROUP);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = ?", Integer.class, testChannelName());
        return id != null ? id : 0;
    }

    @SuppressWarnings("null")
    private void insertTestAbility() {
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES (?, ?, ?, 1, 1, 10)",
                TEST_GROUP, testModel(), testChannelId);
    }

    private void insertTestToken() {
        long now = System.currentTimeMillis() / 1000;
        // key 含 sk- 前缀作为整体存储（与 generateKey 格式一致）
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (?, ?, 1, 'Test Relay Token', ?, 99999, 1, 0, ?, 0)",
                testUserId, "sk-" + testTokenKey(), now, TEST_GROUP);
    }
}
