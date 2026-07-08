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
import yaoshu.token.service.UserService;

/**
 * 定价配置集成测试。
 * <p>
 * 覆盖场景：定价数据获取 + 响应结构验证 + 重置模型倍率。
 * 定价数据源自 ability/model_meta/vendor 四表 JOIN，测试验证响应结构而非具体数据值。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header，复用 ChannelIT 鉴权模式。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PricingIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private static final String ADMIN_USER = "test_adm_pc_it";
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
    void cleanPricing() {
        // 定价接口为只读，无需清理
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void getPricing() {
        ResponseEntity<Map> resp = authGet("/api/pricing");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证 data 是包含 pricing 等字段的对象
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("pricing")).isInstanceOf(List.class);
        assertThat(data.get("vendors")).isNotNull();
        assertThat(data.get("group_ratio")).isNotNull();
        assertThat(data.get("usable_group")).isNotNull();
        assertThat(data.get("supported_endpoint")).isNotNull();
        assertThat(data.get("auto_groups")).isNotNull();
        assertThat(data.get("pricing_version")).isNotNull();

        // 验证 pricing 列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pricingList = (List<Map<String, Object>>) data.get("pricing");
        // 定价列表可能为空（无渠道/模型配置时），不为 null 即可
        if (!pricingList.isEmpty()) {
            Map<String, Object> firstItem = pricingList.get(0);
            // 接口设计文档要求的 key 为 camelCase
            assertThat(firstItem.get("modelName")).isNotNull();
        }
    }

    @Test
    @Order(2)
    void getPricingEmptyParams() {
        // 验证带空参数也能正常返回
        ResponseEntity<Map> resp = authGet("/api/pricing");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(3)
    void resetModelRatio() {
        Map<String, Object> body = new LinkedHashMap<>();
        ResponseEntity<Map> resp = apiPost("/api/pricing/reset_model_ratio", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
        // yue-library Result 使用 msg 字段（非 message）
        assertThat(result.get("msg")).isNotNull();
    }

    @Test
    @Order(4)
    void getPricingAfterReset() {
        // 重置倍率后定价接口仍正常返回
        apiPost("/api/pricing/reset_model_ratio", new LinkedHashMap<>());

        ResponseEntity<Map> resp = authGet("/api/pricing");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("pricing_version")).isNotNull();
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
}
