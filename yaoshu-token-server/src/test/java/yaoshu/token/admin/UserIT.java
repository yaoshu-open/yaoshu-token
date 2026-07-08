package yaoshu.token.admin;

import static org.assertj.core.api.Assertions.assertThat;
import ai.yue.library.base.convert.Convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import yaoshu.token.factory.UserFactory;
import yaoshu.token.service.UserService;

/**
 * 用户管理集成测试（Admin 端 CRUD）。
 * <p>
 * 覆盖场景：创建/查询/列表/搜索/更新/删除 + manage 操作（禁用/启用/配额增减）。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header，复用 ChannelIT 模式。管理端点位于 AdminController（/api/user/*）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String ADMIN_USER = "test_adm_ur_it";
    private static final String ADMIN_PWD = "test_admin_123";
    private Integer adminUserId;
    private String saToken;
    private RestTemplate authRestTemplate;

    @BeforeAll
    void setUpAdmin() throws Exception {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");

        jdbcTemplate.update(
                "INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String hashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test Admin', 3, 1)", ADMIN_USER, hashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        // 登录
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", ADMIN_USER);
        loginBody.put("password", ADMIN_PWD);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> loginEntity = new HttpEntity<>(loginBody, loginHeaders);

        ResponseEntity<String> loginResp = authRestTemplate.exchange(
                apiUrl("/api/user/login"), HttpMethod.POST, loginEntity, String.class);
        assertThat(loginResp.getStatusCode().is2xxSuccessful())
                .as("管理员登录应成功，响应: %s", loginResp.getBody()).isTrue();

        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginResult = Convert.toJSONObject(loginResp.getBody());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginData = (java.util.Map<String, Object>) loginResult.get("data");
        assertThat(loginData).as("登录响应 data 不应为 null").isNotNull();
        adminUserId = ((Number) loginData.get("id")).intValue();
        // 从 JSON 响应体取 token（后端 login 接口直接返回 token 值，通过 yaoshu-token Header 传递）
        this.saToken = (String) loginData.get("token");
        assertThat(this.saToken).as("登录响应 data 中应包含 token 字段").isNotNull();
    }

    @AfterAll
    void tearDownAdmin() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanUsers() {
        // 只清理测试用户（test_usr_ 前缀），不清理 admin 用户（test_adm_ 前缀）
        jdbcTemplate.execute("DELETE FROM users WHERE username LIKE 'test_usr_%'");
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void createUser() {
        Map<String, Object> body = UserFactory.createRequest();
        ResponseEntity<Map> resp = apiPost("/api/user/", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(2)
    void createUserDuplicate() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_dup"));
        ResponseEntity<Map> resp2 = apiPost("/api/user/", UserFactory.createRequest("test_usr_dup"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp2.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(3)
    void createUserMissingUsername() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", "test123");
        ResponseEntity<Map> resp = apiPost("/api/user/", body);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(4)
    void getUser() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_get"));
        int userId = findUserId("test_usr_get");

        ResponseEntity<Map> resp = authGet("/api/user/" + userId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("username")).isEqualTo("test_usr_get");
    }

    @Test
    @Order(5)
    void getUserNotFound() {
        ResponseEntity<Map> resp = authGet("/api/user/99999");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(6)
    void listUsers() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_list_1"));
        apiPost("/api/user/", UserFactory.createRequest("test_usr_list_2"));

        ResponseEntity<Map> resp = authGet("/api/user/?pageNum=1&pageSize=100");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(7)
    void searchUsers() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_search_a"));
        apiPost("/api/user/", UserFactory.createRequest("test_usr_search_b"));

        ResponseEntity<Map> resp = authGet("/api/user/search?keyword=search_a&pageNum=1&pageSize=50");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(8)
    void updateUser() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_update"));
        int userId = findUserId("test_usr_update");

        Map<String, Object> updateBody = UserFactory.updateRequest(userId, "Updated Display");
        ResponseEntity<Map> resp = apiPut("/api/user/", updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证更新已持久化
        ResponseEntity<Map> getResp = authGet("/api/user/" + userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> getData = (Map<String, Object>) getResult.get("data");
        assertThat(getData.get("displayName")).isEqualTo("Updated Display");
    }

    @Test
    @Order(9)
    void disableUser() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_disable"));
        int userId = findUserId("test_usr_disable");

        Map<String, Object> body = UserFactory.manageRequest(userId, "disable");
        ResponseEntity<Map> resp = apiPost("/api/user/manage", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证状态
        ResponseEntity<Map> getResp = authGet("/api/user/" + userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat(data.get("status")).isEqualTo(2);
    }

    @Test
    @Order(10)
    void enableUser() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_enable"));
        int userId = findUserId("test_usr_enable");

        // 先禁用
        apiPost("/api/user/manage", UserFactory.manageRequest(userId, "disable"));
        // 再启用
        ResponseEntity<Map> resp = apiPost("/api/user/manage", UserFactory.manageRequest(userId, "enable"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证状态
        ResponseEntity<Map> getResp = authGet("/api/user/" + userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat(data.get("status")).isEqualTo(1);
    }

    @Test
    @Order(11)
    void addQuota() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_quota"));
        int userId = findUserId("test_usr_quota");

        Map<String, Object> body = UserFactory.manageQuotaRequest(userId, "add", 5000);
        ResponseEntity<Map> resp = apiPost("/api/user/manage", body);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        ResponseEntity<Map> getResp = authGet("/api/user/" + userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat((Integer) data.get("quota")).isGreaterThanOrEqualTo(5000);
    }

    @Test
    @Order(12)
    void deleteUser() {
        apiPost("/api/user/", UserFactory.createRequest("test_usr_delete"));
        int userId = findUserId("test_usr_delete");

        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/user/" + userId, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证已删除
        ResponseEntity<Map> getResp = authGet("/api/user/" + userId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(13)
    void deleteUserNotFound() {
        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/user/99999", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    // ======================== 辅助方法 ========================

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        return headers;
    }

    private ResponseEntity<Map> authGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<Map> apiPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.postForEntity(apiUrl(path), entity, Map.class);
    }

    private ResponseEntity<Map> apiPut(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.PUT, entity, Map.class);
    }

    private ResponseEntity<Map> authExchange(HttpMethod method, String path,
                                              Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), method, entity, Map.class);
    }

    /**
     * 通过用户名查找用户 ID。
     */
    private int findUserId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, username);
    }
}
