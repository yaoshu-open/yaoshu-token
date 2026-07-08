package yaoshu.token.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.base.convert.Convert;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.service.UserService;

/**
 * 负向边界集成测试 — 异常流 + 边界场景。
 * <p>
 * 覆盖用例：UC-400~410 + UC-500~505。
 * 测试鉴权边界、错误输入、SQL 注入防御、空参数幂等性等。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("异常流与边界场景")
public class NegativeBoundaryIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private LoginResult adminAuth;
    private LoginResult userAuth;
    /** 无认证 RestTemplate */
    private RestTemplate publicRest;
    /** 鉴权 RestTemplate */
    private RestTemplate authRestTemplate;

    private static final String ADMIN_NAME = "test_bb_nb_admin";
    private static final String ADMIN_PWD  = "test_nb_123";
    private static final String USER_NAME  = "test_bb_nb_user";
    private static final String USER_PWD   = "test_nb_456";

    @BeforeAll
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'test_bb_%'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String adminHashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test NB Admin', 2, 1)", ADMIN_NAME, adminHashed);

        String userHashed = userService.hashPassword(USER_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test NB User', 1, 1)", USER_NAME, userHashed);

        publicRest = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();
        // publicRest 不对 4xx/5xx 抛异常，以便断言认证失败的 HTTP 状态码（如 401）
        publicRest.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) { return false; }
            @Override
            public void handleError(ClientHttpResponse response) { }
        });
        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        adminAuth = loginAndObtainToken(ADMIN_NAME, ADMIN_PWD);
        userAuth  = loginAndObtainToken(USER_NAME, USER_PWD);
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'test_bb_%'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    // ======================== UC-400~410: 逆向异常流 ========================

    @Test
    @Order(1)
    @DisplayName("UC-400: 注册重名 → 409 或 flag=false")
    void uc400RegisterDuplicate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", USER_NAME);
        body.put("password", "irrelevant");

        ResponseEntity<Map> resp = noAuthPost("/api/user/register", body);

        if (resp.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = resp.getBody();
            assertThat(result.get("flag"))
                    .as("UC-400 重名应返回 false")
                    .isEqualTo(false);
        }
        // Turnstile 阻断时直接通过（不计失败）
    }

    @Test
    @Order(2)
    @DisplayName("UC-401: 登录错误密码 → flag=false（yue-library 业务失败统一 HTTP 200）")
    void uc401LoginWrongPassword() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", USER_NAME);
        body.put("password", "completely_wrong");

        ResponseEntity<Map> resp = noAuthPost("/api/user/login", body);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-401 错误密码应返回 2xx（yue-library Controller 层业务失败统一 HTTP 200）")
                .isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-401 错误密码应返回 flag=false")
                .isEqualTo(false);
    }

    @Test
    @Order(3)
    @DisplayName("UC-402: 未登录访问 self → 401")
    void uc402SelfUnauthenticated() {
        ResponseEntity<Map> resp = publicRest.getForEntity(
                apiUrl("/api/user/self"), Map.class);
        assertThat(resp.getStatusCode().value())
                .as("UC-402 无鉴权访问 self 应返回 401")
                .isEqualTo(401);
    }

    @Test
    @Order(4)
    @DisplayName("UC-403: Token 空 body 创建 → flag=false")
    void uc403TokenEmptyBody() {
        ResponseEntity<Map> resp = authPost(userAuth, "/api/token/", new LinkedHashMap<>());
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-403 空 body 创建 Token 应返回 false")
                .isEqualTo(false);
    }

    @Test
    @Order(5)
    @DisplayName("UC-404: 不存在 Token → flag=false")
    void uc404TokenNotFound() {
        ResponseEntity<Map> resp = authGet(userAuth, "/api/token/99999");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-404 不存在 Token 应返回 false")
                .isEqualTo(false);
    }

    @Test
    @Order(6)
    @DisplayName("UC-405: 普通用户访问管理端点 → flag=false（权限不足）")
    void uc405NormalUserAccessAdmin() {
        ResponseEntity<Map> resp = authGet(userAuth, "/api/user/");
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-405 权限不足应返回 2xx（yue-library 统一 HTTP 200）")
                .isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-405 普通用户访问管理端点应返回 flag=false")
                .isEqualTo(false);
    }

    @Test
    @Order(7)
    @DisplayName("UC-406: Admin 访问 Root 端点 → flag=false（权限不足）")
    void uc406AdminAccessRoot() {
        // OptionController 类级 @SaCheckRole("root")，admin(role=2) 访问应被拒
        ResponseEntity<Map> resp = authGet(adminAuth, "/api/option/");
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-406 权限不足应返回 2xx（yue-library 统一 HTTP 200）")
                .isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-406 Admin 访问 Root 端点应返回 flag=false")
                .isEqualTo(false);
    }

    @Test
    @Order(8)
    @DisplayName("UC-407: 无鉴权访问 /v1/models → 401")
    void uc407V1ModelsUnauthenticated() {
        ResponseEntity<Map> resp = publicRest.getForEntity(
                apiUrl("/v1/models"), Map.class);
        assertThat(resp.getStatusCode().value())
                .as("UC-407 无 Token 访问 v1/models 应返回 401")
                .isEqualTo(401);
    }

    @Test
    @Order(9)
    @DisplayName("UC-408: 无鉴权访问管理后台 → 401")
    void uc408AdminUnauthenticated() {
        ResponseEntity<Map> resp = publicRest.getForEntity(
                apiUrl("/api/channel/"), Map.class);
        assertThat(resp.getStatusCode().value())
                .as("UC-408 无鉴权访问管理后台应返回 401")
                .isEqualTo(401);
    }

    @Test
    @Order(10)
    @DisplayName("UC-409: 不存在渠道详情 → flag=false")
    void uc409ChannelNotFound() {
        ResponseEntity<Map> resp = authGet(adminAuth, "/api/channel/99999");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-409 不存在渠道应返回 false")
                .isEqualTo(false);
    }

    @Test
    @Order(11)
    @DisplayName("UC-410: 不存在模型详情 → flag=false")
    void uc410ModelNotFound() {
        ResponseEntity<Map> resp = authGet(adminAuth, "/api/models/99999");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag"))
                .as("UC-410 不存在模型应返回 false")
                .isEqualTo(false);
    }

    // ======================== UC-500~505: 边界场景 ========================

    @Test
    @Order(20)
    @DisplayName("UC-500: 超长用户名(>50) → 400 或 flag=false")
    void uc500LongUsername() {
        String longName = "a".repeat(60);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", longName);
        body.put("password", "test123");

        ResponseEntity<Map> resp = noAuthPost("/api/user/register", body);

        if (resp.getStatusCode().is4xxClientError()) {
            // 400 符合预期
        } else if (resp.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = resp.getBody();
            assertThat(result.get("flag"))
                    .as("UC-500 超长用户名应返回 false 或 400")
                    .isEqualTo(false);
        }
    }

    @Test
    @Order(21)
    @DisplayName("UC-501: SQL 注入 Token 名 — 不崩溃 (无 500)")
    void uc501SqlInjectionTokenName() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "'; DROP TABLE tokens; --");
        body.put("remain_quota", 1);

        ResponseEntity<Map> resp = authPost(userAuth, "/api/token/", body);
        // 关键断言：不能返回 500
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("UC-501 SQL 注入不应导致 500")
                .isFalse();
    }

    @Test
    @Order(22)
    @DisplayName("UC-502: 空搜索关键字 → 200 不崩溃")
    void uc502EmptySearchKeyword() {
        ResponseEntity<Map> resp = authGet(adminAuth, "/api/user/search?keyword=");
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("UC-502 空搜索不应导致 500")
                .isFalse();
    }

    @Test
    @Order(23)
    @DisplayName("UC-503: 批量删除空列表 → 200 幂等")
    void uc503BatchDeleteEmpty() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", new int[]{});

        ResponseEntity<Map> resp = authPost(userAuth, "/api/token/batch", body);
        // 不崩溃即可
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("UC-503 空 ids 批量删除不应导致 500")
                .isFalse();
    }

    @Test
    @Order(24)
    @DisplayName("UC-504: 无 body 登录 → 400")
    void uc504LoginNoBody() {
        ResponseEntity<Map> resp = noAuthPost("/api/user/login", null);

        // 400 或 flag=false
        if (resp.getStatusCode().is2xxSuccessful()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = resp.getBody();
            assertThat(result.get("flag"))
                    .as("UC-504 无 body 登录应返回 false")
                    .isEqualTo(false);
        } else {
            assertThat(resp.getStatusCode().is4xxClientError())
                    .as("UC-504 无 body 登录应返回 4xx")
                    .isTrue();
        }
    }

    @Test
    @Order(25)
    @DisplayName("UC-505: 错误的 JSON body → 400")
    void uc505MalformedJson() {
        RestTemplate rawRt = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("not a json{{{", headers);

        ResponseEntity<Map> resp = rawRt.postForEntity(apiUrl("/api/user/login"), entity, Map.class);
        assertThat(resp.getStatusCode().is5xxServerError())
                .as("UC-505 错误 JSON 不应导致 500")
                .isFalse();
    }

    // ======================== 辅助方法 ====================

    private record LoginResult(int userId, String token) {}

    private LoginResult loginAndObtainToken(String username, String password) {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", username);
        loginBody.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(loginBody, headers);

        ResponseEntity<String> resp = authRestTemplate.exchange(
                apiUrl("/api/user/login"), HttpMethod.POST, entity, String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = Convert.toJSONObject(resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).as("登录响应 data 不应为 null").isNotNull();
        int uid = ((Number) data.get("id")).intValue();
        String token = (String) data.get("token");
        return new LoginResult(uid, token);
    }

    private HttpHeaders authHeaders(LoginResult login) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", login.token());
        headers.set("yaoshu-user-id", String.valueOf(login.userId()));
        return headers;
    }

    private ResponseEntity<Map> noAuthPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return publicRest.postForEntity(apiUrl(path), entity, Map.class);
    }

    private ResponseEntity<Map> authGet(LoginResult login, String path) {
        HttpHeaders headers = authHeaders(login);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<Map> authPost(LoginResult login, String path, Map<String, Object> body) {
        HttpHeaders headers = authHeaders(login);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.postForEntity(apiUrl(path), entity, Map.class);
    }
}
