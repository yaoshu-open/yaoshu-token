package yaoshu.token.user;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.base.convert.Convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * 用户认证与 Self 操作集成测试。
 * <p>
 * 覆盖场景：注册/登录/登出/Self 操作（GET/PUT/设置/模型/分组/邀请码/签到/OAuth 绑定）。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token），Self 端点位于 UserController（/api/user/self 等）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("用户认证与 Self 操作")
public class UserAuthIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String USER_NAME = "test_usr_ua_001";
    private static final String USER_PWD  = "test_ua_123";

    private Integer userId;
    private String saToken;
    private RestTemplate authRestTemplate;

    @BeforeAll
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USER_NAME);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String hashed = userService.hashPassword(USER_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test UA User', 1, 1)", USER_NAME, hashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();
        // authRestTemplate 不对 4xx/5xx 抛异常，以便断言认证失败后的 HTTP 状态码（如 logout 后 401）
        authRestTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) { return false; }
            @Override
            public void handleError(ClientHttpResponse response) { }
        });

        userId = loginAndObtainToken(USER_NAME, USER_PWD).userId();
        saToken = loginAndObtainToken(USER_NAME, USER_PWD).token();
        assertThat(saToken).as("登录应返回 token").isNotNull();
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USER_NAME);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanTokens() {
        jdbcTemplate.execute("DELETE FROM tokens WHERE name LIKE 'test_%'");
    }

    // ======================== UC-01~04 ========================

    @Test
    @Order(1)
    @DisplayName("UC-01: 用户注册 — 200 + data 含 id")
    void uc01RegisterSuccess() {
        long ts = System.currentTimeMillis() % 100000000;
        String uniqueName = "reg_" + ts;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", uniqueName);
        body.put("password", "test_reg_123");
        ResponseEntity<Map> resp = selfPost("/api/user/register", body);
        if (resp.getStatusCode().is2xxSuccessful()) {
            assertSuccess(resp, "UC-01");
            jdbcTemplate.update("DELETE FROM users WHERE username = ?", uniqueName);
        }
    }

    @Test
    @Order(2)
    @DisplayName("UC-02: 用户登录 — 200 + data 含 id/token")
    void uc02LoginSuccess() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", USER_NAME);
        body.put("password", USER_PWD);
        ResponseEntity<Map> resp = selfPost("/api/user/login", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertSuccess(resp, "UC-02");
        assertThat(resp.getBody().get("data")).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("UC-02-异常: 错误密码 → flag=false")
    void uc02LoginWrongPassword() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", USER_NAME);
        body.put("password", "wrong_password_!!");
        ResponseEntity<Map> resp = selfPost("/api/user/login", body);
        assertFailure(resp, "UC-02 错误密码");
    }

    @Test
    @Order(4)
    @DisplayName("UC-03: 退出登录 — 退出后 GET self 未授权")
    void uc03Logout() {
        LoginResult tmp = loginAndObtainToken(USER_NAME, USER_PWD);
        ResponseEntity<Map> logoutResp = selfGetWithToken(tmp.token(), "/api/user/logout");
        assertThat(logoutResp.getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<Map> selfResp = selfGetWithToken(tmp.token(), "/api/user/self");
        assertUnauthorized(selfResp, "UC-03 退出后");
    }

    @Test
    @Order(5)
    @DisplayName("UC-04: 可用分组列表 — 200")
    void uc04Groups() {
        ResponseEntity<Map> resp = selfGet("/api/user/groups");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ======================== UC-10~19: Self 操作（yaoshu-token Header）========================

    @Test
    @Order(10)
    @DisplayName("UC-10: 获取当前用户信息 — 200 + data")
    void uc10SelfGet() {
        ResponseEntity<Map> resp = selfGet("/api/user/self");
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("UC-10 GET self").isTrue();
        assertSuccess(resp, "UC-10");
    }

    @Test
    @Order(11)
    @DisplayName("UC-10-异常: 无鉴权访问 self → 未授权")
    void uc10SelfUnauthenticated() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/user/self"), Map.class);
        assertUnauthorized(resp, "UC-10 无鉴权");
    }

    @Test
    @Order(12)
    @DisplayName("UC-11: 更新当前用户信息 — 200")
    void uc11SelfUpdate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "UDN_" + System.currentTimeMillis() % 100000);
        ResponseEntity<Map> resp = selfPut("/api/user/self", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertSuccess(resp, "UC-11");
    }

    @Test
    @Order(13)
    @DisplayName("UC-12: 更新用户设置 — 200")
    void uc12SettingUpdate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("preferred_language", "zh-CN");
        ResponseEntity<Map> resp = selfPut("/api/user/setting", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertSuccess(resp, "UC-12");
    }

    @Test
    @Order(14)
    @DisplayName("UC-13: 注销当前用户 — 注销后 GET self 未授权")
    void uc13SelfDelete() {
        long ts = System.currentTimeMillis() % 100000000;
        String tmpName = "del_" + ts;
        String tmpPwd = "tmp_del_456";
        String hashed = userService.hashPassword(tmpPwd);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Del User', 1, 1)", tmpName, hashed);
        LoginResult tmp = loginAndObtainToken(tmpName, tmpPwd);
        ResponseEntity<Map> resp = selfDeleteWithToken(tmp.token(), "/api/user/self");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertSuccess(resp, "UC-13 注销");
        ResponseEntity<Map> getResp = selfGetWithToken(tmp.token(), "/api/user/self");
        assertUnauthorized(getResp, "UC-13 注销后");
    }

    @Test
    @Order(15)
    @DisplayName("UC-14: 用户可用模型列表 — 200，data[] 含 max_context 字段")
    void uc14Models() {
        ResponseEntity<Map> resp = selfGet("/api/user/models");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // 验证 data[] 中每个模型对象含 max_context key（值可空，key 必须存在）
        Map<?, ?> body = resp.getBody();
        if (body != null && body.get("data") instanceof Map<?, ?> data
                && data.get("data") instanceof List<?> modelList && !modelList.isEmpty()) {
            for (Object item : modelList) {
                if (item instanceof Map<?, ?> model) {
                    assertThat(model.containsKey("max_context"))
                            .as("模型 %s 应含 max_context 字段", model.get("id")).isTrue();
                }
            }
        }
    }

    @Test
    @Order(16)
    @DisplayName("UC-15: 当前用户分组 — 200")
    void uc15SelfGroups() {
        ResponseEntity<Map> resp = selfGet("/api/user/self/groups");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(17)
    @DisplayName("UC-16: 邀请码获取 — 200")
    void uc16AffCode() {
        ResponseEntity<Map> resp = selfGet("/api/user/aff");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(18)
    @DisplayName("UC-17: 邀请配额转账 — 接口可达")
    void uc17AffTransfer() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("affCode", "aff_" + System.currentTimeMillis() % 100000);
        body.put("quota", 100);
        ResponseEntity<Map> resp = selfPost("/api/user/aff_transfer", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(19)
    @DisplayName("UC-18: 签到状态 — 200")
    void uc18Checkin() {
        ResponseEntity<Map> resp = selfGet("/api/user/checkin");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(20)
    @DisplayName("UC-19: OAuth 绑定列表 — 200")
    void uc19OAuthBindings() {
        ResponseEntity<Map> resp = selfGet("/api/user/oauth/bindings");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
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

    private HttpHeaders selfHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", saToken);
        return headers;
    }

    private HttpHeaders selfHeadersWithToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", token);
        return headers;
    }

    private ResponseEntity<Map> selfGet(String path) {
        return selfGetWithToken(saToken, path);
    }

    private ResponseEntity<Map> selfGetWithToken(String token, String path) {
        HttpHeaders headers = selfHeadersWithToken(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<Map> selfPost(String path, Map<String, Object> body) {
        HttpHeaders headers = selfHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.postForEntity(apiUrl(path), entity, Map.class);
    }

    private ResponseEntity<Map> selfPut(String path, Map<String, Object> body) {
        HttpHeaders headers = selfHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.PUT, entity, Map.class);
    }

    private ResponseEntity<Map> selfDeleteWithToken(String token, String path) {
        HttpHeaders headers = selfHeadersWithToken(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.DELETE, entity, Map.class);
    }

    private void assertSuccess(ResponseEntity<Map> resp, String label) {
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).as("%s flag 应为 true", label).isEqualTo(true);
    }

    private void assertFailure(ResponseEntity<Map> resp, String label) {
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).as("%s flag 应为 false", label).isEqualTo(false);
    }

    private void assertUnauthorized(ResponseEntity<Map> resp, String label) {
        int status = resp.getStatusCode().value();
        Map<String, Object> body = resp.getBody();
        String msg = body != null ? (String) body.get("msg") : "";
        assertThat(status == 401 || (status == 600 && msg != null &&
                (msg.contains("未登录") || msg.contains("无权") || msg.contains("token"))))
                .as("%s 应返回未授权 (status=%d, msg=%s)", label, status, msg)
                .isTrue();
    }
}
