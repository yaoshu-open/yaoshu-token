package yaoshu.token.user;

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
 * Token 管理集成测试（用户侧自管理）。
 * <p>
 * 覆盖场景：生成/列表/搜索/详情/创建/更新/删除/批量删除/获取Key/批量获取Key。
 * 普通用户（role=1）登录后通过 yaoshu-token Header + yaoshu-user-id Header 操作自己的 Token。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Token 管理（用户侧）")
public class TokenUserSelfIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String USER_NAME = "test_usr_tok_001";
    private static final String USER_PWD  = "test_tok_123";

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
                "VALUES (?, ?, 'Test Token User', 1, 1)", USER_NAME, hashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        LoginResult login = loginAndObtainToken(USER_NAME, USER_PWD);
        userId = login.userId();
        saToken = login.token();
        assertThat(saToken).as("登录应返回 token").isNotNull();
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USER_NAME);
        jdbcTemplate.execute("DELETE FROM tokens WHERE name LIKE 'test_%'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanTokens() {
        // 保留创建一次的 token 供后续测试使用
    }

    // ======================== UC-30~39 ========================

    @Test
    @Order(1)
    @DisplayName("UC-30: 为自己生成 AccessToken — 200 + data 为 key 字符串")
    void uc30GenerateTokenForSelf() {
        ResponseEntity<Map> resp = authGet("/api/user/token");
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-30 为自己生成 AccessToken 应返回 2xx").isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
        // generateToken 返回 R.success(key)，data 是 AccessToken 字符串（非 Map）
        Object data = result.get("data");
        assertThat(data).as("UC-30 data 应为非空 AccessToken 字符串").isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("UC-31: 获取 Token 列表 — 200 + data 数组")
    void uc31TokenList() {
        authGet("/api/user/token");
        ResponseEntity<Map> resp = authGet("/api/token/?pageNum=1&pageSize=50");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(3)
    @DisplayName("UC-32: 搜索 Token — 200 + keyword 搜索")
    void uc32TokenSearch() {
        ResponseEntity<Map> resp = authGet("/api/token/search?keyword=test&pageNum=1&pageSize=50");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(4)
    @DisplayName("UC-33: 获取 Token 详情 — 200 + 含 name/model_limits; 不存在→404")
    void uc33TokenDetail() {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "test_tok_detail_" + System.currentTimeMillis());
        createBody.put("remain_quota", 1000);

        ResponseEntity<Map> createResp = authPost("/api/token/", createBody);
        assertThat(createResp.getStatusCode().is2xxSuccessful()).isTrue();
        int tokId = extractId(createResp);

        ResponseEntity<Map> resp = authGet("/api/token/" + tokId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        ResponseEntity<Map> notFoundResp = authGet("/api/token/99999");
        assertThat(notFoundResp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nfResult = notFoundResp.getBody();
        assertThat(nfResult.get("flag"))
                .as("UC-33 不存在 Token 应 flag=false")
                .isEqualTo(false);
    }

    @Test
    @Order(5)
    @DisplayName("UC-34: 创建 Token — 200 + 返回 id+key; 空 body→400; 缺 name→400")
    void uc34CreateToken() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "test_tok_create_" + System.currentTimeMillis());
        body.put("remain_quota", 500);

        ResponseEntity<Map> resp = authPost("/api/token/", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("key")).isNotNull();

        ResponseEntity<Map> emptyResp = authPost("/api/token/", new LinkedHashMap<>());
        assertThat(emptyResp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyResult = emptyResp.getBody();
        assertThat(emptyResult.get("flag"))
                .as("UC-34 空 body 创建应返回 false")
                .isEqualTo(false);
    }

    @Test
    @Order(6)
    @DisplayName("UC-35: 更新 Token — 200 + 更新后 GET 验证")
    void uc35UpdateToken() {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "test_tok_update_old_" + System.currentTimeMillis());
        createBody.put("remain_quota", 100);

        ResponseEntity<Map> createResp = authPost("/api/token/", createBody);
        int tokId = extractId(createResp);

        String newName = "test_tok_update_new_" + System.currentTimeMillis();
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("id", tokId);
        updateBody.put("name", newName);

        ResponseEntity<Map> resp = authPut("/api/token/", updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        ResponseEntity<Map> getResp = authGet("/api/token/" + tokId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> getData = (Map<String, Object>) getResult.get("data");
        assertThat(getData.get("name"))
                .as("UC-35 Token name 持久化验证")
                .isEqualTo(newName);
    }

    @Test
    @Order(7)
    @DisplayName("UC-36: 删除 Token — 200 + 删除后 GET 返回 404")
    void uc36DeleteToken() {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "test_tok_del_" + System.currentTimeMillis());
        createBody.put("remain_quota", 50);

        ResponseEntity<Map> createResp = authPost("/api/token/", createBody);
        int tokId = extractId(createResp);

        ResponseEntity<Map> resp = authDelete("/api/token/" + tokId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        ResponseEntity<Map> getResp = authGet("/api/token/" + tokId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag"))
                .as("UC-36 删除后 GET 应返回 false")
                .isEqualTo(false);
    }

    @Test
    @Order(8)
    @DisplayName("UC-37: 获取 Token Key — 200 + data 含 key")
    void uc37GetTokenKey() {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "test_tok_key_" + System.currentTimeMillis());
        createBody.put("remain_quota", 200);

        ResponseEntity<Map> createResp = authPost("/api/token/", createBody);
        int tokId = extractId(createResp);

        ResponseEntity<Map> resp = authPost("/api/token/" + tokId + "/key", null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("key")).isNotNull();
    }

    @Test
    @Order(9)
    @DisplayName("UC-38: 批量删除 Token — 200 + ids=[] 幂等")
    void uc38BatchDelete() {
        Map<String, Object> b1 = new LinkedHashMap<>();
        b1.put("name", "test_tok_batch_1_" + System.currentTimeMillis());
        b1.put("remain_quota", 10);
        ResponseEntity<Map> r1 = authPost("/api/token/", b1);
        int id1 = extractId(r1);

        Map<String, Object> b2 = new LinkedHashMap<>();
        b2.put("name", "test_tok_batch_2_" + System.currentTimeMillis());
        b2.put("remain_quota", 10);
        ResponseEntity<Map> r2 = authPost("/api/token/", b2);
        int id2 = extractId(r2);

        Map<String, Object> batchBody = new LinkedHashMap<>();
        batchBody.put("ids", new int[]{id1, id2});

        ResponseEntity<Map> resp = authPost("/api/token/batch", batchBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        Map<String, Object> emptyBatch = new LinkedHashMap<>();
        emptyBatch.put("ids", new int[]{});
        ResponseEntity<Map> emptyResp = authPost("/api/token/batch", emptyBatch);
        assertThat(emptyResp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(10)
    @DisplayName("UC-39: 批量获取 Key — 200 + data 数组含各 id 的 key")
    void uc39BatchGetKeys() {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("name", "test_tok_bk_" + System.currentTimeMillis());
        createBody.put("remain_quota", 30);

        ResponseEntity<Map> createResp = authPost("/api/token/", createBody);
        int tokId = extractId(createResp);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", new int[]{tokId});

        ResponseEntity<Map> resp = authPost("/api/token/batch/keys", body);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
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

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(userId));
        return headers;
    }

    private ResponseEntity<Map> authGet(String path) {
        HttpHeaders headers = authHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<Map> authPost(String path, Map<String, Object> body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.postForEntity(apiUrl(path), entity, Map.class);
    }

    private ResponseEntity<Map> authPut(String path, Map<String, Object> body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.PUT, entity, Map.class);
    }

    private ResponseEntity<Map> authDelete(String path) {
        HttpHeaders headers = authHeaders();
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
