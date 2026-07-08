package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.base.convert.Convert;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/**
 * 渠道配置化集成测试 —— 一个 @TestFactory 类遍历所有渠道配置。
 * <p>
 * 每条已启用渠道自动生成 4 个标准用例：非流式 200 + 非流式 401 + 流式 200 + 流式 401。
 * 新增渠道 = 在 channels() 方法中追加一行配置。
 * <p>
 * 所有用例结束后自动打印汇总报告（渠道维度 + 用例维度）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChannelsConfigIT extends BaseIntegrationTest {

    private static final String TEST_GROUP = "default";
    private static final int CHANNEL_TYPE = 1;

    // ======================== 渠道 API Key（敏感配置，@Value 注入） ========================

    @Value("${yaoshu.test.channel-key}")
    private String deepseekKey;

    @Value("${yaoshu.test.gateway-key}")
    private String gatewayKey;

    @Value("${yaoshu.test.vveai-key}")
    private String vveaiKey;

    // ======================== 结果收集器（线程安全） ========================

    private static final ConcurrentLinkedQueue<String> RESULTS = new ConcurrentLinkedQueue<>();

    /** 记录单条用例结果 */
    private static void record(String channel, String testCase, boolean pass, String detail) {
        String mark = pass ? "✅" : "❌";
        RESULTS.add(String.format("%s %-16s | %-12s | %s", mark, channel, testCase, detail));
    }

    /** 在所有 DynamicTest 执行完毕后打印汇总报告 */
    @AfterAll
    void printSummary() {
        List<String> lines = new ArrayList<>(RESULTS);
        if (lines.isEmpty()) {
            System.out.println("\n⚠️  无测试结果（所有渠道可能都 disabled）。");
            return;
        }

        // 按渠道分组统计
        Set<String> channels = new LinkedHashSet<>();
        Map<String, int[]> channelStats = new LinkedHashMap<>(); // [pass, fail]
        int totalPass = 0, totalFail = 0;
        for (String line : lines) {
            String channel = line.substring(2, 18).trim(); // "✅ ChannelName    |"
            channels.add(channel);
            boolean pass = line.startsWith("✅");
            channelStats.computeIfAbsent(channel, k -> new int[2]);
            if (pass) { channelStats.get(channel)[0]++; totalPass++; }
            else      { channelStats.get(channel)[1]++; totalFail++; }
        }

        // 汇总表
        System.out.println("\n" + "=".repeat(68));
        System.out.println("                    ChannelsConfigIT 汇总报告");
        System.out.println("=".repeat(68));
        System.out.printf("%-18s | %6s | %6s | %6s | %s%n",
                "渠道", "通过", "失败", "合计", "判定");
        System.out.println("-".repeat(68));
        for (String ch : channels) {
            int[] stats = channelStats.get(ch);
            int sum = stats[0] + stats[1];
            String verdict = stats[1] == 0 ? "✅ 通过" : "❌ 失败";
            System.out.printf("%-18s | %6d | %6d | %6d | %s%n",
                    ch, stats[0], stats[1], sum, verdict);
        }
        System.out.println("-".repeat(68));
        System.out.printf("%-18s | %6d | %6d | %6d |%n", "合计", totalPass, totalFail, totalPass + totalFail);
        System.out.println("=".repeat(68));

        // 逐条详情
        System.out.println("\n逐条明细：");
        for (String line : lines) {
            System.out.println("  " + line);
        }
        System.out.println();
    }

    // ======================== 渠道配置清单 ========================

    /**
     * 渠道配置（新增渠道只需在此追加一行）。
     */
    record ChannelConfig(String displayName, String userSuffix, String tokenKey,
                         String channelName, String model, String baseUrl,
                         String key, boolean disabled) {
        String testUser()    { return "test_relay_cc_" + userSuffix; }
        String bearerToken() { return "sk-" + tokenKey; }
    }

    private List<ChannelConfig> channels() {
        return List.of(
            new ChannelConfig("DeepSeek", "ds", "testrelayccdskeyabcdefghijklmnopqrs",
                    "test_ch_deepseek_cc", "deepseek-v4-flash", "https://api.deepseek.com",
                    deepseekKey, false),
            new ChannelConfig("yaoshu.cc GW", "gw", "testrelayccgwkeyabcdefghijklmnopqr",
                    "test_ch_yaoshugw_cc", "gpt-5.4-mini", "https://token.yaoshu.cc",
                    gatewayKey, false),
            new ChannelConfig("vveai", "vv", "testrelayccvvkeyabcdefghijklmnop",
                    "test_ch_vveai_cc", "gemini-2.5-flash-lite", "https://api.vveai.com",
                    vveaiKey, false)
        );
    }

    // ======================== @TestFactory：渠道标准用例工厂 ========================

    /**
     * 为每条已启用渠道生成 4 个 DynamicTest：
     * 非流式 200 → 非流式 401 → 流式 200 → 流式 401
     */
    @TestFactory
    Stream<DynamicTest> channelStandardTests() {
        return channels().stream()
                .filter(c -> !c.disabled())
                .flatMap(c -> Stream.of(
                        DynamicTest.dynamicTest(c.displayName() + " 非流式 200",
                                () -> testNonStreaming200(c)),
                        DynamicTest.dynamicTest(c.displayName() + " 非流式 401",
                                () -> testInvalidToken401(c)),
                        DynamicTest.dynamicTest(c.displayName() + " 流式 200",
                                () -> testStreaming200(c)),
                        DynamicTest.dynamicTest(c.displayName() + " 流式 401",
                                () -> testStreamInvalidToken401(c))
                ));
    }

    // ======================== 测试用例方法 ========================

    void testNonStreaming200(ChannelConfig c) {
        deployData(c);
        try {
            Map<String, Object> body = buildChatBody(c.model(), "Say hello in one word.");
            ResponseEntity<String> resp = relayPost(c.bearerToken(), body);
            String respBody = resp.getBody();

            if (!resp.getStatusCode().is2xxSuccessful()) {
                record(c.displayName(), "非流式 200", false,
                        "HTTP " + resp.getStatusCode() + " — "
                        + (respBody != null && respBody.length() > 200
                                ? respBody.substring(0, 200) : respBody));
            }
            assertThat(resp.getStatusCode().is2xxSuccessful())
                    .as("[%s] 非流式请求应返回 200，实际 status=%s",
                            c.displayName(), resp.getStatusCode())
                    .isTrue();

            Map<String, Object> result = Convert.toJSONObject(respBody);
            assertOpenAIResponseFormat(result, c.displayName());

            assertThat(result.get("created")).as("[%s] created", c.displayName()).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> firstChoice =
                    ((List<Map<String, Object>>) result.get("choices")).get(0);
            assertThat(firstChoice.get("index")).as("[%s] choice.index", c.displayName()).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            assertThat(message.get("content")).as("[%s] message.content", c.displayName()).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> usage = (Map<String, Object>) result.get("usage");
            assertThat(usage).as("[%s] usage", c.displayName()).isNotNull();
            assertThat(usage.get("prompt_tokens")).as("[%s] usage.prompt_tokens", c.displayName()).isNotNull();
            assertThat(usage.get("completion_tokens")).as("[%s] usage.completion_tokens", c.displayName()).isNotNull();
            assertThat(usage.get("total_tokens")).as("[%s] usage.total_tokens", c.displayName()).isNotNull();

            record(c.displayName(), "非流式 200", true, "OK");
        } catch (AssertionError e) {
            // record already called above for the non-2xx branch; catch here for
            // assertion failures on response format fields
            throw e;
        } finally {
            cleanupData(c);
        }
    }

    void testInvalidToken401(ChannelConfig c) {
        deployData(c);
        try {
            Map<String, Object> body = buildChatBody(c.model(), "hello");
            ResponseEntity<String> resp = relayPost("sk-invalidnotexist", body);

            boolean pass = resp.getStatusCode().is4xxClientError();
            record(c.displayName(), "非流式 401", pass,
                    pass ? "HTTP " + resp.getStatusCode().value()
                         : "HTTP " + resp.getStatusCode().value() + " (期望 4xx)");

            assertThat(pass)
                    .as("[%s] 无效 Token 应返回 4xx，实际 status=%s",
                            c.displayName(), resp.getStatusCode())
                    .isTrue();
        } finally {
            cleanupData(c);
        }
    }

    void testStreaming200(ChannelConfig c) throws Exception {
        deployData(c);
        try {
            Map<String, Object> body = buildChatBody(c.model(), "Say hello in one word.");
            body.put("stream", true);

            StreamResult result = streamPost(c.bearerToken(), body);

            boolean statusOk = result.status() == 200;
            if (!statusOk) {
                record(c.displayName(), "流式 200", false,
                        "HTTP " + result.status() + " — "
                        + (result.body().length() > 150
                                ? result.body().substring(0, 150) : result.body()));
            }
            assertThat(statusOk)
                    .as("[%s] 流式请求应返回 200，实际 status=%d",
                            c.displayName(), result.status())
                    .isTrue();

            assertThat(result.contentType().toLowerCase())
                    .as("[%s] 流式响应 Content-Type 应为 text/event-stream", c.displayName())
                    .contains("text/event-stream");

            assertThat(result.body())
                    .as("[%s] SSE 响应应包含 'data:' 前缀", c.displayName())
                    .contains("data:");

            assertThat(result.body())
                    .as("[%s] SSE 响应应以 [DONE] 标记结束", c.displayName())
                    .contains("[DONE]");

            record(c.displayName(), "流式 200", true, "OK");
        } catch (AssertionError e) {
            // record already called for non-200 status; rethrow for format failures
            throw e;
        } finally {
            cleanupData(c);
        }
    }

    void testStreamInvalidToken401(ChannelConfig c) throws Exception {
        deployData(c);
        try {
            Map<String, Object> body = buildChatBody(c.model(), "hello");
            body.put("stream", true);

            StreamResult result = streamPost("sk-invalidnotexist", body);

            boolean pass = result.status() >= 400;
            record(c.displayName(), "流式 401", pass,
                    pass ? "HTTP " + result.status()
                         : "HTTP " + result.status() + " (期望 >=400)");

            assertThat(pass)
                    .as("[%s] 无效 Token 流式请求应返回 >= 400，实际 status=%d",
                            c.displayName(), result.status())
                    .isTrue();
        } finally {
            cleanupData(c);
        }
    }

    // ======================== 数据部署 ========================

    private void deployData(ChannelConfig c) {
        int userId = upsertTestUser(c);
        int channelId = insertChannel(c);
        insertAbility(c, channelId);
        insertToken(c, userId);
    }

    private int upsertTestUser(ChannelConfig c) {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", c.testUser());
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`) " +
                "VALUES (?, 'test_pwd_not_used', 'Test Relay User', 2, 1, ?)",
                c.testUser(), TEST_GROUP);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, c.testUser());
        return id != null ? id : 0;
    }

    @SuppressWarnings("null")
    private int insertChannel(ChannelConfig c) {
        long now = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (?, ?, 1, ?, ?, ?, ?, ?, 1)",
                CHANNEL_TYPE, c.key(), c.channelName(), now, c.baseUrl(), c.model(), TEST_GROUP);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = ?", Integer.class, c.channelName());
        return id != null ? id : 0;
    }

    @SuppressWarnings("null")
    private void insertAbility(ChannelConfig c, int channelId) {
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES (?, ?, ?, 1, 1, 10)",
                TEST_GROUP, c.model(), channelId);
    }

    private void insertToken(ChannelConfig c, int userId) {
        long now = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (?, ?, 1, 'Test Relay Token', ?, 99999, 1, 0, ?, 0)",
                userId, "sk-" + c.tokenKey(), now, TEST_GROUP);
    }

    private void cleanupData(ChannelConfig c) {
        jdbcTemplate.execute("DELETE FROM abilities WHERE `group` = '" + TEST_GROUP
                + "' AND model = '" + c.model() + "'");
        jdbcTemplate.execute("DELETE FROM channels WHERE name = '" + c.channelName() + "'");
        jdbcTemplate.execute("DELETE FROM tokens WHERE `key` = 'sk-" + c.tokenKey() + "'");
        jdbcTemplate.execute("DELETE FROM users WHERE username = '" + c.testUser() + "'");
    }

    // ======================== 请求助手 ========================

    private Map<String, Object> buildChatBody(String model, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("maxTokens", 50);
        body.put("temperature", 0.0);
        return body;
    }

    private ResponseEntity<String> relayPost(String bearerToken, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        return restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private record StreamResult(int status, String contentType, String body) {}

    /**
     * 使用原生 HttpURLConnection 发起 SSE 流式 POST。
     * HttpURLConnection.getErrorStream() 可读取 HTTP>=400 的响应体。
     */
    private StreamResult streamPost(String bearerToken, Map<String, Object> body) throws Exception {
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

    @SuppressWarnings("unchecked")
    private void assertOpenAIResponseFormat(Map<String, Object> result, String displayName) {
        assertThat(result.get("id")).as("[%s] id", displayName).isNotNull();
        assertThat(result.get("object")).as("[%s] object", displayName).isEqualTo("chat.completion");
        assertThat(result.get("model")).as("[%s] model", displayName).isNotNull();

        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        assertThat(choices).as("[%s] choices", displayName).isNotEmpty();
        Map<String, Object> firstChoice = choices.get(0);
        assertThat(firstChoice.get("finish_reason")).as("[%s] choice.finish_reason", displayName).isNotNull();

        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        assertThat(message).as("[%s] choice.message", displayName).isNotNull();
        assertThat(message.get("role")).as("[%s] message.role", displayName).isEqualTo("assistant");
    }
}
