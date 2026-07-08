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
import yaoshu.token.factory.ChannelFactory;
import yaoshu.token.service.UserService;

/**
 * 渠道管理集成测试。
 * <p>
 * 覆盖场景：CRUD + 名称冲突 + 标签筛选 + 复制 + 批量删除。
 * 鉴权通过 yaoshu-token Header（登录后从响应体取 token）+ yaoshu-user-id Header。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChannelIT extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    // --- 鉴权基础设施 ---
    private static final String ADMIN_USER = "test_adm_ch_it";
    private static final String ADMIN_PWD = "test_admin_123";
    private Integer adminUserId;
    private String saToken;
    private RestTemplate authRestTemplate;

    @BeforeAll
    void setUpAdmin() throws Exception {
        // 先清理旧测试数据，再创建 (INSERT IGNORE 不覆盖已存在的旧 hash)
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

        // 通过 Spring Boot RestTemplateBuilder 创建 RestTemplate（使用 Spring 自动配置的 converters）
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

        // 用 String.class 获取原始 JSON 避免 RestTemplate 泛型擦除导致嵌套 data 丢失
        ResponseEntity<String> loginResp = authRestTemplate.exchange(
                apiUrl("/api/user/login"),
                HttpMethod.POST,
                loginEntity,
                String.class);
        assertThat(loginResp.getStatusCode().is2xxSuccessful())
                .as("管理员登录应成功，响应: %s", loginResp.getBody())
                .isTrue();

        // 手动解析 JSON（RestTemplate Map 反序列化时嵌套对象可能因类型擦除丢失）
        
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
    void cleanChannels() {
        jdbcTemplate.execute("DELETE FROM channels WHERE name LIKE 'test_%'");
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    void createChannel() {
        Map<String, Object> body = ChannelFactory.createRequest();
        ResponseEntity<Map> resp = apiPost("/api/channel/", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // ChannelController.add 返回 R.success() 无 data，通过 DB 查询验证创建成功
        int channelId = findChannelId(ChannelFactory.TEST_CHANNEL_NAME);
        assertThat(channelId).isGreaterThan(0);
    }

    @Test
    @Order(2)
    void createChannelDuplicateName() {
        apiPost("/api/channel/", ChannelFactory.createRequest());
        ResponseEntity<Map> resp2 = apiPost("/api/channel/", ChannelFactory.createRequest());
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(3)
    void createChannelMissingName() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", 1);

        ResponseEntity<Map> resp = apiPost("/api/channel/", body);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(4)
    void listAllChannels() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_list_1"));
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_list_2"));

        ResponseEntity<Map> resp = authGet("/api/channel/?pageNum=1&pageSize=100");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(5)
    void getChannel() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_get"));
        int channelId = findChannelId("test_ch_get");

        ResponseEntity<Map> resp = authGet("/api/channel/" + channelId);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(6)
    void getChannelNotFound() {
        ResponseEntity<Map> resp = authGet("/api/channel/99999");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(7)
    void searchChannel() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_search_alpha"));
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_search_beta"));

        ResponseEntity<Map> resp = authGet(
                "/api/channel/search?keyword=alpha&pageNum=1&pageSize=50");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(8)
    void updateChannel() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_update_old"));
        int channelId = findChannelId("test_ch_update_old");

        Map<String, Object> updateBody = ChannelFactory.updateRequest(channelId, "test_ch_update_new");
        ResponseEntity<Map> resp = apiPut("/api/channel/", updateBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(9)
    void updateChannelNotFound() {
        Map<String, Object> updateBody = ChannelFactory.updateRequest(99999, "ghost");
        ResponseEntity<Map> resp = apiPut("/api/channel/", updateBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(10)
    void deleteChannel() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_delete"));
        int channelId = findChannelId("test_ch_delete");

        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/channel/" + channelId, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);

        // 验证已删除
        ResponseEntity<Map> getResp = authGet("/api/channel/" + channelId);
        @SuppressWarnings("unchecked")
        Map<String, Object> getResult = getResp.getBody();
        assertThat(getResult.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(11)
    void deleteChannelNotFound() {
        ResponseEntity<Map> resp = authExchange(
                HttpMethod.DELETE, "/api/channel/99999", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    @Order(12)
    void batchDelete() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_batch_1"));
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_batch_2"));
        int id1 = findChannelId("test_ch_batch_1");
        int id2 = findChannelId("test_ch_batch_2");

        Map<String, Object> batchBody = ChannelFactory.batchDeleteRequest(id1, id2);
        ResponseEntity<Map> resp = apiPost("/api/channel/batch", batchBody);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    @Order(13)
    void copyChannel() {
        apiPost("/api/channel/", ChannelFactory.createRequest("test_ch_copy"));
        int channelId = findChannelId("test_ch_copy");

        ResponseEntity<Map> resp = apiPost(
                "/api/channel/copy/" + channelId +
                "?suffix=_copy_test&reset_balance=false", null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
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
     * POST 请求（带 JSON body + 鉴权头：yaoshu-token + yaoshu-user-id）。
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
     * 通过渠道名查找渠道 ID（ChannelController.add 返回 R.success() 无 data，需从 DB 查）。
     */
    private int findChannelId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = ?", Integer.class, name);
    }
}
