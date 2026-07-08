package yaoshu.token.admin;

import static org.assertj.core.api.Assertions.assertThat;
import ai.yue.library.base.convert.Convert;

import java.util.LinkedHashMap;
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
import yaoshu.token.factory.ModelFactory;
import yaoshu.token.service.UserService;

/**
 * 模型元数据管理集成测试。
 * <p>
 * 覆盖场景：CRUD + 名称校验 + 搜索 + 上游同步预览 + 缺失模型查询。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header，复用 ChannelIT 鉴权模式。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ModelIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    // --- 鉴权基础设施 ---
    private static final String ADMIN_USER = "test_adm_md_it";
    private static final String ADMIN_PWD = "test_admin_123";
    private Integer adminUserId;
    private String saToken;
    private RestTemplate authRestTemplate;

    @BeforeAll
    void setUpAdmin() throws Exception {
        // 先清理旧测试数据，再创建
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");

        // 部署 PasswordLoginEnabled 选项
        jdbcTemplate.update(
                "INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        // 创建 admin 用户 (role=2)
        String hashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                "VALUES (?, ?, 'Test Admin', 2, 1)", ADMIN_USER, hashed);

        // 通过 Spring Boot RestTemplateBuilder 创建 RestTemplate
        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        // 登录获取 Sa-Token + adminUserId
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", ADMIN_USER);
        loginBody.put("password", ADMIN_PWD);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> loginEntity = new HttpEntity<>(loginBody, loginHeaders);

        ResponseEntity<String> loginResp = authRestTemplate.exchange(
                apiUrl("/api/user/login"),
                HttpMethod.POST,
                loginEntity,
                String.class);
        assertThat(loginResp.getStatusCode().is2xxSuccessful())
                .as("管理员登录应成功，响应: %s", loginResp.getBody())
                .isTrue();

        // 手动解析 JSON
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginResult = Convert.toJSONObject(loginResp.getBody());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> loginData = (java.util.Map<String, Object>) loginResult.get("data");
        assertThat(loginData).as("登录响应 data 不应为 null, raw=%s", loginResp.getBody()).isNotNull();
        adminUserId = ((Number) loginData.get("id")).intValue();
        saToken = (String) loginData.get("token");
    }

    @AfterAll
    void tearDownAdmin() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
    }

    @AfterEach
    void cleanModels() {
        jdbcTemplate.execute("DELETE FROM models WHERE model_name LIKE 'test_%'");
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void createModel() {
        Map<String, Object> body = ModelFactory.createRequest();
        ResponseEntity<Map> resp = apiPost("/api/models", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("id")).isNotNull();
        // 响应 JSON key 为 camelCase（modelName），非 snake_case（model_name）
        assertThat(data.get("modelName")).isEqualTo(ModelFactory.TEST_MODEL_NAME);
    }

    @Test
    @Order(2)
    void createModelMissingName() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "no name");

        ResponseEntity<Map> resp = apiPost("/api/models", body);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(3)
    void getModel() {
        ResponseEntity<Map> createResp = apiPost(
                "/api/models", ModelFactory.createRequest("test_model_get"));
        int modelId = extractId(createResp);

        ResponseEntity<Map> resp = authGet("/api/models/" + modelId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(4)
    void getModelNotFound() {
        ResponseEntity<Map> resp = authGet("/api/models/99999");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(5)
    void listModels() {
        apiPost("/api/models", ModelFactory.createRequest("test_model_list_1"));
        apiPost("/api/models", ModelFactory.createRequest("test_model_list_2"));

        ResponseEntity<Map> resp = authGet("/api/models?pageNum=1&pageSize=100");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(6)
    void searchModels() {
        apiPost("/api/models", ModelFactory.createRequest("test_model_search_alpha"));
        apiPost("/api/models", ModelFactory.createRequest("test_model_search_beta"));

        ResponseEntity<Map> resp = authGet("/api/models/search?keyword=search_alpha&pageNum=1&pageSize=50");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(7)
    void updateModel() {
        ResponseEntity<Map> createResp = apiPost(
                "/api/models", ModelFactory.createRequest("test_model_update_old"));
        int modelId = extractId(createResp);

        Map<String, Object> updateBody = ModelFactory.updateRequest(modelId, "test_model_update_new");
        ResponseEntity<Map> resp = apiPut("/api/models", updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证更新已持久化
        ResponseEntity<Map> getResp = authGet("/api/models/" + modelId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> getData = (Map<String, Object>) getResult.get("data");
        // 响应 JSON key 为 camelCase（modelName），非 snake_case（model_name）
        assertThat(getData.get("modelName")).isEqualTo("test_model_update_new");
    }

    @Test
    @Order(8)
    void updateModelNotFound() {
        Map<String, Object> updateBody = ModelFactory.updateRequest(99999, "ghost_model");
        ResponseEntity<Map> resp = apiPut("/api/models", updateBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(9)
    void deleteModel() {
        ResponseEntity<Map> createResp = apiPost(
                "/api/models", ModelFactory.createRequest("test_model_delete"));
        int modelId = extractId(createResp);

        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/models/" + modelId, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证已删除
        ResponseEntity<Map> getResp = authGet("/api/models/" + modelId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(10)
    void deleteModelNotFound() {
        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/models/99999", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(11)
    void syncUpstreamPreview() {
        ResponseEntity<Map> resp = authGet("/api/models/sync_upstream/preview");
        // 上游同步预览是只读操作，即使上游不可用也应返回成功或可预期的错误
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result).isNotNull();
    }

    @Test
    @Order(12)
    void getMissingModels() {
        ResponseEntity<Map> resp = authGet("/api/models/missing");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    // ======================== 辅助方法 ========================

    /**
     * GET 请求（带鉴权头：yaoshu-token + yaoshu-user-id）。
     */
    private ResponseEntity<Map> authGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.GET, entity, Map.class);
    }

    /**
     * POST 请求（带 JSON body + 鉴权头 + 鉴权头：yaoshu-token + yaoshu-user-id）。
     */
    private ResponseEntity<Map> apiPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.postForEntity(apiUrl(path), entity, Map.class);
    }

    /**
     * PUT 请求（带 JSON body + 鉴权头：yaoshu-token + yaoshu-user-id）。
     */
    private ResponseEntity<Map> apiPut(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.PUT, entity, Map.class);
    }

    /**
     * 通用请求（带鉴权头：yaoshu-token + yaoshu-user-id）。
     */
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
     * 从创建响应中提取模型 ID。
     */
    @SuppressWarnings("unchecked")
    private int extractId(ResponseEntity<Map> resp) {
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return ((Number) data.get("id")).intValue();
    }
}
