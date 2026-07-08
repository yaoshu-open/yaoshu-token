package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.SubscriptionOrderMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.pojo.entity.SubscriptionOrder;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.service.payment.waffopancake.client.PemKeyLoader;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeApiClient;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeApiException;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeCheckoutResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeGraphqlResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeOnetimeProductsResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakePairException;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeStoresResource;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeWebhookVerifier;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalog;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCheckoutSession;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCreateSessionParams;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookEvent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.v7.core.util.RandomUtil;
import cn.hutool.v7.core.text.StrUtil;

import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Waffo Pancake 支付服务  * <p>
 * 实现策略：基于 Waffo Pancake REST + GraphQL API 与 Webhook 验签机制，
 * 封装项目内部 API 客户端类（service.payment.waffopancake.client 包），
 * 等价覆盖 Go 依赖的 waffo-pancake-sdk-go 能力。
 * <p>
 * 核心方法（对齐 Go）：
 * <ul>
 *   <li>{@link #createCheckoutSession} ← Go CreateWaffoPancakeCheckoutSession</li>
 *   <li>{@link #verifyWebhookEvent} ← Go VerifyConfiguredWaffoPancakeWebhook</li>
 *   <li>{@link #resolveTradeNo} ← Go ResolveWaffoPancakeTradeNo</li>
 *   <li>{@link #buyerIdentityFromUserId(int)} ← Go WaffoPancakeBuyerIdentityFromUserID</li>
 * </ul>
 * <p>
 * 设计文档：ai-docs/后端设计/设计_WaffoPancake支付.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaffoPancakeService {

    /** tradeNo 前缀（充值），webhook 分发依赖前缀匹配（RFC §八.1） */
    public static final String TRADE_NO_PREFIX_TOPUP = "WAFFO_PANCAKE-";
    /** tradeNo 前缀（订阅），webhook 分发依赖前缀匹配（RFC §八.1） */
    public static final String TRADE_NO_PREFIX_SUBSCRIPTION = "WAFFO_PANCAKE_SUB-";

    private static final String PAYMENT_PROVIDER_WAFFO_PANCAKE = "waffo_pancake";

    /** 确定性默认门店名（稳定 body → 稳定幂等键） */
    public static final String DEFAULT_STORE_NAME = "yaoshu-store";
    /** 确定性默认商品名 */
    public static final String DEFAULT_PRODUCT_NAME = "yaoshu-charge-product";

    private final OptionService optionService;
    private final TopUpMapper topUpMapper;
    private final SubscriptionOrderMapper subscriptionOrderMapper;
    private final WaffoPancakeWebhookVerifier webhookVerifier;

    /** 单例客户端（无状态，所有商户共用 HttpClient 与签名器） */
    private final WaffoPancakeApiClient apiClient = new WaffoPancakeApiClient();
    private final WaffoPancakeCheckoutResource checkoutResource = new WaffoPancakeCheckoutResource(apiClient);
    private final WaffoPancakeGraphqlResource graphqlResource = new WaffoPancakeGraphqlResource(apiClient);
    private final WaffoPancakeStoresResource storesResource = new WaffoPancakeStoresResource(apiClient);
    private final WaffoPancakeOnetimeProductsResource onetimeProductsResource = new WaffoPancakeOnetimeProductsResource(apiClient);

    /**
     * 创建 authenticated checkout 会话（对齐 Go CreateWaffoPancakeCheckoutSession）。
     * <p>
     * 内部完成两步调用：① issue-session-token（API Key 签名）→ ② create-session（业务参数）。
     * 凭证从 options 表动态读取（WaffoPancakeMerchantID + WaffoPancakePrivateKey + WaffoPancakeProductID）。
     */
    public WaffoPancakeCheckoutSession createCheckoutSession(WaffoPancakeCreateSessionParams params) {
        String merchantId = loadMerchantId();
        PrivateKey privateKey = loadPrivateKey();
        return checkoutResource.createAuthenticatedSession(params, merchantId, privateKey);
    }

    /**
     * Webhook 验签并解析事件（对齐 Go VerifyConfiguredWaffoPancakeWebhook）。
     * <p>
     * 原始 body 必须是 raw text（禁止先 JSON 解析），签名 header 解析 + 重放校验 +
     * 公钥选择（按 body.mode）+ RSA-SHA256 验签由 {@link WaffoPancakeWebhookVerifier} 完成。
     */
    public WaffoPancakeWebhookEvent verifyWebhookEvent(String rawBody, String signatureHeader) {
        return webhookVerifier.verify(rawBody, signatureHeader);
    }

    /**
     * 充值订单反查与 BuyerIdentity 校验（对齐 Go ResolveWaffoPancakeTradeNo）。
     * <p>
     * 按 orderMerchantExternalId 查 top_ups，校验 PaymentProvider 与 BuyerIdentity。
     * BuyerIdentity 不一致 = 跨用户订单劫持，必须拒绝（RFC §3.3 防劫持核心防线）。
     *
     * @param event 已验签事件
     * @return 本地 tradeNo（= event.data.orderMerchantExternalId）
     * @throws RuntimeException 订单不存在 / paymentProvider 不匹配 / identity 不匹配
     */
    public String resolveTradeNo(WaffoPancakeWebhookEvent event) {
        if (event == null || event.getData() == null) {
            throw new RuntimeException("missing webhook event");
        }
        String tradeNo = event.getData().getOrderMerchantExternalId();
        tradeNo = tradeNo == null ? "" : tradeNo.trim();
        if (tradeNo.isEmpty()) {
            throw new RuntimeException("missing webhook orderMerchantExternalId");
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1"));
        if (topUp == null || !PAYMENT_PROVIDER_WAFFO_PANCAKE.equals(topUp.getPaymentProvider())) {
            throw new RuntimeException("waffo pancake order not found for tradeNo=" + tradeNo);
        }
        String expectedIdentity = buyerIdentityFromUserId(topUp.getUserId());
        String actualIdentity = event.getData().getMerchantProvidedBuyerIdentity();
        actualIdentity = actualIdentity == null ? "" : actualIdentity.trim();
        if (!expectedIdentity.equals(actualIdentity)) {
            throw new RuntimeException(
                    "waffo pancake buyer identity mismatch for tradeNo=" + tradeNo
                            + ": expected=" + expectedIdentity + " actual=" + actualIdentity);
        }
        return tradeNo;
    }

    /**
     * 订阅订单反查与 BuyerIdentity 校验（对齐 Go ResolveWaffoPancakeSubscriptionTradeNo）。
     * <p>
     * 按 orderMerchantExternalId 查 subscription_orders，校验 PaymentProvider 与 BuyerIdentity。
     * 与 {@link #resolveTradeNo} 的差异仅在数据源（SubscriptionOrder vs TopUp）。
     *
     * @param event 已验签事件
     * @return 本地 tradeNo
     * @throws RuntimeException 订单不存在 / paymentProvider 不匹配 / identity 不匹配
     */
    public String resolveSubscriptionTradeNo(WaffoPancakeWebhookEvent event) {
        if (event == null || event.getData() == null) {
            throw new RuntimeException("missing webhook event");
        }
        String tradeNo = event.getData().getOrderMerchantExternalId();
        tradeNo = tradeNo == null ? "" : tradeNo.trim();
        if (tradeNo.isEmpty()) {
            throw new RuntimeException("missing webhook orderMerchantExternalId");
        }
        SubscriptionOrder order = subscriptionOrderMapper.selectOne(new LambdaQueryWrapper<SubscriptionOrder>()
                .eq(SubscriptionOrder::getTradeNo, tradeNo)
                .last("LIMIT 1"));
        if (order == null || !PAYMENT_PROVIDER_WAFFO_PANCAKE.equals(order.getPaymentProvider())) {
            throw new RuntimeException("waffo pancake subscription order not found for tradeNo=" + tradeNo);
        }
        String expectedIdentity = buyerIdentityFromUserId(order.getUserId());
        String actualIdentity = event.getData().getMerchantProvidedBuyerIdentity();
        actualIdentity = actualIdentity == null ? "" : actualIdentity.trim();
        if (!expectedIdentity.equals(actualIdentity)) {
            throw new RuntimeException(
                    "waffo pancake buyer identity mismatch for subscription tradeNo=" + tradeNo
                            + ": expected=" + expectedIdentity + " actual=" + actualIdentity);
        }
        return tradeNo;
    }

    /**
     * 查询 catalog（对齐 Go ListWaffoPancakeCatalog）。
     * <p>
     * 双用途：①凭证探针（成功响应 = 凭证有效）；②商品列表（active 状态的 OnetimeProducts）。
     * 接受外部凭证（管理端 body 凭证 typed-but-not-saved 场景），空白时由调用方回退持久化凭证。
     */
    public WaffoPancakeCatalog listCatalog(String merchantId, String privateKeyPem) {
        if (StrUtil.isBlank(merchantId) || StrUtil.isBlank(privateKeyPem)) {
            throw new WaffoPancakeApiException("Waffo Pancake 凭证未配置");
        }
        PrivateKey privateKey = PemKeyLoader.loadPrivateKey(privateKeyPem);
        return graphqlResource.queryCatalog(merchantId, privateKey);
    }

    /**
     * 创建门店 + 充值商品的配对（对齐 Go CreateWaffoPancakePrimaryPair）。
     * <p>
     * 两步流程：① createStore（yaoshu-store）→ storeId；② createProduct（yaoshu-charge-product，
     * seed 价 "1.00"，checkout 时由 PriceSnapshot 覆盖）+ publishProduct → productId。
     * <p>
     * OrphanStore 半成功处理：store 创建成功但 product 失败时抛 {@link WaffoPancakePairException}
     *（携带含 storeId/storeName 的 partialResult），Controller 据此返回孤儿 store 上下文。
     * 不持久化任何配置（对齐 Go：operator 的最终 Save 提交选定 ID）。
     *
     * @param merchantId    商户 ID（MER_xxx）
     * @param privateKeyPem RSA 私钥 PEM
     * @param returnURL     购买成功跳转 URL（空白则不传给 Pancake）
     * @return 配对结果（storeId/storeName/productId/productName）
     * @throws WaffoPancakeApiException    store 创建失败或凭证无效
     * @throws WaffoPancakePairException   store 成功但 product 失败（OrphanStore 半成功）
     */
    public WaffoPancakePairResult createPrimaryPair(String merchantId, String privateKeyPem, String returnURL) {
        if (StrUtil.isBlank(merchantId) || StrUtil.isBlank(privateKeyPem)) {
            throw new WaffoPancakeApiException("Waffo Pancake 凭证未配置");
        }
        PrivateKey privateKey = PemKeyLoader.loadPrivateKey(privateKeyPem);

        // 步骤 1：创建门店（失败直接抛出，无孤儿）
        String storeId = storesResource.createStore(DEFAULT_STORE_NAME, merchantId, privateKey);

        // 步骤 2：创建 + 发布充值商品（失败则 store 成为孤儿）
        try {
            String productId = onetimeProductsResource.createProduct(
                    storeId, DEFAULT_PRODUCT_NAME, "1.00", returnURL, merchantId, privateKey);
            onetimeProductsResource.publishProduct(productId, merchantId, privateKey);
            return WaffoPancakePairResult.builder()
                    .storeId(storeId)
                    .storeName(DEFAULT_STORE_NAME)
                    .productId(productId)
                    .productName(DEFAULT_PRODUCT_NAME)
                    .build();
        } catch (RuntimeException e) {
            // store 已创建但 product 失败 → OrphanStore 半成功（对齐 Go 返回 partial + error）
            WaffoPancakePairResult partial = WaffoPancakePairResult.builder()
                    .storeId(storeId)
                    .storeName(DEFAULT_STORE_NAME)
                    .orphanStore(true)
                    .build();
            throw new WaffoPancakePairException(
                    "store created at " + storeId + " but product creation failed: " + e.getMessage(), partial);
        }
    }

    /**
     * 为订阅套餐创建 OnetimeProduct（对齐 Go CreateWaffoPancakeProductForPlan）。
     * <p>
     * 用 OnetimeProduct 代订阅（非 SubscriptionProduct），因当前实现无续订事件处理
     *（RFC §3.5，Go 已验证决策）。接受 name/amount（来自表单，非 plan 行），
     * create + publish 两步后返回 productId。
     *
     * @param merchantId    商户 ID（MER_xxx）
     * @param privateKeyPem RSA 私钥 PEM
     * @param storeId       门店 ID（STO_xxx）
     * @param name          套餐名称（商品名）
     * @param amount        显示格式价格字符串（如 "9.99"）
     * @param returnURL     购买成功跳转 URL（空白则不传）
     * @return 新建商品 ID（PROD_xxx）
     * @throws WaffoPancakeApiException 凭证/storeId/name/amount 为空或 API 调用失败
     */
    public String createProductForPlan(String merchantId, String privateKeyPem, String storeId,
                                       String name, String amount, String returnURL) {
        if (StrUtil.isBlank(merchantId) || StrUtil.isBlank(privateKeyPem)) {
            throw new WaffoPancakeApiException("Waffo Pancake 凭证未配置");
        }
        // 对齐 Go 校验顺序：storeId → name → amount
        String trimmedStoreId = StrUtil.isBlank(storeId) ? "" : storeId.trim();
        if (trimmedStoreId.isEmpty()) {
            throw new WaffoPancakeApiException("store id is required to create a product");
        }
        String trimmedName = StrUtil.isBlank(name) ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new WaffoPancakeApiException("plan name is required");
        }
        String trimmedAmount = StrUtil.isBlank(amount) ? "" : amount.trim();
        if (trimmedAmount.isEmpty()) {
            throw new WaffoPancakeApiException("plan price is required");
        }
        PrivateKey privateKey = PemKeyLoader.loadPrivateKey(privateKeyPem);
        String productId = onetimeProductsResource.createProduct(
                trimmedStoreId, trimmedName, trimmedAmount, returnURL, merchantId, privateKey);
        onetimeProductsResource.publishProduct(productId, merchantId, privateKey);
        return productId;
    }

    /**
     * 持久化 5 字段配置（对齐 Go SaveWaffoPancakeConfig）。
     * <p>
     * 单事务原子写入（OptionService.batchUpdate）。空白 privateKey 视为"保持当前"
     *（Stripe 式 API 密钥 UX，避免管理员重复粘贴私钥）。
     */
    public void saveConfig(String merchantId, String privateKey, String returnURL,
                           String storeId, String productId) {
        Map<String, String> options = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(merchantId)) {
            options.put("WaffoPancakeMerchantID", merchantId.trim());
        }
        if (StrUtil.isNotBlank(privateKey)) {
            options.put("WaffoPancakePrivateKey", privateKey.trim());
        }
        if (StrUtil.isNotBlank(returnURL)) {
            options.put("WaffoPancakeReturnURL", returnURL.trim());
        }
        if (StrUtil.isNotBlank(storeId)) {
            options.put("WaffoPancakeStoreID", storeId.trim());
        }
        if (StrUtil.isNotBlank(productId)) {
            options.put("WaffoPancakeProductID", productId.trim());
        }
        optionService.batchUpdate(options);
    }

    /**
     * 生成 BuyerIdentity。
     * <p>
     * 格式硬编码：{@code yaoshu-user-{userId}}。webhook 二次校验硬编码此格式，
     * 修改格式会导致 webhook 校验失效（RFC §八.2）。
     */
    public static String buyerIdentityFromUserId(int userId) {
        return "yaoshu-user-" + userId;
    }

    /**
     * 生成 tradeNo（对齐 Go controller/topup_waffo_pancake.go 的 tradeNo 构造）。
     * <p>
     * 格式：前缀 + userId + unixMilli + 6位随机字母数字。
     * 前缀：{@link #TRADE_NO_PREFIX_TOPUP}（充值）或 {@link #TRADE_NO_PREFIX_SUBSCRIPTION}（订阅）。
     */
    public static String generateTradeNo(int userId, boolean isSubscription) {
        String prefix = isSubscription ? TRADE_NO_PREFIX_SUBSCRIPTION : TRADE_NO_PREFIX_TOPUP;
        return prefix + userId + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomLettersAndNumbers(6);
    }

    private String loadMerchantId() {
        String merchantId = optionService.getValue("WaffoPancakeMerchantID");
        if (StrUtil.isBlank(merchantId)) {
            throw new WaffoPancakeApiException("Waffo Pancake MerchantID not configured");
        }
        return merchantId;
    }

    private PrivateKey loadPrivateKey() {
        String privateKeyPem = optionService.getValue("WaffoPancakePrivateKey");
        if (StrUtil.isBlank(privateKeyPem)) {
            throw new WaffoPancakeApiException("Waffo Pancake PrivateKey not configured");
        }
        return PemKeyLoader.loadPrivateKey(privateKeyPem);
    }
}
