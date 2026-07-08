package yaoshu.token.flow;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.base.convert.Convert;

import java.util.LinkedHashMap;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.service.UserService;

/**
 * 组合业务流集成测试 — 多接口端到端串联验证。
 * <p>
 * 覆盖场景：UC-300~302。验证全链路端点串联调用不中断：
 * <ul>
 *   <li>UC-300: 用户生命周期（登录→self→设置→Token CRUD）</li>
 *   <li>UC-301: 管理员用户生命周期（创建→详情→更新→删除）</li>
 *   <li>UC-302: 管理员资源面板全链路 GET</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("组合业务流")
public class CompositeFlowIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String USER_NAME  = "test_flow_cf_user";
    private static final String USER_PWD   = "test_cf_123";
    private static final String ADMIN_NAME = "test_flow_cf_admin";
    private static final String ADMIN_PWD  = "test_cf_adm";

    private LoginResult userLogin;
    private LoginResult adminLogin;
    private RestTemplate authRestTemplate;

    @BeforeAll
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?)", USER_NAME, ADMIN_NAME);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String userHashed = userService.hashPassword(USER_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test CF User', 1, 1)", USER_NAME, userHashed);

        String adminHashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test CF Admin', 2, 1)", ADMIN_NAME, adminHashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        userLogin  = loginAndObtainToken(USER_NAME, USER_PWD);
        adminLogin = loginAndObtainToken(ADMIN_NAME, ADMIN_PWD);
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?)", USER_NAME, ADMIN_NAME);
        jdbcTemplate.execute("DELETE FROM tokens WHERE name LIKE 'test_%'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanTokens() {
        jdbcTemplate.execute("DELETE FROM tokens WHERE name LIKE 'test_%'");
    }

    // ======================== UC-300~302 ========================

    @Test
    @Order(1)
    @DisplayName("UC-300: 注册→登录→自助→Token生命周期")
    void uc300UserLifecycle() {
        LoginResult login = loginAndObtainToken(USER_NAME, USER_PWD);

        ResponseEntity<Map> selfResp = authGet(login, "/api/user/self");
        assertThat(selfResp.getStatusCode().is2xxSuccessful())
                .as("UC-300 Step1: GET self").isTrue();
        assertThat(selfResp.getBody().get("flag")).isEqualTo(true);

        Map<String, Object> selfBody = new LinkedHashMap<>();
        // display_name 列 varchar(20)，前缀+6位毫秒尾数=16字符 < 20
        selfBody.put("displayName", "UC300_Upd_" + (System.currentTimeMillis() % 1000000L));
        ResponseEntity<Map> putResp = authPut(login, "/api/user/self", selfBody);
        assertThat(putResp.getBody().get("flag"))
                .as("UC-300 Step2: PUT self").isEqualTo(true);

        ResponseEntity<Map> groupsResp = authGet(login, "/api/user/self/groups");
        assertThat(groupsResp.getBody().get("flag"))
                .as("UC-300 Step3: GET self/groups").isEqualTo(true);

        Map<String, Object> settingBody = new LinkedHashMap<>();
        settingBody.put("preferred_language", "en");
        ResponseEntity<Map> settingResp = authPut(login, "/api/user/setting", settingBody);
        assertThat(settingResp.getBody().get("flag"))
                .as("UC-300 Step4: PUT setting").isEqualTo(true);

        ResponseEntity<Map> modelsResp = authGet(login, "/api/user/models");
        assertThat(modelsResp.getBody().get("flag"))
                .as("UC-300 Step5: GET models").isEqualTo(true);

        ResponseEntity<Map> genTokenResp = authGet(login, "/api/user/token");
        assertThat(genTokenResp.getBody().get("flag"))
                .as("UC-300 Step6: GET user/token").isEqualTo(true);

        Map<String, Object> tokenBody = new LinkedHashMap<>();
        tokenBody.put("name", "test_cf_tok_" + System.currentTimeMillis());
        tokenBody.put("remain_quota", 1000);
        ResponseEntity<Map> createResp = authPost(login, "/api/token/", tokenBody);
        assertThat(createResp.getBody().get("flag"))
                .as("UC-300 Step7: POST token/").isEqualTo(true);
        int tokId = extractId(createResp);

        ResponseEntity<Map> listResp = authGet(login, "/api/token/?pageNum=1&pageSize=50");
        assertThat(listResp.getBody().get("flag"))
                .as("UC-300 Step8: GET token list").isEqualTo(true);

        ResponseEntity<Map> searchResp = authGet(login,
                "/api/token/search?keyword=test_cf&pageNum=1&pageSize=10");
        assertThat(searchResp.getBody().get("flag"))
                .as("UC-300 Step9: search token").isEqualTo(true);

        ResponseEntity<Map> detailResp = authGet(login, "/api/token/" + tokId);
        assertThat(detailResp.getBody().get("flag"))
                .as("UC-300 Step10: GET token detail").isEqualTo(true);

        Map<String, Object> updBody = new LinkedHashMap<>();
        updBody.put("id", tokId);
        updBody.put("name", "test_cf_tok_upd_" + System.currentTimeMillis());
        ResponseEntity<Map> updResp = authPut(login, "/api/token/", updBody);
        assertThat(updResp.getBody().get("flag"))
                .as("UC-300 Step11: PUT token").isEqualTo(true);

        ResponseEntity<Map> keyResp = authPost(login, "/api/token/" + tokId + "/key", null);
        assertThat(keyResp.getBody().get("flag"))
                .as("UC-300 Step12: GET token key").isEqualTo(true);

        Map<String, Object> batchKeyBody = new LinkedHashMap<>();
        batchKeyBody.put("ids", new int[]{tokId});
        ResponseEntity<Map> batchKeyResp = authPost(login, "/api/token/batch/keys", batchKeyBody);
        assertThat(batchKeyResp.getBody().get("flag"))
                .as("UC-300 Step13: batch keys").isEqualTo(true);

        ResponseEntity<Map> delResp = authDelete(login, "/api/token/" + tokId);
        assertThat(delResp.getBody().get("flag"))
                .as("UC-300 Step14: DELETE token").isEqualTo(true);

        ResponseEntity<Map> verifyResp = authGet(login, "/api/token/" + tokId);
        assertThat(verifyResp.getBody().get("flag"))
                .as("UC-300 Step15: verify delete → false")
                .isEqualTo(false);
    }

    @Test
    @Order(2)
    @DisplayName("UC-301: 管理员用户生命周期 — 创建→管理→删除")
    void uc301AdminUserLifecycle() {
        ResponseEntity<Map> listResp = authGet(adminLogin, "/api/user/?pageNum=1&pageSize=50");
        assertThat(listResp.getBody().get("flag"))
                .as("UC-301 Step1: list users").isEqualTo(true);

        ResponseEntity<Map> searchResp = authGet(adminLogin,
                "/api/user/search?keyword=test&pageNum=1&pageSize=10");
        assertThat(searchResp.getBody().get("flag"))
                .as("UC-301 Step2: search users").isEqualTo(true);

        // username 列 varchar(20)，前缀12+6位毫秒尾数=18字符 < 20
        String newName = "test_cf_mgd_" + (System.currentTimeMillis() % 1000000L);
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("username", newName);
        createBody.put("password", "mgd123");
        createBody.put("displayName", "Managed User");
        createBody.put("role", 1);
        ResponseEntity<Map> createResp = authPost(adminLogin, "/api/user/", createBody);
        assertThat(createResp.getBody().get("flag"))
                .as("UC-301 Step3: create user").isEqualTo(true);
        // Go CreateUser 返回空成功（无 data/id，对齐 Go 契约），通过 DB 查询获取新建用户 id
        int createdId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, newName);

        ResponseEntity<Map> detailResp = authGet(adminLogin, "/api/user/" + createdId);
        assertThat(detailResp.getBody().get("flag"))
                .as("UC-301 Step4: user detail").isEqualTo(true);

        Map<String, Object> updBody = new LinkedHashMap<>();
        updBody.put("id", createdId);
        updBody.put("displayName", "Updated Managed");
        ResponseEntity<Map> updResp = authPut(adminLogin, "/api/user/", updBody);
        assertThat(updResp.getBody().get("flag"))
                .as("UC-301 Step5: update user").isEqualTo(true);

        ResponseEntity<Map> delResp = authDelete(adminLogin, "/api/user/" + createdId);
        assertThat(delResp.getBody().get("flag"))
                .as("UC-301 Step6: delete user").isEqualTo(true);

        ResponseEntity<Map> verifyResp = authGet(adminLogin, "/api/user/" + createdId);
        assertThat(verifyResp.getBody().get("flag"))
                .as("UC-301 Step7: verify delete → false")
                .isEqualTo(false);
    }

    @Test
    @Order(3)
    @DisplayName("UC-302: 管理员资源面板 — 全链路 GET")
    void uc302AdminDashboard() {
        assertThat(authGet(adminLogin, "/api/channel/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/models/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/vendors/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/log/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/redemption/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/group/").getBody().get("flag")).isEqualTo(true);
        assertThat(authGet(adminLogin, "/api/deployments/?pageNum=1&pageSize=50").getBody().get("flag")).isEqualTo(true);
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

    private ResponseEntity<Map> authPut(LoginResult login, String path, Map<String, Object> body) {
        HttpHeaders headers = authHeaders(login);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.PUT, entity, Map.class);
    }

    private ResponseEntity<Map> authDelete(LoginResult login, String path) {
        HttpHeaders headers = authHeaders(login);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.DELETE, entity, Map.class);
    }

    @SuppressWarnings("unchecked")
    private int extractId(ResponseEntity<Map> resp) {
        Map<String, Object> result = resp.getBody();
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return data != null && data.get("id") != null
                ? ((Number) data.get("id")).intValue() : -1;
    }
}
