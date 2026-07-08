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
import yaoshu.token.factory.TokenFactory;
import yaoshu.token.service.UserService;

/**
 * Token 管理集成测试（Admin 端）。
 * <p>
 * 覆盖场景：创建/列表/搜索/获取/更新/删除/批量删除/查看密钥/批量获取密钥。
 * TokenController 使用 {@code request.getAttribute("id")} 获取当前用户，管理该用户的 tokens。
 * adminAuthFilter (role=2) 覆盖 /api/token/*。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header，复用 ChannelIT 模式。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TokenIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String ADMIN_USER = "test_adm_tk_it";
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
                "VALUES (?, ?, 'Test Admin', 2, 1)", ADMIN_USER, hashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

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
        saToken = (String) loginData.get("token");
    }

    @AfterAll
    void tearDownAdmin() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanTokens() {
        jdbcTemplate.execute("DELETE FROM tokens WHERE name LIKE 'test_tok_%'");
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void createToken() {
        Map<String, Object> body = TokenFactory.createRequest();
        ResponseEntity<Map> resp = apiPost("/api/token/", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证 token 已持久化（通过列表找到创建的 token）
        int tokenId = findTokenId(TokenFactory.TEST_TOKEN_NAME);
        assertThat(tokenId).isGreaterThan(0);
    }

    @Test
    @Order(2)
    void createTokenMissingName() {
        Map<String, Object> body = new LinkedHashMap<>();
        ResponseEntity<Map> resp = apiPost("/api/token/", body);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(3)
    void getAllTokens() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_list_1"));
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_list_2"));

        ResponseEntity<Map> resp = authGet("/api/token/?pageNum=1&pageSize=100");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("list")).isNotNull();
    }

    @Test
    @Order(4)
    void getToken() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_get"));
        int tokenId = findTokenId("test_tok_get");

        ResponseEntity<Map> resp = authGet("/api/token/" + tokenId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("name")).isEqualTo("test_tok_get");
        // key 应为掩码格式
        String maskedKey = (String) data.get("key");
        assertThat(maskedKey).startsWith("sk-");
    }

    @Test
    @Order(5)
    void getTokenNotFound() {
        ResponseEntity<Map> resp = authGet("/api/token/99999");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(6)
    void searchTokens() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_search_alpha"));
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_search_beta"));

        ResponseEntity<Map> resp = authGet("/api/token/search?keyword=alpha&pageNum=1&pageSize=50");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(7)
    void updateToken() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_update_old"));
        int tokenId = findTokenId("test_tok_update_old");

        Map<String, Object> updateBody = TokenFactory.updateRequest(tokenId, "test_tok_update_new");
        ResponseEntity<Map> resp = apiPut("/api/token/", updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证更新已持久化
        ResponseEntity<Map> getResp = authGet("/api/token/" + tokenId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat(data.get("name")).isEqualTo("test_tok_update_new");
    }

    @Test
    @Order(8)
    void updateTokenStatusOnly() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_status"));
        int tokenId = findTokenId("test_tok_status");

        Map<String, Object> statusBody = TokenFactory.statusOnlyRequest(tokenId, 2);
        ResponseEntity<Map> resp = apiExchange(HttpMethod.PUT,
                "/api/token/?status_only=true", statusBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证状态
        ResponseEntity<Map> getResp = authGet("/api/token/" + tokenId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat(data.get("status")).isEqualTo(2);
    }

    @Test
    @Order(9)
    void getTokenKey() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_key_view"));
        int tokenId = findTokenId("test_tok_key_view");

        ResponseEntity<Map> resp = apiPost("/api/token/" + tokenId + "/key", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("key")).isNotNull();
        assertThat((String) data.get("key")).startsWith("sk-");
    }

    @Test
    @Order(10)
    void batchGetKeys() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_batch_key_1"));
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_batch_key_2"));
        int id1 = findTokenId("test_tok_batch_key_1");
        int id2 = findTokenId("test_tok_batch_key_2");

        Map<String, Object> batchBody = TokenFactory.batchKeysRequest(id1, id2);
        ResponseEntity<Map> resp = apiPost("/api/token/batch/keys", batchBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(11)
    void deleteToken() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_delete"));
        int tokenId = findTokenId("test_tok_delete");

        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/token/" + tokenId, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证已删除
        ResponseEntity<Map> getResp = authGet("/api/token/" + tokenId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(12)
    void deleteTokenNotFound() {
        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/token/99999", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(13)
    void batchDelete() {
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_batch_del_1"));
        apiPost("/api/token/", TokenFactory.createRequest("test_tok_batch_del_2"));
        int id1 = findTokenId("test_tok_batch_del_1");
        int id2 = findTokenId("test_tok_batch_del_2");

        Map<String, Object> batchBody = TokenFactory.batchDeleteRequest(id1, id2);
        ResponseEntity<Map> resp = apiPost("/api/token/batch", batchBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    // ======================== 辅助方法 ========================

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

    private ResponseEntity<Map> apiExchange(HttpMethod method, String path,
                                             Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), method, entity, Map.class);
    }

    private ResponseEntity<Map> authExchange(HttpMethod method, String path,
                                              Map<String, Object> body) {
        return apiExchange(method, path, body);
    }

    /**
     * 通过 token 名称查找 token ID（属于当前 admin 用户）。
     */
    private int findTokenId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tokens WHERE name = ? AND user_id = ?",
                Integer.class, name, adminUserId);
    }
}
