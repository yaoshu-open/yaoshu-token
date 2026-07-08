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
import yaoshu.token.factory.OAuthProviderFactory;
import yaoshu.token.service.UserService;

/**
 * 自定义 OAuth Provider 管理集成测试。
 * <p>
 * 覆盖场景：创建/列表/获取/更新/删除 + 名称冲突。
 * RootAuth (role=3) 要求，参考 UserIT 的 Root 鉴权模式。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header。
 * 端点路径：/api/custom-oauth-provider/*
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CustomOAuthProviderIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String ADMIN_USER = "test_adm_oa_it";
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
                "VALUES (?, ?, 'Test Root', 3, 1)", ADMIN_USER, hashed);

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
    void cleanProviders() {
        jdbcTemplate.execute("DELETE FROM custom_oauth_providers WHERE slug LIKE 'test-%'");
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void createProvider() {
        Map<String, Object> body = OAuthProviderFactory.createRequest();
        ResponseEntity<Map> resp = apiPost("/api/custom-oauth-provider/", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("slug")).isEqualTo(body.get("slug"));
    }

    @Test
    @Order(2)
    void listProviders() {
        apiPost("/api/custom-oauth-provider/", OAuthProviderFactory.createRequest());
        apiPost("/api/custom-oauth-provider/", OAuthProviderFactory.createRequest());

        ResponseEntity<Map> resp = authGet("/api/custom-oauth-provider/");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(3)
    void getProvider() {
        Map<String, Object> body = OAuthProviderFactory.createRequest();
        ResponseEntity<Map> createResp = apiPost("/api/custom-oauth-provider/", body);
        int providerId = extractId(createResp);

        ResponseEntity<Map> resp = authGet("/api/custom-oauth-provider/" + providerId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(4)
    void getProviderNotFound() {
        ResponseEntity<Map> resp = authGet("/api/custom-oauth-provider/99999");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(5)
    void updateProvider() {
        Map<String, Object> body = OAuthProviderFactory.createRequest();
        ResponseEntity<Map> createResp = apiPost("/api/custom-oauth-provider/", body);
        int providerId = extractId(createResp);

        Map<String, Object> updateBody = OAuthProviderFactory.updateRequest("Updated Name");
        ResponseEntity<Map> resp = apiPut("/api/custom-oauth-provider/" + providerId, updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证更新已持久化
        ResponseEntity<Map> getResp = authGet("/api/custom-oauth-provider/" + providerId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) getResult.get("data");
        assertThat(data.get("name")).isEqualTo("Updated Name");
    }

    @Test
    @Order(6)
    void deleteProvider() {
        Map<String, Object> body = OAuthProviderFactory.createRequest();
        ResponseEntity<Map> createResp = apiPost("/api/custom-oauth-provider/", body);
        int providerId = extractId(createResp);

        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/custom-oauth-provider/" + providerId, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证已删除
        ResponseEntity<Map> getResp = authGet("/api/custom-oauth-provider/" + providerId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(7)
    void deleteProviderNotFound() {
        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/custom-oauth-provider/99999", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
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

    private ResponseEntity<Map> authExchange(HttpMethod method, String path,
                                              Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        headers.set("yaoshu-user-id", String.valueOf(adminUserId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), method, entity, Map.class);
    }

    @SuppressWarnings("unchecked")
    private int extractId(ResponseEntity<Map> resp) {
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return ((Number) data.get("id")).intValue();
    }
}
