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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 输入校验边界测试 —— 非法输入与越权访问的异常场景覆盖。
 * <p>
 * 覆盖场景：
 * <ol>
 *   <li>空 body（Content-Type: application/json 但 body 为空）</li>
 *   <li>非法 JSON 字符串</li>
 *   <li>缺失必填字段</li>
 *   <li>未认证访问受保护端点 → 401</li>
 *   <li>/v1/* 路径无 Token → 401</li>
 *   <li>正常有效请求（基线对照）</li>
 * </ol>
 */
public class InputValidationIT extends BaseIntegrationTest {

    // ======================== 测试数据 ========================

    private static final String TEST_USERNAME = "test_inputval_user";
    private static final String TEST_PWD_HASH = "$2a$10$test_inputval_hash_placeholder";
    private static final String VALID_TOKEN_KEY = "testinputvalidaaaaaaaaaaaaaaa";

    private Integer userId;

    @BeforeEach
    void setUpTestData() {
        // 清理旧数据
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testinputval%'");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);

        // 创建测试用户
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, quota) " +
                "VALUES (?, ?, 'InputVal User', 1, 1, 100000)",
                TEST_USERNAME, TEST_PWD_HASH);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USERNAME);

        long now = System.currentTimeMillis() / 1000;

        // 有效 Token
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 10000, 0, -1, ?, ?, 'Input Validation Token')",
                "sk-" + VALID_TOKEN_KEY, userId, now, now);
    }

    @AfterEach
    void tearDownTestData() {
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testinputval%'");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);
    }

    // ======================== 辅助方法 ========================

    /**
     * 发送 POST 请求到指定路径（JSON body 为字符串）。
     */
    private ResponseEntity<String> postJson(String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(apiUrl(path), HttpMethod.POST, entity, String.class);
    }

    /**
     * 发送 GET 请求（无鉴权 Header）。
     */
    private ResponseEntity<String> getUnauthenticated(String path) {
        return restTemplate.exchange(apiUrl(path), HttpMethod.GET, null, String.class);
    }

    /**
     * 发送带 Token 的 POST 请求到 /v1/chat/completions。
     */
    private ResponseEntity<String> v1ChatRequest(String bearerToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.set("Authorization", bearerToken);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                apiUrl("/v1/chat/completions"),
                HttpMethod.POST,
                entity,
                String.class);
    }

    /**
     * 从 OpenAI 格式响应中提取 error.message，或从标准 {@code {success, message}} 格式提取 message。
     */
    private String extractErrorMessage(ResponseEntity<String> response) {
        if (response.getBody() == null) return null;
        Map<String, Object> body = Convert.toJSONObject(response.getBody());
        // OpenAI 格式：{"error": {"message": "..."}}
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        if (error != null && error.get("message") != null) {
            return (String) error.get("message");
        }
        // 标准格式：{"success": false, "message": "..."}
        if (body.get("message") != null) {
            return (String) body.get("message");
        }
        return null;
    }

    // ======================== 测试用例 ========================

    @Test
    void emptyBodyShouldReturnError() {
        // 空 body POST 到 /api/user/login —— 验证不崩溃（非 500）
        ResponseEntity<String> resp = postJson("/api/user/login", "");

        assertThat(resp.getStatusCode().is5xxServerError())
                .as("空 body 不应导致服务端异常")
                .isFalse();
    }

    @Test
    void invalidJsonShouldReturnError() {
        // 非法 JSON 字符串 —— 验证不崩溃（非 500）
        ResponseEntity<String> resp = postJson("/api/user/login", "{invalid json!!!");

        assertThat(resp.getStatusCode().is5xxServerError())
                .as("非法 JSON 不应导致服务端异常")
                .isFalse();
    }

    @Test
    void missingRequiredFieldsShouldReturnError() {
        // 有 JSON 但缺失 username/password
        Map<String, Object> emptyMap = new LinkedHashMap<>();
        String jsonBody = Convert.toJSONString(emptyMap);

        ResponseEntity<String> resp = postJson("/api/user/login", jsonBody);

        // 缺失必填字段：业务层返回 success=false（HTTP 200 + 业务错误）
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("有效 JSON 应正常处理")
                .isTrue();
        // 响应体应包含错误信息（Result 格式 flag=false）
        if (resp.getBody() != null) {
            Map<String, Object> body = Convert.toJSONObject(resp.getBody());
            assertThat(body.get("flag")).isEqualTo(false);
        }
    }

    @Test
    void unauthenticatedAccessToProtectedEndpointShouldReturn401() {
        // GET /api/user/self 需要 UserAuth（minRole=1），无 session → 应 401
        ResponseEntity<String> resp = getUnauthenticated("/api/user/self");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void unauthenticatedAccessToAdminEndpointShouldReturn401() {
        // GET /api/channel/ 需要 AdminAuth（minRole=2），无 session → 应 401
        ResponseEntity<String> resp = getUnauthenticated("/api/channel/");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void v1EndpointWithoutTokenShouldReturn401() {
        // /v1/chat/completions 需要 TokenAuthFilter
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "test-model");

        ResponseEntity<String> resp = v1ChatRequest(null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        String errorMsg = extractErrorMessage(resp);
        assertThat(errorMsg).contains("未提供令牌");
    }

    @Test
    void v1EndpointWithValidTokenButNoBodyFieldsShouldReachRelayLayer() {
        // 带有效 Token 但 body 缺 messages → 应通过认证层，由 relay 层处理
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "nonexistent-model"); // 缺 messages

        ResponseEntity<String> resp = v1ChatRequest("Bearer sk-" + VALID_TOKEN_KEY, body);

        // 不应返回 401（鉴权通过），下游处理错误（可能是 400 或上游错误）
        assertThat(resp.getStatusCode().value()).isNotEqualTo(401);
    }

    @Test
    void validLoginRequestShouldSucceed() {
        // 有效登录请求（基线对照），预期返回提示用户名或密码错误（用户无真实密码）
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", TEST_USERNAME);
        loginBody.put("password", "wrong_password");

        ResponseEntity<String> resp = postJson("/api/user/login", Convert.toJSONString(loginBody));

        // 应正常处理（虽然因密码错误会返回失败，但不应是 4xx/5xx 协议层错误）
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("有效请求应返回 200（业务失败在 body 中表达）")
                .isTrue();
    }

    @Test
    void registerUsernameExceedingMaxLengthShouldReturnValidationError() {
        // Bug-01 回归：username 21 字符（超过 VARCHAR(20) 限制），应在 IPO @Size 校验层拦截
        // 不应到达 DB 层触发 MysqlDataTruncation 并泄露 SQL 详情
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("username", "a".repeat(21));
        registerBody.put("password", "TestPass1234");

        ResponseEntity<String> resp = postJson("/api/user/register?turnstile=", Convert.toJSONString(registerBody));

        // 断言1：不应是 5xx 服务器错误（Bug-01 原现象是 code=505）
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("超长 username 应被 IPO @Size 校验拦截，不应到达 DB 层触发 SQL 异常")
                .isFalse();

        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);

        String responseStr = resp.getBody();
        // 断言2：响应应包含友好校验提示（@Size message）
        assertThat(responseStr).contains("用户名长度不能超过 20 字符");
        // 断言3：响应不应泄露 SQL 详情（Bug-01 安全核心）
        assertThat(responseStr)
                .doesNotContain("Data truncation")
                .doesNotContain("INSERT INTO users")
                .doesNotContain("UserMapper")
                .doesNotContain("Sql错误");
    }

    @Test
    void registerUsernameAtMaxLengthBoundaryShouldPassValidation() {
        // 边界对照：username 正好 20 字符应通过 @Size 校验（到达业务层）
        // 用已存在的用户名使其在业务层失败，但不触发 SQL 异常
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("username", TEST_USERNAME); // 已存在用户名，20 字符内
        registerBody.put("password", "TestPass1234");

        ResponseEntity<String> resp = postJson("/api/user/register?turnstile=", Convert.toJSONString(registerBody));

        // 应正常处理（非 5xx），业务层返回"用户名已存在"之类的提示
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("合法长度 username 不应触发服务器错误")
                .isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        // 不应泄露 SQL 详情
        assertThat(resp.getBody())
                .doesNotContain("Data truncation")
                .doesNotContain("INSERT INTO users");
    }

    // ======================== IPO 专项审计边界测试（本轮新增） ========================

    /**
     * UserIPO.Register.email：非法邮箱格式应被 @Email 拦截。
     */
    @Test
    void registerInvalidEmailFormatShouldReturnValidationError() {
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("username", "vu_" + (System.currentTimeMillis() % 100000000L));
        registerBody.put("password", "TestPass1234");
        registerBody.put("email", "not-an-email");

        ResponseEntity<String> resp = postJson("/api/user/register?turnstile=", Convert.toJSONString(registerBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("邮箱格式不正确");
    }

    /**
     * UserIPO.Register.email：超长邮箱（>50）应被 @Size 拦截，对齐 DB users.email VARCHAR(50)。
     */
    @Test
    void registerEmailExceedingMaxLengthShouldReturnValidationError() {
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("username", "vu_" + (System.currentTimeMillis() % 100000000L));
        registerBody.put("password", "TestPass1234");
        // 构造 60 字符邮箱（local 50 + @x.com）
        registerBody.put("email", "a".repeat(50) + "@x.com");

        ResponseEntity<String> resp = postJson("/api/user/register?turnstile=", Convert.toJSONString(registerBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("邮箱长度不能超过 50 字符");
    }

    /**
     * UserIPO.Login.username：超长 username（>20）应被 @Size 拦截，防 DB 截断与 SQL 泄露。
     */
    @Test
    void loginUsernameExceedingMaxLengthShouldReturnValidationError() {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", "a".repeat(21));
        loginBody.put("password", "TestPass1234");

        ResponseEntity<String> resp = postJson("/api/user/login", Convert.toJSONString(loginBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("用户名长度不能超过 20 字符");
    }

    /**
     * UserIPO.Login.password：超长 password（>255）应被 @Size 拦截，防恶意超长输入 DoS。
     */
    @Test
    void loginPasswordExceedingMaxLengthShouldReturnValidationError() {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", TEST_USERNAME);
        loginBody.put("password", "a".repeat(256));

        ResponseEntity<String> resp = postJson("/api/user/login", Convert.toJSONString(loginBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("密码长度不能超过 255 字符");
    }

    /**
     * SetupIPO.username：超长 username（>12）应被 @Size 拦截。
     * max=12 沿用 Go setup.go 业务规则（比 DB varchar(20) 更严）。
     * 注意：setup 端点在已初始化系统中业务层会拒绝，但 @Valid 校验先于业务层触发。
     */
    @Test
    void setupUsernameExceedingMaxLengthShouldReturnValidationError() {
        Map<String, Object> setupBody = new LinkedHashMap<>();
        setupBody.put("username", "a".repeat(13));
        setupBody.put("password", "TestPass1234");
        setupBody.put("confirmPassword", "TestPass1234");

        ResponseEntity<String> resp = postJson("/api/setup", Convert.toJSONString(setupBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("用户名长度不能超过12个字符");
    }

    /**
     * SetupIPO.password：短密码（<8）应被 @Size(min=8) 拦截。
     */
    @Test
    void setupPasswordTooShortShouldReturnValidationError() {
        Map<String, Object> setupBody = new LinkedHashMap<>();
        setupBody.put("username", "validsetup");
        setupBody.put("password", "short");
        setupBody.put("confirmPassword", "short");

        ResponseEntity<String> resp = postJson("/api/setup", Convert.toJSONString(setupBody));

        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
        assertThat(resp.getBody()).isNotNull();
        Map<String, Object> body = Convert.toJSONObject(resp.getBody());
        assertThat(body.get("flag")).isEqualTo(false);
        assertThat(resp.getBody()).contains("密码长度至少为8个字符");
    }
}
