package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import cn.hutool.v7.core.util.RandomUtil;
import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import ai.yue.library.base.util.I18nUtils;
import lombok.AllArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import lombok.Data;
import ai.yue.library.base.util.I18nUtils;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.common.UrlValidator;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.entity.SubscriptionOrder;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.pojo.entity.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 支付网关编排服务。
 * 负责 Stripe / Creem / Waffo 的下单、验签和回调状态流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String PAYMENT_METHOD_STRIPE = "stripe";
    private static final String PAYMENT_METHOD_CREEM = "creem";
    private static final String PAYMENT_METHOD_WAFFO = "waffo";

    private static final String PAYMENT_PROVIDER_STRIPE = "stripe";
    private static final String PAYMENT_PROVIDER_CREEM = "creem";
    private static final String PAYMENT_PROVIDER_WAFFO = "waffo";

    private static final String STRIPE_API_BASE = "https://api.stripe.com/v1";
    private static final String CREEM_API_BASE = "https://api.creem.io/v1";
    private static final String CREEM_TEST_API_BASE = "https://test-api.creem.io/v1";
    private static final String WAFFO_SANDBOX_BASE = "https://sandbox.waffo.com";
    private static final String WAFFO_PRODUCTION_BASE = "https://api.waffo.com";
    private static final String WAFFO_ORDER_PATH = "/v1/orders";
    private static final String WAFFO_SIGNATURE_HEADER = "X-SIGNATURE";
    private static final String WAFFO_MERCHANT_ID_HEADER = "X-MERCHANT-ID";
    private static final DateTimeFormatter WAFFO_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final TopupService topupService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final UserService userService;
    private final OptionService optionService;
    private final EpayService epayService;

    public GatewayLaunchResult createStripeTopup(int userId, StripeTopupRequest request) {
        if (!PAYMENT_METHOD_STRIPE.equals(request.getPaymentMethod())) {
            throw new RuntimeException(I18nUtils.get("payment.unsupported_channel"));
        }
        if (!topupService.isStripeTopupEnabled()) {
            throw new RuntimeException(I18nUtils.get("payment.stripe_not_configured"));
        }
        long minTopup = topupService.getIntSetting("StripeMinTopUp", 1);
        if (request.getAmount() < minTopup) {
            throw new RuntimeException(I18nUtils.get("payment.topup_count_lt_min", minTopup));
        }
        if (request.getAmount() > 10000) {
            throw new RuntimeException(I18nUtils.get("payment.topup_count_gt_max"));
        }
        validateRedirectUrl(request.getSuccessUrl(), "支付成功重定向URL不在可信任域名列表中");
        validateRedirectUrl(request.getCancelUrl(), "支付取消重定向URL不在可信任域名列表中");

        User user = requireUser(userId);
        String group = StrUtilCompat.blankToDefault(user.getGroup(), "default");
        double payMoney = topupService.calculateGatewayPayMoney(request.getAmount(), group, "StripeUnitPrice").doubleValue();
        if (payMoney <= 0.01D) {
            throw new RuntimeException(I18nUtils.get("topup.amount_too_low"));
        }

        String tradeNo = "ref_" + sha1("yaoshu-ref-" + userId + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomLettersAndNumbers(4));
        String payLink = createStripeCheckoutSession(new StripeCheckoutRequest(
                tradeNo,
                request.getSuccessUrl(),
                request.getCancelUrl(),
                String.valueOf(request.getAmount()),
                getStripePriceId(),
                user.getStripeCustomer(),
                user.getEmail(),
                "payment"
        ));
        TopUp topUp = topupService.createPendingGatewayOrder(
                userId,
                request.getAmount(),
                payMoney,
                PAYMENT_METHOD_STRIPE,
                PAYMENT_PROVIDER_STRIPE,
                tradeNo
        );
        log.info("Stripe 充值订单创建成功 userId={} tradeNo={} amount={} money={}", userId, topUp.getTradeNo(), request.getAmount(), topUp.getMoney());
        return new GatewayLaunchResult(Map.of("pay_link", payLink));
    }

    public GatewayLaunchResult createStripeSubscription(int userId, int planId) {
        if (!topupService.isStripeTopupEnabled()) {
            throw new RuntimeException(I18nUtils.get("payment.stripe_not_configured"));
        }
        if (StrUtil.isBlank(optionService.getValue("StripeWebhookSecret"))) {
            throw new RuntimeException(I18nUtils.get("payment.webhook_not_configured"));
        }
        User user = requireUser(userId);
        SubscriptionPlan plan = subscriptionPlanService.getPlanById(planId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new RuntimeException(I18nUtils.get("subscription.plan_not_enabled"));
        }
        if (StrUtil.isBlank(plan.getStripePriceId())) {
            throw new RuntimeException(I18nUtils.get("payment.price_id_not_configured"));
        }
        String tradeNo = "sub_ref_" + sha1("sub-stripe-ref-" + userId + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomLettersAndNumbers(4));
        String payLink = createStripeCheckoutSession(new StripeCheckoutRequest(
                tradeNo,
                epayService.buildConsoleReturnUrl("/console/topup"),
                epayService.buildConsoleReturnUrl("/console/topup"),
                "1",
                plan.getStripePriceId(),
                user.getStripeCustomer(),
                user.getEmail(),
                "subscription"
        ));
        subscriptionPlanService.createPendingExternalOrder(userId, planId, PAYMENT_METHOD_STRIPE, PAYMENT_PROVIDER_STRIPE, tradeNo);
        log.info("Stripe 订阅订单创建成功 userId={} tradeNo={} planId={}", userId, tradeNo, planId);
        return new GatewayLaunchResult(Map.of("pay_link", payLink));
    }

    public WebhookResult handleStripeWebhook(String body, String signatureHeader, String clientIp) {
        if (!topupService.isStripeTopupEnabled()) {
            return WebhookResult.statusOnly(403);
        }
        String webhookSecret = optionService.getValue("StripeWebhookSecret");
        if (StrUtil.isBlank(webhookSecret)) {
            return WebhookResult.statusOnly(403);
        }
        try {
            Event event = Webhook.constructEvent(body, signatureHeader, webhookSecret);
            JSONObject payload = Convert.toJSONObject(body);
            JSONObject object = payload.getJSONObject("data") == null ? null : payload.getJSONObject("data").getJSONObject("object");
            if (object == null) {
                return WebhookResult.statusOnly(400);
            }
            String eventType = event.getType();
            String tradeNo = object.getString("client_reference_id");
            switch (eventType) {
                case "checkout.session.completed":
                    if ("complete".equalsIgnoreCase(object.getString("status"))
                            && "paid".equalsIgnoreCase(object.getString("payment_status"))) {
                        safelyHandleStripeFulfillment(tradeNo, object.getString("customer"), payload, clientIp, eventType);
                    }
                    break;
                case "checkout.session.async_payment_succeeded":
                    safelyHandleStripeFulfillment(tradeNo, object.getString("customer"), payload, clientIp, eventType);
                    break;
                case "checkout.session.async_payment_failed":
                    safelyUpdateStripeStatus(tradeNo, CommonConstants.TOP_UP_STATUS_FAILED, clientIp, eventType);
                    break;
                case "checkout.session.expired":
                    safelyExpireStripeOrder(tradeNo, clientIp, eventType);
                    break;
                default:
                    break;
            }
            return WebhookResult.statusOnly(200);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook 验签失败 clientIp={} error={}", clientIp, e.getMessage());
            return WebhookResult.statusOnly(400);
        } catch (Exception e) {
            log.error("Stripe webhook 处理失败 clientIp={} error={}", clientIp, e.getMessage(), e);
            return WebhookResult.statusOnly(500);
        }
    }

    public GatewayLaunchResult createCreemTopup(int userId, CreemTopupRequest request) {
        if (!PAYMENT_METHOD_CREEM.equals(request.getPaymentMethod())) {
            throw new RuntimeException(I18nUtils.get("payment.unsupported_channel"));
        }
        if (!topupService.isCreemTopupEnabled()) {
            throw new RuntimeException(I18nUtils.get("payment.creem_not_configured"));
        }
        if (StrUtil.isBlank(request.getProductId())) {
            throw new RuntimeException(I18nUtils.get("payment.please_select_product"));
        }
        JSONObject product = findCreemProduct(request.getProductId());
        if (product == null) {
            throw new RuntimeException(I18nUtils.get("payment.product_not_exists"));
        }
        User user = requireUser(userId);
        String tradeNo = "ref_" + sha1("creem-api-ref-" + userId + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomLettersAndNumbers(4));
        TopUp topUp = topupService.createPendingFixedQuotaOrder(
                userId,
                product.getLongValue("quota"),
                product.getDoubleValue("price"),
                PAYMENT_METHOD_CREEM,
                PAYMENT_PROVIDER_CREEM,
                tradeNo
        );
        String checkoutUrl = createCreemCheckout(tradeNo, product, user);
        log.info("Creem 充值订单创建成功 userId={} tradeNo={} productId={}", userId, topUp.getTradeNo(), request.getProductId());
        return new GatewayLaunchResult(Map.of("checkout_url", checkoutUrl, "order_id", tradeNo));
    }

    public GatewayLaunchResult createCreemSubscription(int userId, int planId) {
        if (!topupService.isCreemTopupEnabled()) {
            throw new RuntimeException(I18nUtils.get("payment.creem_not_configured"));
        }
        SubscriptionPlan plan = subscriptionPlanService.getPlanById(planId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new RuntimeException(I18nUtils.get("subscription.plan_not_enabled"));
        }
        if (StrUtil.isBlank(plan.getCreemProductId())) {
            throw new RuntimeException(I18nUtils.get("payment.creem_not_configured"));
        }
        if (StrUtil.isBlank(optionService.getValue("CreemWebhookSecret"))
                && !"true".equalsIgnoreCase(optionService.getValue("CreemTestMode"))) {
            throw new RuntimeException(I18nUtils.get("payment.creem_webhook_not_configured"));
        }
        User user = requireUser(userId);
        String tradeNo = "sub_ref_" + sha1("sub-creem-ref-" + RandomUtil.randomLettersAndNumbers(6) + System.currentTimeMillis() + user.getUsername());
        subscriptionPlanService.createPendingExternalOrder(userId, planId, PAYMENT_METHOD_CREEM, PAYMENT_PROVIDER_CREEM, tradeNo);
        JSONObject product = new JSONObject();
        product.put("productId", plan.getCreemProductId());
        product.put("name", plan.getTitle());
        product.put("price", plan.getPriceAmount());
        product.put("currency", "USD");
        String checkoutUrl = createCreemCheckout(tradeNo, product, user);
        log.info("Creem 订阅订单创建成功 userId={} tradeNo={} planId={}", userId, tradeNo, planId);
        return new GatewayLaunchResult(Map.of("checkout_url", checkoutUrl, "order_id", tradeNo));
    }

    public WebhookResult handleCreemWebhook(String body, String signatureHeader, String clientIp) {
        if (!topupService.isCreemTopupEnabled()) {
            return WebhookResult.statusOnly(403);
        }
        String secret = optionService.getValue("CreemWebhookSecret");
        boolean testMode = "true".equalsIgnoreCase(optionService.getValue("CreemTestMode"));
        if (StrUtil.isBlank(signatureHeader)) {
            return WebhookResult.statusOnly(401);
        }
        if (!verifyCreemSignature(body, signatureHeader, secret, testMode)) {
            return WebhookResult.statusOnly(401);
        }
        try {
            JSONObject payload = Convert.toJSONObject(body);
            if (!"checkout.completed".equals(payload.getString("eventType"))) {
                return WebhookResult.statusOnly(200);
            }
            JSONObject object = payload.getJSONObject("object");
            JSONObject order = object == null ? null : object.getJSONObject("order");
            if (order == null || !"paid".equalsIgnoreCase(order.getString("status"))) {
                return WebhookResult.statusOnly(200);
            }
            String tradeNo = object.getString("request_id");
            if (StrUtil.isBlank(tradeNo)) {
                return WebhookResult.statusOnly(400);
            }
            String actualMethod = PAYMENT_METHOD_CREEM;
            if (subscriptionPlanService.tryCompleteExternalOrder(tradeNo, body, PAYMENT_PROVIDER_CREEM, actualMethod)) {
                return WebhookResult.statusOnly(200);
            }
            JSONObject customer = object.getJSONObject("customer");
            topupService.completeCreemOrder(
                    tradeNo,
                    customer == null ? "" : customer.getString("email"),
                    customer == null ? "" : customer.getString("name"),
                    clientIp
            );
            return WebhookResult.statusOnly(200);
        } catch (Exception e) {
            log.error("Creem webhook 处理失败 clientIp={} error={}", clientIp, e.getMessage(), e);
            return WebhookResult.statusOnly(500);
        }
    }

    public GatewayLaunchResult createWaffoTopup(int userId, WaffoTopupRequest request) {
        if (!topupService.isWaffoTopupEnabled()) {
            throw new RuntimeException(I18nUtils.get("payment.waffo_not_enabled"));
        }
        User user = requireUser(userId);
        long minTopup = topupService.getIntSetting("WaffoMinTopUp", 1);
        if (request.getAmount() < minTopup) {
            throw new RuntimeException(I18nUtils.get("payment.topup_count_lt_min", minTopup));
        }
        String group = StrUtilCompat.blankToDefault(user.getGroup(), "default");
        double payMoney = topupService.calculateGatewayPayMoney(request.getAmount(), group, "WaffoUnitPrice").doubleValue();
        if (payMoney < 0.01D) {
            throw new RuntimeException(I18nUtils.get("topup.amount_too_low"));
        }
        String merchantOrderId = "WAFFO-" + userId + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomLettersAndNumbers(6);
        long storedAmount = topupService.normalizeGatewayStoredAmount(request.getAmount());
        TopUp topUp = topupService.createPendingFixedQuotaOrder(
                userId,
                storedAmount,
                payMoney,
                PAYMENT_METHOD_WAFFO,
                PAYMENT_PROVIDER_WAFFO,
                merchantOrderId
        );
        WaffoPayMethod resolvedMethod = resolveWaffoPayMethod(userId, request);
        String paymentUrl;
        try {
            paymentUrl = createWaffoOrder(topUp, user, request.getAmount(), resolvedMethod);
        } catch (Exception e) {
            topupService.updatePendingGatewayOrderStatus(topUp.getTradeNo(), PAYMENT_PROVIDER_WAFFO, CommonConstants.TOP_UP_STATUS_FAILED);
            throw e;
        }
        log.info("Waffo 充值订单创建成功 userId={} tradeNo={} amount={} money={}", userId, topUp.getTradeNo(), request.getAmount(), topUp.getMoney());
        return new GatewayLaunchResult(Map.of("payment_url", paymentUrl, "order_id", merchantOrderId));
    }

    public WebhookResult handleWaffoWebhook(String body, String signatureHeader, String clientIp) {
        if (!topupService.isWaffoTopupEnabled()) {
            return WebhookResult.statusOnly(403);
        }
        try {
            if (!verifyWaffoSignature(body, signatureHeader)) {
                return WebhookResult.statusOnly(400);
            }
            JSONObject payload = Convert.toJSONObject(body);
            if (!"PAYMENT".equalsIgnoreCase(payload.getString("eventType"))) {
                return WaffoWebhookReply.success();
            }
            JSONObject result = payload.getJSONObject("result");
            if (result == null) {
                return WaffoWebhookReply.failed("invalid payload");
            }
            String tradeNo = result.getString("merchantOrderID");
            String orderStatus = result.getString("orderStatus");
            if (!"PAY_SUCCESS".equalsIgnoreCase(orderStatus)) {
                topupService.updatePendingGatewayOrderStatus(tradeNo, PAYMENT_PROVIDER_WAFFO, CommonConstants.TOP_UP_STATUS_FAILED);
                return WaffoWebhookReply.success();
            }
            topupService.completeWaffoOrder(tradeNo, clientIp);
            return WaffoWebhookReply.success();
        } catch (Exception e) {
            log.error("Waffo webhook 处理失败 clientIp={} error={}", clientIp, e.getMessage(), e);
            return WaffoWebhookReply.failed(e.getMessage());
        }
    }

    private void completeStripeOrder(String tradeNo,
                                     String customerId,
                                     JSONObject payload,
                                     String clientIp,
                                     String eventType) {
        JSONObject object = payload.getJSONObject("data").getJSONObject("object");
        JSONObject summary = new JSONObject();
        summary.put("customer", customerId);
        summary.put("amount_total", object.get("amount_total"));
        summary.put("currency", object.getString("currency"));
        summary.put("event_type", eventType);
        if (subscriptionPlanService.tryCompleteExternalOrder(tradeNo, summary.toJSONString(), PAYMENT_PROVIDER_STRIPE, PAYMENT_METHOD_STRIPE)) {
            return;
        }
        topupService.completeStripeOrder(tradeNo, customerId, clientIp);
    }

    private void safelyHandleStripeFulfillment(String tradeNo,
                                               String customerId,
                                               JSONObject payload,
                                               String clientIp,
                                               String eventType) {
        try {
            completeStripeOrder(tradeNo, customerId, payload, clientIp, eventType);
        } catch (Exception e) {
            log.error("Stripe webhook 完成订单失败 tradeNo={} eventType={} clientIp={} error={}",
                    tradeNo, eventType, clientIp, e.getMessage(), e);
        }
    }

    private void safelyUpdateStripeStatus(String tradeNo, String targetStatus, String clientIp, String eventType) {
        try {
            topupService.updatePendingGatewayOrderStatus(tradeNo, PAYMENT_PROVIDER_STRIPE, targetStatus);
        } catch (Exception e) {
            log.error("Stripe webhook 更新订单状态失败 tradeNo={} eventType={} clientIp={} error={}",
                    tradeNo, eventType, clientIp, e.getMessage(), e);
        }
    }

    private void safelyExpireStripeOrder(String tradeNo, String clientIp, String eventType) {
        try {
            if (!subscriptionPlanService.tryExpireOrder(tradeNo, PAYMENT_PROVIDER_STRIPE)) {
                topupService.updatePendingGatewayOrderStatus(tradeNo, PAYMENT_PROVIDER_STRIPE, CommonConstants.TOP_UP_STATUS_EXPIRED);
            }
        } catch (Exception e) {
            log.error("Stripe webhook 过期订单处理失败 tradeNo={} eventType={} clientIp={} error={}",
                    tradeNo, eventType, clientIp, e.getMessage(), e);
        }
    }

    private User requireUser(int userId) {
        User user = userService.getById(userId, false);
        if (user == null) {
            throw new RuntimeException(I18nUtils.get("admin.user_not_exists"));
        }
        return user;
    }

    private String createStripeCheckoutSession(StripeCheckoutRequest request) {
        List<String> form = new ArrayList<>();
        form.add(formPair("client_reference_id", request.getTradeNo()));
        form.add(formPair("success_url", StrUtilCompat.blankToDefault(request.getSuccessUrl(), epayService.buildConsoleReturnUrl("/console/log"))));
        form.add(formPair("cancel_url", StrUtilCompat.blankToDefault(request.getCancelUrl(), epayService.buildConsoleReturnUrl("/console/topup"))));
        form.add(formPair("line_items[0][price]", request.getPriceId()));
        form.add(formPair("line_items[0][quantity]", request.getQuantity()));
        form.add(formPair("mode", request.getMode()));
        form.add(formPair("allow_promotion_codes", "true"));
        if (StrUtil.isBlank(request.getCustomerId())) {
            if (StrUtil.isNotBlank(request.getEmail())) {
                form.add(formPair("customer_email", request.getEmail()));
            }
            form.add(formPair("customer_creation", "always"));
        } else {
            form.add(formPair("customer", request.getCustomerId()));
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(STRIPE_API_BASE + "/checkout/sessions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + getStripeApiSecret())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", form)))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Stripe API http status " + response.statusCode());
            }
            JSONObject payload = Convert.toJSONObject(response.body());
            String url = payload.getString("url");
            if (StrUtil.isBlank(url)) {
                throw new RuntimeException("Stripe API resp no checkout url");
            }
            return url;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("payment.start_failed_raw", e.getMessage()), e);
        }
    }

    private JSONObject findCreemProduct(String productId) {
        String raw = optionService.getValue("CreemProducts");
        JSONArray products = StrUtil.isBlank(raw) ? new JSONArray() : Convert.toJSONArray(raw);
        for (int i = 0; i < products.size(); i++) {
            JSONObject product = products.getJSONObject(i);
            if (product != null && StrUtil.equals(productId, product.getString("productId"))) {
                return product;
            }
        }
        return null;
    }

    private String createCreemCheckout(String tradeNo, JSONObject product, User user) {
        JSONObject request = new JSONObject();
        request.put("product_id", product.getString("productId"));
        request.put("request_id", tradeNo);
        JSONObject customer = new JSONObject();
        customer.put("email", StrUtilCompat.blankToDefault(user.getEmail(), ""));
        request.put("customer", customer);
        JSONObject metadata = new JSONObject();
        metadata.put("username", StrUtilCompat.blankToDefault(user.getUsername(), ""));
        metadata.put("reference_id", tradeNo);
        metadata.put("product_name", product.getString("name"));
        if (product.containsKey("quota")) {
            metadata.put("quota", String.valueOf(product.getLongValue("quota")));
        }
        request.put("metadata", metadata);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(getCreemApiBase() + "/checkouts"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-key", optionService.getValue("CreemApiKey"))
                .POST(HttpRequest.BodyPublishers.ofString(request.toJSONString()))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Creem API http status " + response.statusCode());
            }
            JSONObject payload = Convert.toJSONObject(response.body());
            String checkoutUrl = payload.getString("checkout_url");
            if (StrUtil.isBlank(checkoutUrl)) {
                throw new RuntimeException("Creem API resp no checkout url");
            }
            return checkoutUrl;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("payment.start_failed_raw", e.getMessage()), e);
        }
    }

    private boolean verifyCreemSignature(String payload, String signature, String secret, boolean testMode) {
        if (StrUtil.isBlank(secret)) {
            return testMode;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder expected = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                expected.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String createWaffoOrder(TopUp topUp, User user, long requestAmount, WaffoPayMethod payMethod) {
        String merchantId = getWaffoMerchantId();
        JSONObject request = new JSONObject();
        request.put("paymentRequestId", topUp.getTradeNo());
        request.put("merchantOrderId", topUp.getTradeNo());
        request.put("orderCurrency", getWaffoCurrency());
        request.put("orderAmount", formatWaffoAmount(topUp.getMoney(), getWaffoCurrency()));
        request.put("orderDescription", "Recharge " + requestAmount + " credits");
        request.put("orderRequestedAt", formatWaffoRequestedAt());
        request.put("notifyUrl", getWaffoNotifyUrl());
        request.put("successRedirectURL", getWaffoReturnUrl());
        request.put("failedRedirectURL", getWaffoReturnUrl());

        JSONObject merchantInfo = new JSONObject();
        merchantInfo.put("merchantId", merchantId);
        request.put("merchantInfo", merchantInfo);

        JSONObject userInfo = new JSONObject();
        userInfo.put("userId", String.valueOf(user.getId()));
        userInfo.put("userEmail", user.getId() + "@examples.com");
        userInfo.put("userTerminal", "WEB");
        request.put("userInfo", userInfo);

        JSONObject paymentInfo = new JSONObject();
        paymentInfo.put("productName", "ONE_TIME_PAYMENT");
        if (payMethod != null) {
            paymentInfo.put("payMethodType", payMethod.getPayMethodType());
            paymentInfo.put("payMethodName", payMethod.getPayMethodName());
        }
        request.put("paymentInfo", paymentInfo);
        String requestBody = request.toJSONString();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(getWaffoBaseUrl() + WAFFO_ORDER_PATH))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(WAFFO_MERCHANT_ID_HEADER, merchantId)
                .header(WAFFO_SIGNATURE_HEADER, signWaffoRequest(requestBody))
                .header("Authorization", "Bearer " + getWaffoApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("Waffo API http status " + response.statusCode());
            }
            JSONObject payload = Convert.toJSONObject(response.body());
            if (!"SUCCESS".equalsIgnoreCase(payload.getString("code")) && payload.containsKey("code")) {
                throw new RuntimeException(StrUtilCompat.blankToDefault(payload.getString("message"), "Waffo create order failed"));
            }
            JSONObject data = payload.getJSONObject("data");
            if (data == null) {
                throw new RuntimeException("Waffo API resp no data");
            }
            String redirectUrl = StrUtilCompat.blankToDefault(data.getString("redirectUrl"), data.getString("orderAction"));
            if (StrUtil.isBlank(redirectUrl)) {
                throw new RuntimeException("Waffo API resp no payment url");
            }
            return redirectUrl;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("payment.start_failed_raw", e.getMessage()), e);
        }
    }

    private boolean verifyWaffoSignature(String body, String signature) {
        if (StrUtil.isBlank(signature)) {
            return false;
        }
        String publicKey = optionService.getValue(isWaffoSandbox() ? "WaffoSandboxPublicCert" : "WaffoPublicCert");
        if (StrUtil.isBlank(publicKey) || StrUtil.isBlank(body)) {
            return false;
        }
        try {
            PublicKey key = parseRsaPublicKey(publicKey);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(body.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(stripBase64Signature(signature)));
        } catch (Exception e) {
            log.warn("Waffo webhook 验签失败 error={}", e.getMessage());
            return false;
        }
    }

    private WaffoPayMethod resolveWaffoPayMethod(int userId, WaffoTopupRequest request) {
        JSONArray methods = readWaffoPayMethods();
        if (request.getPayMethodIndex() != null) {
            int index = request.getPayMethodIndex();
            if (index < 0 || index >= methods.size()) {
                throw new RuntimeException(I18nUtils.get("payment.unsupported_method"));
            }
            JSONObject method = methods.getJSONObject(index);
            return new WaffoPayMethod(method.getString("payMethodType"), method.getString("payMethodName"));
        }
        if (StrUtil.isBlank(request.getPayMethodType())) {
            return null;
        }
        for (int i = 0; i < methods.size(); i++) {
            JSONObject method = methods.getJSONObject(i);
            if (StrUtil.equals(request.getPayMethodType(), method.getString("payMethodType"))
                    && StrUtil.equals(request.getPayMethodName(), method.getString("payMethodName"))) {
                return new WaffoPayMethod(method.getString("payMethodType"), method.getString("payMethodName"));
            }
        }
        throw new RuntimeException(I18nUtils.get("payment.unsupported_method"));
    }

    private JSONArray readWaffoPayMethods() {
        String raw = optionService.getValue("WaffoPayMethods");
        return StrUtil.isBlank(raw) ? new JSONArray() : Convert.toJSONArray(raw);
    }

    private String getStripeApiSecret() {
        String secret = optionService.getValue("StripeApiSecret");
        if (StrUtil.isBlank(secret) || (!secret.startsWith("sk_") && !secret.startsWith("rk_"))) {
            throw new RuntimeException(I18nUtils.get("payment.stripe_not_configured"));
        }
        return secret;
    }

    private String getStripePriceId() {
        String priceId = optionService.getValue("StripePriceId");
        if (StrUtil.isBlank(priceId)) {
            throw new RuntimeException(I18nUtils.get("payment.stripe_not_configured"));
        }
        return priceId;
    }

    private String getCreemApiBase() {
        return "true".equalsIgnoreCase(optionService.getValue("CreemTestMode")) ? CREEM_TEST_API_BASE : CREEM_API_BASE;
    }

    private boolean isWaffoSandbox() {
        return "true".equalsIgnoreCase(optionService.getValue("WaffoSandbox"));
    }

    private String getWaffoBaseUrl() {
        String configured = optionService.getValue("WaffoEndpoint");
        if (StrUtil.isNotBlank(configured)) {
            return StrUtil.removeSuffix(configured.trim(), "/");
        }
        return isWaffoSandbox() ? WAFFO_SANDBOX_BASE : WAFFO_PRODUCTION_BASE;
    }

    private String getWaffoApiKey() {
        String key = optionService.getValue(isWaffoSandbox() ? "WaffoSandboxApiKey" : "WaffoApiKey");
        if (StrUtil.isBlank(key)) {
            throw new RuntimeException(I18nUtils.get("payment.waffo_api_key_not_configured"));
        }
        return key;
    }

    private String getWaffoMerchantId() {
        String merchantId = optionService.getValue("WaffoMerchantId");
        if (StrUtil.isBlank(merchantId)) {
            throw new RuntimeException(I18nUtils.get("payment.waffo_merchant_id_not_configured"));
        }
        return merchantId.trim();
    }

    private String getWaffoPrivateKey() {
        String key = optionService.getValue(isWaffoSandbox() ? "WaffoSandboxPrivateKey" : "WaffoPrivateKey");
        if (StrUtil.isBlank(key)) {
            throw new RuntimeException(I18nUtils.get("payment.waffo_private_key_not_configured"));
        }
        return key;
    }

    private String getWaffoNotifyUrl() {
        String notifyUrl = optionService.getValue("WaffoNotifyUrl");
        if (StrUtil.isNotBlank(notifyUrl)) {
            return notifyUrl;
        }
        return epayService.buildCallbackUrl("/api/waffo/webhook");
    }

    private String getWaffoReturnUrl() {
        String returnUrl = optionService.getValue("WaffoReturnUrl");
        if (StrUtil.isNotBlank(returnUrl)) {
            return returnUrl;
        }
        return epayService.buildConsoleReturnUrl("/console/topup?show_history=true");
    }

    private String getWaffoCurrency() {
        return StrUtilCompat.blankToDefault(optionService.getValue("WaffoCurrency"), "USD").trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String formatWaffoRequestedAt() {
        return WAFFO_TIMESTAMP_FORMATTER.format(Instant.now());
    }

    private String formatWaffoAmount(Double amount, String currency) {
        double safeAmount = amount == null ? 0D : amount;
        if (List.of("IDR", "JPY", "KRW", "VND").contains(currency)) {
            return String.format(java.util.Locale.ROOT, "%.0f", safeAmount);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", safeAmount);
    }

    private void validateRedirectUrl(String url, String message) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        try {
            UrlValidator.validateRedirectURL(url);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(message);
        }
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("payment.sha1_failed"), e);
        }
    }

    private String formPair(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(StrUtilCompat.blankToDefault(value, ""), StandardCharsets.UTF_8);
    }

    @Data
    @AllArgsConstructor
    public static class GatewayLaunchResult {
        private Map<String, Object> data;
    }

    @Data
    @AllArgsConstructor
    public static class WebhookResult {
        private int status;
        private String body;
        private Map<String, String> headers;

        public static WebhookResult statusOnly(int status) {
            return new WebhookResult(status, "", Map.of());
        }
    }

    public static final class WaffoWebhookReply {
        private WaffoWebhookReply() {
        }

        public static WebhookResult success() {
            return signedReply("{\"success\":true}");
        }

        public static WebhookResult failed(String msg) {
            return signedReply(Convert.toJSONString(Map.of("success", false, "message", StrUtilCompat.blankToDefault(msg, "failed"))));
        }

        private static WebhookResult signedReply(String body) {
            try {
                String signature = Holder.INSTANCE.signWaffoResponse(body);
                return new WebhookResult(200, body, Map.of("X-SIGNATURE", signature));
            } catch (Exception e) {
                throw new RuntimeException(I18nUtils.get("payment.waffo_webhook_sign_failed"), e);
            }
        }

        /**
         * 借助外层单例服务完成回调响应签名，避免静态内部类重复维护密钥解析逻辑。
         */
        private static final class Holder {
            private static PaymentGatewayService INSTANCE;
        }
    }

    @Data
    @AllArgsConstructor
    private static class StripeCheckoutRequest {
        private String tradeNo;
        private String successUrl;
        private String cancelUrl;
        private String quantity;
        private String priceId;
        private String customerId;
        private String email;
        private String mode;
    }

    @Data
    public static class StripeTopupRequest {
        private long amount;
        private String paymentMethod;
        private String successUrl;
        private String cancelUrl;
    }

    @Data
    public static class CreemTopupRequest {
        private String productId;
        private String paymentMethod;
    }

    @Data
    public static class WaffoTopupRequest {
        private long amount;
        private Integer payMethodIndex;
        private String payMethodType;
        private String payMethodName;
    }

    @Data
    @AllArgsConstructor
    private static class WaffoPayMethod {
        private String payMethodType;
        private String payMethodName;
    }

    @jakarta.annotation.PostConstruct
    void initWaffoReplySigner() {
        WaffoWebhookReply.Holder.INSTANCE = this;
    }

    private String signWaffoResponse(String body) {
        return signWaffoPayload(body, "Waffo 回调响应签名失败");
    }

    private String signWaffoRequest(String body) {
        return signWaffoPayload(body, "Waffo 请求签名失败");
    }

    private String signWaffoPayload(String body, String message) {
        try {
            PrivateKey privateKey = parseRsaPrivateKey(getWaffoPrivateKey());
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException(message, e);
        }
    }

    private PrivateKey parseRsaPrivateKey(String privateKey) throws Exception {
        String cleanedKey = privateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\n", "")
                .trim();
        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private PublicKey parseRsaPublicKey(String publicKey) throws Exception {
        String cleanedKey = publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\n", "")
                .trim();
        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private String stripBase64Signature(String signature) {
        return signature
                .replace("-----BEGIN SIGNATURE-----", "")
                .replace("-----END SIGNATURE-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\n", "")
                .trim();
    }
}
