package yaoshu.token.service.payment.waffopancake;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.WaffoPancakeService;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakePairException;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalog;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalogProduct;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalogStore;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * Waffo Pancake pair 端点集成测试 —— 验证 Controller 层（路由+鉴权+凭证解析+响应结构）。
 * <p>
 * Mock 策略：{@code @MockBean WaffoPancakeService}（对齐红线15：Pancake create-store/create-product
 * 是真实写操作，测试环境不可真实创建上游资源，Mock 外部不可控依赖）。
 * Service.createPrimaryPair 的编排逻辑（两步配对+OrphanStore）由 WaffoPancakeServiceTest 单测覆盖。
 * <p>
 * 鉴权：pair 端点位于 /api/option/waffo-pancake/pair，rootAuthFilter 拦截 /api/option/*（需 role=3）。
 * 通过 yaoshu-token Header（登录后从响应体取 token）传递鉴权，复用 WaffoPancakeOptionIT 的 root 登录模式。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WaffoPancake pair 端点集成测试（Controller 层）")
class WaffoPancakePairIT extends BaseIntegrationTest {

    private static final String ADMIN_USER = "test_wp_pair_admin";
    private static final String ADMIN_PWD = "test_wp_pair_123";
    private static final String PAIR_URL = "/api/option/waffo-pancake/pair";

    @MockBean
    private WaffoPancakeService waffoPancakeService;

    @Autowired
    private UserService userService;
    @Autowired
    private OptionService optionService;
    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private RestTemplate authRestTemplate;
    private String saToken;

    @BeforeAll
    void setUpRootAdmin() {
        // 清理残留 + 确保 options 表无 WaffoPancake 凭证（凭证未配置用例依赖）
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('PasswordLoginEnabled', 'true')");

        String hashed = userService.hashPassword(ADMIN_PWD);
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status) " +
                        "VALUES (?, ?, 'WP Pair Test Admin', 3, 1)", ADMIN_USER, hashed);

        authRestTemplate = restTemplateBuilder
                .requestFactory(() -> new SimpleClientHttpRequestFactory())
                .build();

        // root 登录获取 Sa-Token
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
        Map<String, Object> loginResult = ai.yue.library.base.convert.Convert.toJSONObject(loginResp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = (Map<String, Object>) loginResult.get("data");
        assertThat(loginData).as("登录响应 data 不应为 null").isNotNull();
        saToken = (String) loginData.get("token");
        assertThat(saToken).as("登录响应应包含 token 字段").isNotNull();
    }

    @BeforeEach
    void cleanWaffoOptions() {
        // 测试隔离：每个测试方法前清理 WaffoPancake options + 刷新缓存（subProduct 写入会污染 pair 凭证校验）
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
        optionService.refreshCache();
    }

    @AfterAll
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", ADMIN_USER);
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` = 'PasswordLoginEnabled'");
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
    }

    @Test
    @DisplayName("pair 成功：Mock Service 返回 → 响应含 store_id/store_name/product_id/product_name")
    void pair_Success_ResponseStructure() {
        WaffoPancakePairResult mockResult = WaffoPancakePairResult.builder()
                .storeId("STO_it_success")
                .storeName(WaffoPancakeService.DEFAULT_STORE_NAME)
                .productId("PROD_it_success")
                .productName(WaffoPancakeService.DEFAULT_PRODUCT_NAME)
                .build();
        when(waffoPancakeService.createPrimaryPair(anyString(), anyString(), anyString()))
                .thenReturn(mockResult);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", "MER_it_pair");
        body.put("privateKey", "fake_pem_for_it");
        body.put("returnUrl", "https://test.example.com/thanks");

        ResponseEntity<Map> resp = apiPost(PAIR_URL, body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("store_id")).isEqualTo("STO_it_success");
        assertThat(data.get("store_name")).isEqualTo(WaffoPancakeService.DEFAULT_STORE_NAME);
        assertThat(data.get("product_id")).isEqualTo("PROD_it_success");
        assertThat(data.get("product_name")).isEqualTo(WaffoPancakeService.DEFAULT_PRODUCT_NAME);
    }

    @Test
    @DisplayName("pair OrphanStore 半成功：Mock 抛 PairException → 响应含 orphan_store:true + store 上下文")
    void pair_OrphanStore_ResponseStructure() {
        WaffoPancakePairResult partial = WaffoPancakePairResult.builder()
                .storeId("STO_it_orphan")
                .storeName(WaffoPancakeService.DEFAULT_STORE_NAME)
                .orphanStore(true)
                .build();
        when(waffoPancakeService.createPrimaryPair(anyString(), anyString(), anyString()))
                .thenThrow(new WaffoPancakePairException(
                        "store created at STO_it_orphan but product creation failed: mock", partial));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", "MER_it_orphan");
        body.put("privateKey", "fake_pem_for_it");

        ResponseEntity<Map> resp = apiPost(PAIR_URL, body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(false);
        assertThat(result.get("msg")).isEqualTo("error");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("orphan_store")).isEqualTo(true);
        assertThat(data.get("store_id")).isEqualTo("STO_it_orphan");
        assertThat(data.get("store_name")).isEqualTo(WaffoPancakeService.DEFAULT_STORE_NAME);
        assertThat(data.get("error")).asString().contains("STO_it_orphan");
    }

    @Test
    @DisplayName("pair 凭证未配置（options 表无凭证 + body 无凭证）→ 错误响应")
    void pair_NoCreds_ErrorResponse() {
        // body 为空 Map（无 merchant_id/private_key），且 @BeforeAll 已清理 options 表
        Map<String, Object> body = new LinkedHashMap<>();

        ResponseEntity<Map> resp = apiPost(PAIR_URL, body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(false);
        assertThat(result.get("msg")).asString().contains("凭证未配置");
    }

    // ======================== subscription-product 端点（WP-07） ========================

    @Test
    @DisplayName("subscription-product 成功：Mock createProductForPlan → 响应含 product_id/product_name/store_id")
    void subProduct_Success() {
        // sub-product 用持久化凭证（对齐 Go resolveWaffoPancakeAdminCreds("", "")），需配置 options
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakeMerchantID', 'MER_sub_it')");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakePrivateKey', 'fake_pem_sub')");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakeStoreID', 'STO_sub_it')");
        // jdbcTemplate 直接写入需刷新 optionService 内存缓存（getValue 走缓存）
        optionService.refreshCache();
        when(waffoPancakeService.createProductForPlan(
                anyString(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenReturn("PROD_sub_it");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Pro Monthly");
        body.put("amount", "9.99");

        ResponseEntity<Map> resp = apiPost("/api/option/waffo-pancake/subscription-product", body);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("product_id")).isEqualTo("PROD_sub_it");
        assertThat(data.get("product_name")).isEqualTo("Pro Monthly");
        assertThat(data.get("store_id")).isEqualTo("STO_sub_it");
    }

    @Test
    @DisplayName("subscription-product 凭证未配置 → 错误响应（未完成配置）")
    void subProduct_NoCreds_Error() {
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Pro Monthly");
        body.put("amount", "9.99");

        ResponseEntity<Map> resp = apiPost("/api/option/waffo-pancake/subscription-product", body);

        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(false);
        assertThat(result.get("msg")).asString().contains("未完成配置");
    }

    @Test
    @DisplayName("subscription-product 套餐名称为空 → 错误响应")
    void subProduct_BlankName_Error() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "");
        body.put("amount", "9.99");

        ResponseEntity<Map> resp = apiPost("/api/option/waffo-pancake/subscription-product", body);

        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(false);
        assertThat(result.get("msg")).asString().contains("套餐名称不能为空");
    }

    @Test
    @DisplayName("subscription-product-options 成功：Mock listCatalog → 响应含 products 列表")
    void subProductOptions_Success() {
        jdbcTemplate.update("DELETE FROM `options` WHERE `key` LIKE 'WaffoPancake%'");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakeMerchantID', 'MER_opt_it')");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakePrivateKey', 'fake_pem_opt')");
        jdbcTemplate.update("INSERT INTO `options` (`key`, value) VALUES ('WaffoPancakeStoreID', 'STO_opt_it')");
        // jdbcTemplate 直接写入需刷新 optionService 内存缓存（getValue 走缓存）
        optionService.refreshCache();

        WaffoPancakeCatalogProduct prod1 = WaffoPancakeCatalogProduct.builder()
                .id("PROD_opt1").name("Plan A").status("active").build();
        WaffoPancakeCatalogStore store = WaffoPancakeCatalogStore.builder()
                .id("STO_opt_it").name("store").status("active").prodEnabled(false)
                .onetimeProducts(Arrays.asList(prod1)).build();
        WaffoPancakeCatalog catalog = WaffoPancakeCatalog.builder()
                .stores(Arrays.asList(store)).build();
        when(waffoPancakeService.listCatalog(anyString(), anyString())).thenReturn(catalog);

        HttpHeaders optHeaders = new HttpHeaders();
        optHeaders.set("yaoshu-token", saToken);
        ResponseEntity<Map> resp = authRestTemplate.exchange(
                apiUrl("/api/option/waffo-pancake/subscription-product-options"),
                HttpMethod.POST, new HttpEntity<>(optHeaders), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("flag")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertThat(data.get("store_id")).isEqualTo("STO_opt_it");
        assertThat(data.get("products")).isNotNull();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> apiPost(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("yaoshu-token", saToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return authRestTemplate.exchange(apiUrl(path), HttpMethod.POST, entity, Map.class);
    }
}
