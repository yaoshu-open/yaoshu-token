package yaoshu.token.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import yaoshu.token.mapper.SubscriptionOrderMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.pojo.entity.SubscriptionOrder;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeApiException;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeOnetimeProductsResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakePairException;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeStoresResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeWebhookVerifier;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookData;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookEvent;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WaffoPancakeService 单测 —— 聚焦 resolveTradeNo/resolveSubscriptionTradeNo 业务校验逻辑
 * 与 generateTradeNo/buyerIdentityFromUserId 静态方法契约。
 * <p>
 * Mock 策略：TopUpMapper/SubscriptionOrderMapper 是 MyBatis-Plus 数据访问层，
 * 单测 Mock 它聚焦 resolveTradeNo 的订单反查 + BuyerIdentity 防劫持校验逻辑
 *（对齐现有 PerfMetricsServiceTest 模式）。真实 DB 验证在 WaffoPancakeWebhookIT 集成测试。
 * <p>
 * identity 不匹配用例覆盖 RFC §3.3「防跨用户订单劫持」核心防线，
 * 等价 Go TestResolveWaffoPancakeTradeNo_RejectsBuyerIdentityMismatch。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WaffoPancakeService — 订单反查与防劫持校验")
class WaffoPancakeServiceTest {

    private static final String TOPUP_TRADE_NO = "WAFFO_PANCAKE-1-1718900000-abc123";
    private static final String SUB_TRADE_NO = "WAFFO_PANCAKE_SUB-1-1718900000-xyz789";

    @Mock
    private OptionService optionService;
    @Mock
    private TopUpMapper topUpMapper;
    @Mock
    private SubscriptionOrderMapper subscriptionOrderMapper;
    @Mock
    private WaffoPancakeWebhookVerifier webhookVerifier;
    @Mock
    private WaffoPancakeStoresResource storesResource;
    @Mock
    private WaffoPancakeOnetimeProductsResource onetimeProductsResource;

    @InjectMocks
    private WaffoPancakeService service;

