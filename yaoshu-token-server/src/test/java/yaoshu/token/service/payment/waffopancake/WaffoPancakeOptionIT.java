package yaoshu.token.service.payment.waffopancake;

import ai.yue.library.base.convert.Convert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Waffo Pancake 管理端 save 配置集成测试 —— 验证 5 字段原子持久化 + 空白 privateKey 保持当前（Stripe 式 UX）。
 * <p>
 * 鉴权：save 端点位于 /api/option/waffo-pancake/save，rootAuthFilter 拦截 /api/option/*（需 role=3）。
 * 通过 yaoshu-token Header（登录后从响应体取 token）传递鉴权，复用 UserIT 的 root 登录模式。
 * <p>
 * 数据隔离：测试用 options key（WaffoPancake*）在 @AfterAll 清理，避免污染 dev 环境配置。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WaffoPancake 管理端 save 配置持久化集成测试")
class WaffoPancakeOptionIT extends BaseIntegrationTest {

    private static final String ADMIN_USER = "test_wp_opt_admin";
    private static final String ADMIN_PWD = "test_wp_admin_123";
    private static final String SAVE_URL = "/api/option/waffo-pancake/save";

    @Autowired
    private UserService userService;
    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private RestTemplate authRestTemplate;
    private String saToken;

    @BeforeAll
    void setUpRootAdmin() {
        // 清理残留 + 插入 root 测试用户（role=3）
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String hashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                        "VALUES (?, ?, 'WP Test Admin', 3, 1)", ADMIN_USER, hashed);

        // 登录获取 Sa-Token
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
                .as("root 登录应成功，响应: %s", loginResp.getBody()).isTrue();

        // 从响应体解析 Sa-Token
        @SuppressWarnings("unchecked")
        Map<String, Object> loginResult = Convert.toJSONObject(loginResp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = (Map<String, Object>) loginResult.get("data");
        assertThat(loginData).as("登录响应 data 不应为 null").isNotNull();
        saToken = (String) loginData.get("token");
        assertThat(saToken).as("登录响应应包含 token 字段").isNotNull();
    }

    @AfterAll
    void tearDown() {
        // 清理测试数据（root 用户 + WaffoPancake 配置 + PasswordLoginEnabled）
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
    }

    // ======================== save 持久化测试 ========================

    @Test
    @DisplayName("save 5 字段全部持久化到 options 表")
    void save_5Fields_AllPersisted() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", "MER_test_it_001");
        body.put("privateKey", "-----BEGIN PRIVATE KEY-----\nfake_key_for_it\n-----END PRIVATE KEY-----");
        body.put("returnUrl", "https://test.example.com/return");
        body.put("storeId", "STR_test_it_001");
        body.put("productId", "PROD_test_it_001");

        ResponseEntity<Map> resp = apiPost(SAVE_URL, body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // 验证 5 字段全部写入 options 表
        assertThatOptionEquals("WaffoPancakeMerchantID", "MER_test_it_001");
        assertThatOptionEquals("WaffoPancakePrivateKey",
                "-----BEGIN PRIVATE KEY-----\nfake_key_for_it\n-----END PRIVATE KEY-----");
        assertThatOptionEquals("WaffoPancakeReturnURL", "https://test.example.com/return");
        assertThatOptionEquals("WaffoPancakeStoreID", "STR_test_it_001");
        assertThatOptionEquals("WaffoPancakeProductID", "PROD_test_it_001");
    }

    @Test
    @DisplayName("空白 privateKey 保持当前值（Stripe 式 API 密钥 UX）")
    void save_BlankPrivateKey_KeepsCurrent() {
        // 第一次：保存含 privateKey 的完整配置
        Map<String, Object> firstBody = new LinkedHashMap<>();
        firstBody.put("merchantId", "MER_keep_test");
        firstBody.put("privateKey", "ORIGINAL_PK_SECRET_VALUE");
        firstBody.put("productId", "PROD_keep_test");
        apiPost(SAVE_URL, firstBody);
        assertThatOptionEquals("WaffoPancakePrivateKey", "ORIGINAL_PK_SECRET_VALUE");

        // 第二次：merchantId 更新 + privateKey 空白（应保持原值）
        Map<String, Object> secondBody = new LinkedHashMap<>();
        secondBody.put("merchantId", "MER_keep_test_updated");
        secondBody.put("privateKey", "");  // 空白
        secondBody.put("productId", "PROD_keep_test_updated");
        ResponseEntity<Map> resp = apiPost(SAVE_URL, secondBody);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // merchantId 和 productId 更新
        assertThatOptionEquals("WaffoPancakeMerchantID", "MER_keep_test_updated");
        assertThatOptionEquals("WaffoPancakeProductID", "PROD_keep_test_updated");
        // privateKey 保持原值（空白不覆盖）
        assertThatOptionEquals("WaffoPancakePrivateKey", "ORIGINAL_PK_SECRET_VALUE");
    }

    @Test
    @DisplayName("null privateKey 保持当前值（管理员不重复粘贴私钥场景）")
    void save_NullPrivateKey_KeepsCurrent() {
        // 第一次保存
        Map<String, Object> firstBody = new LinkedHashMap<>();
        firstBody.put("merchantId", "MER_null_pk");
        firstBody.put("privateKey", "PK_VALUE_FOR_NULL_TEST");
        apiPost(SAVE_URL, firstBody);

        // 第二次：privateKey 不传（null）
        Map<String, Object> secondBody = new LinkedHashMap<>();
        secondBody.put("merchantId", "MER_null_pk_updated");
        // 不放 privateKey 键 → ipo.getPrivateKey() 返回 null
        ResponseEntity<Map> resp = apiPost(SAVE_URL, secondBody);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThatOptionEquals("WaffoPancakeMerchantID", "MER_null_pk_updated");
        assertThatOptionEquals("WaffoPancakePrivateKey", "PK_VALUE_FOR_NULL_TEST");
    }

    @Test
    @DisplayName("save 响应包含 product_id 和 store_id（前端回显）")
    void save_ResponseContainsProductIdAndStoreId() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", "MER_resp_test");
        body.put("productId", "PROD_resp_test");
        body.put("storeId", "STR_resp_test");

        ResponseEntity<Map> resp = apiPost(SAVE_URL, body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Object result = Convert.toJSONObject(resp.getBody());
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) result;
        assertThat(result2.get("flag")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result2.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("product_id")).isEqualTo("PROD_resp_test");
        assertThat(data.get("store_id")).isEqualTo("STR_resp_test");
    }

    // ======================== 测试 helper ========================

    /** 带 yaoshu-token Header 的 POST 请求 */
    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> apiPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.POST, entity, Map.class);
    }

    /** 断言 options 表中指定 key 的值（清理缓存后直接查 DB） */
    private void assertThatOptionEquals(String key, String expected) {
        String actual = jdbcTemplate.query(
                "SELECT `value` FROM `options` WHERE `key` = ?",
                (rs, rowNum) -> rs.getString(1), key).stream().findFirst().orElse(null);
        assertThat(actual).as("options[%s]", key).isEqualTo(expected);
    }
}
