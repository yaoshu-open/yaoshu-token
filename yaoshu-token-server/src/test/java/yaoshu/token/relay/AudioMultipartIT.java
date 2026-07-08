package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import yaoshu.token.BaseIntegrationTest;

/**
 * multipart 音频上传集成测试 — 验证 Session-39 第三轮修复的 AudioHandler multipart 透传。
 * <p>
 * 验证链路：multipart/form-data 请求 → AudioHandler 读取原始字节流透传 → mock 上游 → 响应返回。
 * 修复前 AudioHandler 未正确透传 multipart 请求体，导致音频转写请求失败。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AudioMultipartIT extends BaseIntegrationTest {

    private static final String TEST_USER = "audio_multipart_user";
    private static final String TEST_TOKEN_AUTH = "sk-testAudioMultipartKey32chars";
    private static final String TEST_MODEL = "whisper-1";

    private int testUserId;
    private int mockChannelId;
    private HttpServer mockUpstream;
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicInteger receivedBodySize = new AtomicInteger(0);

    @BeforeAll
    void setUpData() {
        try {
            mockUpstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockUpstream.createContext("/", this::handleMockUpstream);
            mockUpstream.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
            mockUpstream.start();
        } catch (Exception e) {
            throw new RuntimeException("启动 mock 上游失败", e);
        }

        long now = System.currentTimeMillis() / 1000;
        String mockBaseUrl = "http://127.0.0.1:" + mockUpstream.getAddress().getPort();

        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota) " +
                "VALUES (?, 'test', 'Audio User', 2, 1, 'default', 5000000) " +
                "ON DUPLICATE KEY UPDATE quota = 5000000",
                TEST_USER);
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USER);

        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`) " +
                "VALUES (?, ?, 1, 'audio-test-token', ?, 500000, 0, 0, 'default')",
                testUserId, TEST_TOKEN_AUTH, now);

        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (1, 'sk-mock-audio-key', 1, 'mock-audio-channel', ?, '" + mockBaseUrl + "', ?, 'default', 1) " +
                "ON DUPLICATE KEY UPDATE base_url = '" + mockBaseUrl + "'",
                now, TEST_MODEL);
        mockChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = 'mock-audio-channel'", Integer.class);

        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('default', ?, ?, 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1",
                TEST_MODEL, mockChannelId);
    }

    @AfterAll
    void cleanData() {
        if (mockUpstream != null) mockUpstream.stop(0);
        jdbcTemplate.update("DELETE FROM abilities WHERE channel_id = ?", mockChannelId);
        jdbcTemplate.update("DELETE FROM channels WHERE name = 'mock-audio-channel'");
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` = ?", TEST_TOKEN_AUTH);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USER);
    }

    private void handleMockUpstream(HttpExchange exchange) throws java.io.IOException {
        try {
            // 记录请求体大小（验证 multipart 透传：body 应 > 0）
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBodySize.set(body.length);

            // 记录所有 header 名称（排查 Content-Type 大小写问题）
            receivedContentType.set(exchange.getRequestHeaders().keySet().toString());

            String resp = "{\"text\":\"hello world\"}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * multipart 音频转写请求 → AudioHandler 透传原始字节 → mock 上游返回转写结果。
     * <p>
     * 验证 Session-39 第三轮修复：AudioHandler 在 isMultipart 时通过
     * req.getInputStream().readAllBytes() 读取原始请求体字节透传给上游。
     * <p>
     * CachingBodyFilter（order=-1300）+ resolve-lazily=true + 关闭 yue-library RepeatedlyReadFilter
     * 三管齐下，确保 multipart InputStream 在被 Tomcat 解析前已缓存到 Wrapper。
     */
    @Test
    void multipartAudioTranscription_rawBytesForwarded() {
        // 构造 multipart/form-data 请求体
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("model", TEST_MODEL);
        // 音频文件（mock 上游不解析实际内容，用最小 WAV 头模拟）
        ByteArrayResource audioResource = new ByteArrayResource(
                new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00}) {
            @Override
            public String getFilename() {
                return "test.wav";
            }
        };
        multipartBody.add("audio", audioResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(TEST_TOKEN_AUTH);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(multipartBody, headers);

        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/audio/transcriptions"), HttpMethod.POST, entity, String.class);

        // 中转应成功
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("multipart 音频转写应成功，实际: %s, body: %s", resp.getStatusCode(), resp.getBody())
                .isTrue();

        // 验证响应内容
        assertThat(resp.getBody())
                .as("响应应包含 mock 上游返回的转写文本")
                .contains("hello world");

        // 核心验证：multipart 请求体字节被透传到上游（body > 0）
        int bodySize = receivedBodySize.get();
        assertThat(bodySize)
                .as("multipart 请求体应被透传到上游（body size > 0，实际: %d）", bodySize)
                .isGreaterThan(0);

        // 诊断输出：上游收到的所有 header 名称（排查 Content-type 大小写匹配问题）
        System.out.println("[AudioMultipartIT] 上游收到 headers: " + receivedContentType.get()
                + " bodySize=" + bodySize);
    }
}