    /** 测试用 PKCS#8 PEM（动态生成，createPrimaryPair 单测需要 valid PEM 通过 PemKeyLoader） */
    private static String testPrivateKeyPem;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            testPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(kp.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw new IllegalStateException("生成测试 RSA 密钥对失败", e);
        }
    }

    @BeforeEach
    void injectResourceMocks() {
        // storesResource/onetimeProductsResource 在 Service 中是 new 初始化的非构造函数 final 字段，
        // 用反射注入 mock 以隔离 Pancake 上游写操作（对齐红线15：外部不可控依赖可 Mock）
        ReflectionTestUtils.setField(service, "storesResource", storesResource);
        ReflectionTestUtils.setField(service, "onetimeProductsResource", onetimeProductsResource);
    }

    // ======================== resolveTradeNo（充值订单反查） ========================

    @Test
    @DisplayName("正常反查：订单存在 + provider 匹配 + identity 匹配 → 返回 tradeNo")
    void resolveTradeNo_Success() {
        TopUp topUp = buildTopUp(1, TOPUP_TRADE_NO, "waffo_pancake");
        when(topUpMapper.selectOne(any())).thenReturn(topUp);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, "yaoshu-user-1");
        String result = service.resolveTradeNo(event);

        assertEquals(TOPUP_TRADE_NO, result);
    }

    @Test
    @DisplayName("订单不存在 → 抛异常（订单未找到）")
    void resolveTradeNo_OrderNotFound_Throws() {
        when(topUpMapper.selectOne(any())).thenReturn(null);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, "yaoshu-user-1");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
        assertTrue(ex.getMessage().contains("not found"), "异常信息应含 not found");
    }

    @Test
    @DisplayName("paymentProvider 不匹配（订单是 stripe）→ 抛异常")
    void resolveTradeNo_ProviderMismatch_Throws() {
        // 订单存在但 paymentProvider 是 stripe（非 waffo_pancake）
        TopUp topUp = buildTopUp(1, TOPUP_TRADE_NO, "stripe");
        when(topUpMapper.selectOne(any())).thenReturn(topUp);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, "yaoshu-user-1");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
        assertTrue(ex.getMessage().contains("not found"), "provider 不匹配应视为订单未找到");
    }

    @Test
    @DisplayName("identity 不匹配（防跨用户订单劫持）→ 抛异常（Go TestResolveWaffoPancakeTradeNo_RejectsBuyerIdentityMismatch 等价）")
    void resolveTradeNo_BuyerIdentityMismatch_Throws() {
        // 订单 userId=1（期望 identity=yaoshu-user-1），但 webhook 声称 identity=yaoshu-user-999
        TopUp topUp = buildTopUp(1, TOPUP_TRADE_NO, "waffo_pancake");
        when(topUpMapper.selectOne(any())).thenReturn(topUp);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, "yaoshu-user-999");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
        assertTrue(ex.getMessage().contains("identity mismatch"), "异常信息应含 identity mismatch");
    }

    @Test
    @DisplayName("identity 为空（Pancake 未回传）→ 抛异常")
    void resolveTradeNo_EmptyIdentity_Throws() {
        TopUp topUp = buildTopUp(1, TOPUP_TRADE_NO, "waffo_pancake");
        when(topUpMapper.selectOne(any())).thenReturn(topUp);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
        assertTrue(ex.getMessage().contains("identity mismatch"));
    }

    @Test
    @DisplayName("event 为 null → 抛异常")
    void resolveTradeNo_NullEvent_Throws() {
        assertThrows(RuntimeException.class, () -> service.resolveTradeNo(null));
    }

    @Test
    @DisplayName("event.data 为 null → 抛异常")
    void resolveTradeNo_NullData_Throws() {
        WaffoPancakeWebhookEvent event = WaffoPancakeWebhookEvent.builder().data(null).build();
        assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
    }

    @Test
    @DisplayName("orderMerchantExternalId 为空 → 抛异常")
    void resolveTradeNo_EmptyExternalId_Throws() {
        WaffoPancakeWebhookEvent event = buildEvent("", "yaoshu-user-1");
        assertThrows(RuntimeException.class, () -> service.resolveTradeNo(event));
    }

    // ======================== resolveSubscriptionTradeNo（订阅订单反查） ========================

    @Test
    @DisplayName("订阅订单正常反查：订单存在 + provider 匹配 + identity 匹配 → 返回 tradeNo")
    void resolveSubscriptionTradeNo_Success() {
        SubscriptionOrder order = buildSubscriptionOrder(1, SUB_TRADE_NO, "waffo_pancake");
        when(subscriptionOrderMapper.selectOne(any())).thenReturn(order);

        WaffoPancakeWebhookEvent event = buildEvent(SUB_TRADE_NO, "yaoshu-user-1");
        String result = service.resolveSubscriptionTradeNo(event);

        assertEquals(SUB_TRADE_NO, result);
    }

    @Test
    @DisplayName("订阅订单不存在 → 抛异常")
    void resolveSubscriptionTradeNo_OrderNotFound_Throws() {
        when(subscriptionOrderMapper.selectOne(any())).thenReturn(null);

        WaffoPancakeWebhookEvent event = buildEvent(SUB_TRADE_NO, "yaoshu-user-1");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.resolveSubscriptionTradeNo(event));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("订阅订单 identity 不匹配 → 抛异常（防劫持）")
    void resolveSubscriptionTradeNo_BuyerIdentityMismatch_Throws() {
        SubscriptionOrder order = buildSubscriptionOrder(1, SUB_TRADE_NO, "waffo_pancake");
        when(subscriptionOrderMapper.selectOne(any())).thenReturn(order);

        WaffoPancakeWebhookEvent event = buildEvent(SUB_TRADE_NO, "yaoshu-user-999");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.resolveSubscriptionTradeNo(event));
        assertTrue(ex.getMessage().contains("identity mismatch"));
    }

    @Test
    @DisplayName("订阅订单 paymentProvider 不匹配 → 抛异常")
    void resolveSubscriptionTradeNo_ProviderMismatch_Throws() {
        SubscriptionOrder order = buildSubscriptionOrder(1, SUB_TRADE_NO, "stripe");
        when(subscriptionOrderMapper.selectOne(any())).thenReturn(order);

        WaffoPancakeWebhookEvent event = buildEvent(SUB_TRADE_NO, "yaoshu-user-1");
        assertThrows(RuntimeException.class, () -> service.resolveSubscriptionTradeNo(event));
    }

    // ======================== createPrimaryPair（门店+商品配对，对齐 Go CreateWaffoPancakePrimaryPair） ========================

    @Test
    @DisplayName("正常配对：store + createProduct + publishProduct 全成功 → 返回完整 PairResult")
    void createPrimaryPair_Success() {
        when(storesResource.createStore(eq(WaffoPancakeService.DEFAULT_STORE_NAME), anyString(), any()))
                .thenReturn("STO_test123");
        when(onetimeProductsResource.createProduct(
                eq("STO_test123"), eq(WaffoPancakeService.DEFAULT_PRODUCT_NAME), eq("1.00"),
                anyString(), anyString(), any()))
                .thenReturn("PROD_test456");

        WaffoPancakePairResult result = service.createPrimaryPair(
                "MER_test", testPrivateKeyPem, "https://example.com/thanks");

        assertEquals("STO_test123", result.getStoreId());
        assertEquals(WaffoPancakeService.DEFAULT_STORE_NAME, result.getStoreName());
        assertEquals("PROD_test456", result.getProductId());
        assertEquals(WaffoPancakeService.DEFAULT_PRODUCT_NAME, result.getProductName());
        assertFalse(result.isOrphanStore(), "全成功时 orphanStore 必须为 false");
        // 验证 publish 被调用（对齐 Go：create 后必须 publish）
        verify(onetimeProductsResource).publishProduct(eq("PROD_test456"), anyString(), any());
    }

    @Test
    @DisplayName("OrphanStore 半成功：store 成功但 createProduct 失败 → 抛 PairException 携带孤儿 store 上下文")
    void createPrimaryPair_ProductCreateFails_OrphanStore() {
        when(storesResource.createStore(anyString(), anyString(), any())).thenReturn("STO_orphan");
        when(onetimeProductsResource.createProduct(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new WaffoPancakeApiException("create product HTTP 500"));

        WaffoPancakePairException ex = assertThrows(WaffoPancakePairException.class, () ->
                service.createPrimaryPair("MER_test", testPrivateKeyPem, ""));

        // 验证孤儿上下文（对齐 Go：返回 partial{storeID, storeName, orphanStore:true} + error）
        assertEquals("STO_orphan", ex.getPartialResult().getStoreId());
        assertEquals(WaffoPancakeService.DEFAULT_STORE_NAME, ex.getPartialResult().getStoreName());
        assertTrue(ex.getPartialResult().isOrphanStore(), "product 失败时 orphanStore 必须为 true");
        assertTrue(ex.getMessage().contains("STO_orphan"), "异常信息应含 storeId 便于运营定位");
    }

    @Test
    @DisplayName("OrphanStore 半成功：createProduct 成功但 publishProduct 失败 → 抛 PairException（Go 中 publish 失败同样导致 product 步骤失败）")
    void createPrimaryPair_PublishFails_OrphanStore() {
        when(storesResource.createStore(anyString(), anyString(), any())).thenReturn("STO_orphan2");
        when(onetimeProductsResource.createProduct(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("PROD_unpublished");
        doThrow(new WaffoPancakeApiException("publish HTTP 500"))
                .when(onetimeProductsResource).publishProduct(anyString(), anyString(), any());

        WaffoPancakePairException ex = assertThrows(WaffoPancakePairException.class, () ->
                service.createPrimaryPair("MER_test", testPrivateKeyPem, ""));

        assertTrue(ex.getPartialResult().isOrphanStore());
        assertEquals("STO_orphan2", ex.getPartialResult().getStoreId());
    }

    @Test
    @DisplayName("store 创建失败 → 抛 ApiException（非 PairException，无孤儿，因 store 未创建）")
    void createPrimaryPair_StoreFails_NoOrphan() {
        when(storesResource.createStore(anyString(), anyString(), any()))
                .thenThrow(new WaffoPancakeApiException("create store HTTP 500"));

        // store 失败应抛 ApiException（非 PairException），因为 store 未创建无孤儿可言
        assertThrows(WaffoPancakeApiException.class, () ->
                service.createPrimaryPair("MER_test", testPrivateKeyPem, ""));
    }

    @Test
    @DisplayName("凭证空白 → 抛 ApiException（凭证未配置）")
    void createPrimaryPair_BlankCreds_Throws() {
        assertThrows(WaffoPancakeApiException.class, () ->
                service.createPrimaryPair("", testPrivateKeyPem, ""));
        assertThrows(WaffoPancakeApiException.class, () ->
                service.createPrimaryPair("MER_test", "", ""));
        assertThrows(WaffoPancakeApiException.class, () ->
                service.createPrimaryPair("   ", "   ", ""));
    }

    // ======================== createProductForPlan（订阅套餐商品创建，对齐 Go CreateWaffoPancakeProductForPlan） ========================

    @Test
    @DisplayName("正常创建：createProduct + publishProduct 全成功 → 返回 productId")
    void createProductForPlan_Success() {
        when(onetimeProductsResource.createProduct(
                eq("STO_plan"), eq("Pro 套餐"), eq("9.99"), anyString(), anyString(), any()))
                .thenReturn("PROD_plan123");

        String productId = service.createProductForPlan(
                "MER_test", testPrivateKeyPem, "STO_plan", "Pro 套餐", "9.99", "https://x.com/thanks");

        assertEquals("PROD_plan123", productId);
        verify(onetimeProductsResource).publishProduct(eq("PROD_plan123"), anyString(), any());
    }

    @Test
    @DisplayName("storeId 为空 → 抛异常（store id is required）")
    void createProductForPlan_BlankStoreId_Throws() {
        WaffoPancakeApiException ex = assertThrows(WaffoPancakeApiException.class, () ->
                service.createProductForPlan("MER_test", testPrivateKeyPem, "  ", "Pro", "9.99", ""));
        assertTrue(ex.getMessage().contains("store id is required"));
    }

    @Test
    @DisplayName("name 为空 → 抛异常（plan name is required）")
    void createProductForPlan_BlankName_Throws() {
        WaffoPancakeApiException ex = assertThrows(WaffoPancakeApiException.class, () ->
                service.createProductForPlan("MER_test", testPrivateKeyPem, "STO_plan", "", "9.99", ""));
        assertTrue(ex.getMessage().contains("plan name is required"));
    }

    @Test
    @DisplayName("amount 为空 → 抛异常（plan price is required）")
    void createProductForPlan_BlankAmount_Throws() {
        WaffoPancakeApiException ex = assertThrows(WaffoPancakeApiException.class, () ->
                service.createProductForPlan("MER_test", testPrivateKeyPem, "STO_plan", "Pro", "   ", ""));
        assertTrue(ex.getMessage().contains("plan price is required"));
    }

    // ======================== generateTradeNo（静态方法契约） ========================

    @Test
    @DisplayName("充值 tradeNo 前缀为 WAFFO_PANCAKE-（webhook 分发依赖前缀匹配）")
    void generateTradeNo_TopupPrefix() {
        String tradeNo = WaffoPancakeService.generateTradeNo(1, false);
        assertTrue(tradeNo.startsWith(WaffoPancakeService.TRADE_NO_PREFIX_TOPUP),
                "充值 tradeNo 必须以 WAFFO_PANCAKE- 开头，实际: " + tradeNo);
        assertTrue(tradeNo.startsWith("WAFFO_PANCAKE-1-"),
                "充值 tradeNo 必须含 userId 段，实际: " + tradeNo);
    }

    @Test
    @DisplayName("订阅 tradeNo 前缀为 WAFFO_PANCAKE_SUB-（webhook 按 SUB 前缀分发到订阅链路）")
    void generateTradeNo_SubscriptionPrefix() {
        String tradeNo = WaffoPancakeService.generateTradeNo(1, true);
        assertTrue(tradeNo.startsWith(WaffoPancakeService.TRADE_NO_PREFIX_SUBSCRIPTION),
                "订阅 tradeNo 必须以 WAFFO_PANCAKE_SUB- 开头，实际: " + tradeNo);
        assertTrue(tradeNo.startsWith("WAFFO_PANCAKE_SUB-1-"),
                "订阅 tradeNo 必须含 userId 段，实际: " + tradeNo);
    }

    @Test
    @DisplayName("两次生成 tradeNo 不同（随机后缀保证唯一性）")
    void generateTradeNo_Uniqueness() {
        String t1 = WaffoPancakeService.generateTradeNo(1, false);
        String t2 = WaffoPancakeService.generateTradeNo(1, false);
        assertNotEquals(t1, t2, "两次生成的 tradeNo 必须不同（随机后缀）");
    }

    @Test
    @DisplayName("不同 userId 生成不同 tradeNo 的 userId 段")
    void generateTradeNo_DifferentUserId() {
        String t1 = WaffoPancakeService.generateTradeNo(1, false);
        String t2 = WaffoPancakeService.generateTradeNo(2, false);
        assertNotEquals(t1, t2);
        assertTrue(t1.startsWith("WAFFO_PANCAKE-1-"));
        assertTrue(t2.startsWith("WAFFO_PANCAKE-2-"));
    }

    // ======================== buyerIdentityFromUserId（静态方法契约） ========================

    @Test
    @DisplayName("buyerIdentity 格式为 yaoshu-user-{userId}（RFC §3.3 硬编码格式，不可改）")
    void buyerIdentityFromUserId_Format() {
        assertEquals("yaoshu-user-1", WaffoPancakeService.buyerIdentityFromUserId(1));
        assertEquals("yaoshu-user-999", WaffoPancakeService.buyerIdentityFromUserId(999));
        assertEquals("yaoshu-user-0", WaffoPancakeService.buyerIdentityFromUserId(0));
    }

    @Test
    @DisplayName("buyerIdentity 与 generateTradeNo + resolveTradeNo 的 identity 校验链路一致")
    void buyerIdentityFromUserId_ConsistentWithResolve() {
        // 验证生成 identity 与 resolveTradeNo 校验逻辑使用同一格式（防格式漂移）
        int userId = 42;
        String generatedIdentity = WaffoPancakeService.buyerIdentityFromUserId(userId);
        TopUp topUp = buildTopUp(userId, TOPUP_TRADE_NO, "waffo_pancake");
        when(topUpMapper.selectOne(any())).thenReturn(topUp);

        WaffoPancakeWebhookEvent event = buildEvent(TOPUP_TRADE_NO, generatedIdentity);
        // 应成功返回（格式一致，不触发 identity mismatch）
        assertEquals(TOPUP_TRADE_NO, service.resolveTradeNo(event));
    }

    // ======================== 测试 helper ========================

    private static TopUp buildTopUp(int userId, String tradeNo, String paymentProvider) {
        TopUp topUp = new TopUp();
        topUp.setUserId(userId);
        topUp.setTradeNo(tradeNo);
        topUp.setPaymentProvider(paymentProvider);
        topUp.setStatus("0");
        return topUp;
    }

    private static SubscriptionOrder buildSubscriptionOrder(int userId, String tradeNo, String paymentProvider) {
        SubscriptionOrder order = new SubscriptionOrder();
        order.setUserId(userId);
        order.setTradeNo(tradeNo);
        order.setPaymentProvider(paymentProvider);
        order.setStatus("0");
        return order;
    }

    private static WaffoPancakeWebhookEvent buildEvent(String orderMerchantExternalId, String buyerIdentity) {
        WaffoPancakeWebhookData data = WaffoPancakeWebhookData.builder()
                .orderMerchantExternalId(orderMerchantExternalId)
                .merchantProvidedBuyerIdentity(buyerIdentity)
                .orderId("ORD_test")
                .amount("10.00")
                .currency("USD")
                .build();
        return WaffoPancakeWebhookEvent.builder()
                .id("evt_test")
                .eventType("order.completed")
                .mode("test")
                .data(data)
                .build();
    }
}
