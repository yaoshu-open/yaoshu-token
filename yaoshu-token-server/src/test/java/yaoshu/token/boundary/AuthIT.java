package yaoshu.token.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.base.convert.Convert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权边界测试 —— Token 认证的异常场景覆盖（/v1/* 路径，TokenAuthFilter）。
 * <p>
 * 覆盖场景：
 * <ol>
 *   <li>无 Token → 401</li>
 *   <li>无效/伪造 Token → 401</li>
 *   <li>Token 已过期（expired_time 在过去）→ 401</li>
 *   <li>Token 配额已用尽（remain_quota=0，非无限配额）→ 401</li>
 *   <li>Token 关联用户被封禁（status != 1）→ 401</li>
 *   <li>有效 Token（正常认证通过）→ 期望上游错误（因无渠道），但至少通过 Token 认证</li>
 * </ol>
 * <p>
 * 测试数据直接通过 JdbcTemplate 插入数据库（绕过业务 API），因为本测试类验证 Filter 层的鉴权逻辑，
 * 而非 Service 层业务逻辑。遵循集成测试规范 §二.2 的例外条款。
 */
public class AuthIT extends BaseIntegrationTest {

    // ======================== 测试数据常量 ========================

    private static final String TEST_USERNAME = "test_auth_user";
    private static final String TEST_USER_PWD_HASH = "$2a$10$test_auth_user_hash_placeholder";

    // 有效 Token key（含 sk- 前缀作为整体存储与查询）
    private static final String VALID_KEY = "sk-testauthvalidaaaaaaaaaaaaaaaaa";
    private static final String EXPIRED_KEY = "sk-testauthexpaaaaaaaaaaaaaaaaa";
    private static final String EXHAUSTED_KEY = "sk-testauthexhbaaaaaaaaaaaaaaaaa";
    private static final String BANNED_USER_KEY = "sk-testauthbannedaaaaaaaaaaaaaa";

    private Integer userId;

    // ======================== 部署测试数据 ========================

    @BeforeEach
    void setUpTestData() {
        // 清理旧数据
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testauth%'");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);

        // 插入测试用户（status=1 正常）
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, quota) " +
                "VALUES (?, ?, 'Test Auth User', 1, 1, 100000)",
                TEST_USERNAME, TEST_USER_PWD_HASH);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USERNAME);

        long now = System.currentTimeMillis() / 1000;

        // 1) 有效 Token（status=1, 充足配额, 未过期）
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 10000, 0, -1, ?, ?, 'Valid Token')",
                VALID_KEY, userId, now, now);

        // 2) 已过期 Token（expired_time 在过去）
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 10000, 0, ?, ?, ?, 'Expired Token')",
                EXPIRED_KEY, userId, now - 86400, now - 86400, now - 86400);

        // 3) 配额耗尽 Token（remain_quota=0，非无限配额）
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 0, 0, -1, ?, ?, 'Exhausted Token')",
                EXHAUSTED_KEY, userId, now, now);

        // 4) 已封禁用户的 Token（单独创建一个 status=2 的用户）
        jdbcTemplate.update("INSERT INTO users (username, password, display_name, role, status, quota) " +
                "VALUES ('test_auth_bnd_user', ?, 'Banned User', 1, 2, 0)", TEST_USER_PWD_HASH);
        Integer bannedUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'test_auth_bnd_user'", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 10000, 0, -1, ?, ?, 'Banned User Token')",
                BANNED_USER_KEY, bannedUserId, now, now);
    }

    @AfterEach
    void tearDownTestData() {
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testauth%'");
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?)", TEST_USERNAME, "test_auth_bnd_user");
    }

    // ======================== 辅助方法 ========================

    /**
     * 构建 /v1/chat/completions 请求体（最小有效请求）。
     */
    private Map<String, Object> minimalRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-3.5-turbo");
        body.put("messages", java.util.List.of(
                Map.of("role", "user", "content", "hello")
        ));
        return body;
    }

    /**
     * 发送带 Authorization Header 的 /v1/chat/completions 请求。
     * <p>
     * 使用 byte[] 响应类型 + UTF-8 手动解码，规避 Filter 层响应未设置 charset 导致的中文乱码。
     */
    private ResponseEntity<byte[]> v1ChatRequest(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.set("Authorization", bearerToken);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(minimalRequestBody(), headers);
        return restTemplate.exchange(
                apiUrl("/v1/chat/completions"),
                HttpMethod.POST,
                entity,
                byte[].class);
    }

    /**
     * 从 OpenAI 格式错误响应中提取 error.message。
     */
    private String extractErrorMessage(ResponseEntity<byte[]> response) {
        if (response.getBody() == null || response.getBody().length == 0) return null;
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> errorBody = Convert.toJSONObject(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) errorBody.get("error");
        return error != null ? (String) error.get("message") : null;
    }

    // ======================== 测试用例 ========================

    @Test
    void noTokenShouldReturn401() {
        ResponseEntity<byte[]> resp = v1ChatRequest(null);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("未提供令牌");
    }

    @Test
    void invalidTokenShouldReturn401() {
        // 伪造不存在的 key
        ResponseEntity<byte[]> resp = v1ChatRequest("Bearer sk-nonexistentfakekey12345");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("令牌无效");
    }

    @Test
    void expiredTokenShouldReturn401() {
        // 注意：MockMvc 不适用，这里用 TestRestTemplate 验证已过期的 Token
        ResponseEntity<byte[]> resp = v1ChatRequest("Bearer " + EXPIRED_KEY);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("令牌已过期");
    }

    @Test
    void exhaustedQuotaTokenShouldReturn401() {
        ResponseEntity<byte[]> resp = v1ChatRequest("Bearer " + EXHAUSTED_KEY);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("令牌配额已用尽");
    }

    @Test
    void bannedUserTokenShouldReturn401() {
        ResponseEntity<byte[]> resp = v1ChatRequest("Bearer " + BANNED_USER_KEY);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("用户已被封禁");
    }

    @Test
    void validTokenShouldPassAuth() {
        // 有效 Token 应通过认证层 — 后续因无渠道配置可能失败，但不应是 401
        ResponseEntity<byte[]> resp = v1ChatRequest("Bearer " + VALID_KEY);

        // 不应返回 401（鉴权应通过）
        assertThat(resp.getStatusCode().value()).isNotEqualTo(401);

        // 预期返回其他错误（如渠道不可用 500 或 400），但 message 不应是鉴权错误
        if (resp.getBody() != null && resp.getBody().length > 0) {
            String errorMsg = extractErrorMessage(resp);
            if (errorMsg != null) {
                assertThat(errorMsg).doesNotContain("令牌无效", "未提供令牌", "配额已用尽", "令牌已过期", "用户已被封禁");
            }
        }
    }
}
