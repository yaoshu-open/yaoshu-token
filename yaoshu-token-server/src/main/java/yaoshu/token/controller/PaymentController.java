package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.web.util.ServletUtils;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.pojo.ipo.PaymentIPO;
import yaoshu.token.service.EpayService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.PaymentGatewayService;
import yaoshu.token.service.SubscriptionPlanService;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.WaffoPancakeService;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakeWebhookException;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCheckoutSession;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCreateSessionParams;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePriceSnapshot;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付与支付Webhook控制器  * <p>
 * 认证：混合（支付操作 UserAuth，Webhook 无认证）
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final TopupService topupService;
    private final UserService userService;
    private final EpayService epayService;
    private final PaymentGatewayService paymentGatewayService;
    private final WaffoPancakeService waffoPancakeService;
    private final OptionService optionService;
    private final SubscriptionPlanService subscriptionPlanService;

    // ======================== 支付操作（UserAuth） ========================

    @PostMapping("/user/pay")
    public Result<?> requestEpay(HttpServletRequest request, @Valid @RequestBody EpayPayRequest body) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        if (!topupService.isEpayTopupEnabled()) {
            throw new ResultException(R.errorPrompt("当前管理员未配置支付信息"));
        }
        Integer userId = getUserId(request);
        TopUp topUp = topupService.createPendingEpayOrder(userId, body.getAmount(), body.getPaymentMethod());
        EpayService.PurchaseRequest purchaseRequest = epayService.buildPurchaseRequest(
                body.getPaymentMethod(),
                topUp.getTradeNo(),
                "TUC" + body.getAmount(),
                BigDecimal.valueOf(topUp.getMoney()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                epayService.buildCallbackUrl("/api/user/epay/notify"),
                epayService.buildConsoleReturnUrl("/console/log")
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data", purchaseRequest.getParams());
        payload.put("url", purchaseRequest.getUrl());
        return R.success(payload);
    }

    @PostMapping("/user/amount")
    public Result<?> requestAmount(HttpServletRequest request, @Valid @RequestBody AmountRequest body) {
        long minTopup = topupService.getMinTopup();
        if (body.getAmount() < minTopup) {
            throw new ResultException(R.errorPrompt("充值数量不能小于 " + minTopup));
        }
        Integer userId = getUserId(request);
        String group = userService.getUserGroup(userId);
        BigDecimal payMoney = topupService.calculatePayMoney(body.getAmount(), group);
        if (payMoney.compareTo(new BigDecimal("0.01")) <= 0) {
            throw new ResultException(R.errorPrompt("充值金额过低"));
        }
        return R.success(payMoney.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    @PostMapping("/user/stripe/pay")
    public Result<?> requestStripePay(HttpServletRequest request,
                                                @Valid @RequestBody StripePayRequest body) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        PaymentGatewayService.StripeTopupRequest gatewayRequest = new PaymentGatewayService.StripeTopupRequest();
        gatewayRequest.setAmount(body.getAmount());
        gatewayRequest.setPaymentMethod(body.getPaymentMethod());
        gatewayRequest.setSuccessUrl(body.getSuccessUrl());
        gatewayRequest.setCancelUrl(body.getCancelUrl());
        try {
            PaymentGatewayService.GatewayLaunchResult launchResult = paymentGatewayService.createStripeTopup(userId, gatewayRequest);
            return R.success(launchResult.getData());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/user/stripe/amount")
    public Result<?> requestStripeAmount(HttpServletRequest request, @Valid @RequestBody AmountRequest body) {
        return calculateGatewayAmount(request, body.getAmount(), "StripeMinTopUp", "StripeUnitPrice");
    }

    @PostMapping("/user/creem/pay")
    public Result<?> requestCreemPay(HttpServletRequest request,
                                               @Valid @RequestBody CreemPayRequest body) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        PaymentGatewayService.CreemTopupRequest gatewayRequest = new PaymentGatewayService.CreemTopupRequest();
        gatewayRequest.setProductId(body.getProductId());
        gatewayRequest.setPaymentMethod(body.getPaymentMethod());
        try {
            PaymentGatewayService.GatewayLaunchResult launchResult = paymentGatewayService.createCreemTopup(userId, gatewayRequest);
            return R.success(launchResult.getData());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/user/waffo/amount")
    public Result<?> requestWaffoAmount(HttpServletRequest request, @Valid @RequestBody AmountRequest body) {
        return calculateGatewayAmount(request, body.getAmount(), "WaffoMinTopUp", "WaffoUnitPrice");
    }

    @PostMapping("/user/waffo/pay")
    public Result<?> requestWaffoPay(HttpServletRequest request,
                                               @Valid @RequestBody WaffoPayRequest body) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        PaymentGatewayService.WaffoTopupRequest gatewayRequest = new PaymentGatewayService.WaffoTopupRequest();
        gatewayRequest.setAmount(body.getAmount());
        gatewayRequest.setPayMethodIndex(body.getPayMethodIndex());
        gatewayRequest.setPayMethodType(body.getPayMethodType());
        gatewayRequest.setPayMethodName(body.getPayMethodName());
        try {
            PaymentGatewayService.GatewayLaunchResult launchResult = paymentGatewayService.createWaffoTopup(userId, gatewayRequest);
            return R.success(launchResult.getData());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/user/waffo-pancake/amount")
    public Result<?> requestWaffoPancakeAmount(HttpServletRequest request, @Valid @RequestBody AmountRequest body) {
        return calculateGatewayAmount(request, body.getAmount(), "WaffoPancakeMinTopUp", "WaffoPancakeUnitPrice");
    }

    @PostMapping("/user/waffo-pancake/pay")
    public Result<?> requestWaffoPancakePay(HttpServletRequest request,
                                                      @Valid @RequestBody PaymentIPO.WaffoPancakePay ipo) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        if (!topupService.isWaffoPancakeTopupEnabled()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 配置不完整"));
        }
        Long amount = ipo.getAmount();
        if (amount == null) {
            throw new ResultException(R.errorPrompt("参数错误"));
        }
        long minTopup = topupService.getIntSetting("WaffoPancakeMinTopUp", 1);
        if (amount < minTopup) {
            throw new ResultException(R.errorPrompt("充值数量不能小于 " + minTopup));
        }
        Integer userId = getUserId(request);
        String group = userService.getUserGroup(userId);
        BigDecimal payMoney = topupService.calculateGatewayPayMoney(amount, group, "WaffoPancakeUnitPrice");
        if (payMoney.compareTo(new BigDecimal("0.01")) <= 0) {
            throw new ResultException(R.errorPrompt("充值金额过低"));
        }
        String productId = optionService.getValue("WaffoPancakeProductID");
        if (productId == null || productId.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake ProductID 未配置"));
        }

        // 生成 tradeNo（前缀 WAFFO_PANCAKE-，webhook 分发依赖前缀匹配）
        String tradeNo = WaffoPancakeService.generateTradeNo(userId, false);

        // 创建 pending 订单（status=0）
        TopUp topUp = topupService.createPendingGatewayOrder(
                userId, amount, payMoney.doubleValue(),
                "waffo_pancake", "waffo_pancake", tradeNo);

        // 调 createCheckoutSession（Authenticated Checkout 两步：issue-token + create-session）
        WaffoPancakeCreateSessionParams params = WaffoPancakeCreateSessionParams.builder()
                .productId(productId)
                .buyerIdentity(WaffoPancakeService.buyerIdentityFromUserId(userId))
                .orderMerchantExternalId(tradeNo)
                .expiresInSeconds(45 * 60)
                .priceSnapshot(new WaffoPancakePriceSnapshot(
                        payMoney.setScale(2, RoundingMode.HALF_UP).toPlainString(), "saas"))
                .build();
        try {
            WaffoPancakeCheckoutSession session = waffoPancakeService.createCheckoutSession(params);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("checkout_url", session.getCheckoutUrl());
            data.put("session_id", session.getSessionId());
            data.put("expires_at", session.getExpiresAt());
            data.put("order_id", tradeNo);
            data.put("token", session.getToken());
            data.put("token_expires_at", session.getTokenExpiresAt());
            return R.success(data);
        } catch (RuntimeException e) {
            // 创建会话失败：更新 status=failed（对齐 Go），返回前端友好提示
            topupService.markTopupFailed(tradeNo);
            throw new ResultException(R.errorPrompt("拉起支付失败"));
        }
    }

    // ======================== Epay 回调（无认证） ========================

    @PostMapping("/user/epay/notify")
    public void epayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayNotify(request, response);
    }

    @GetMapping("/user/epay/notify")
    public void epayNotifyGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayNotify(request, response);
    }

    // ======================== Webhook（无认证） ========================

    @PostMapping("/stripe/webhook")
    public void stripe(HttpServletRequest request, HttpServletResponse response, @RequestBody String body) throws IOException {
        PaymentGatewayService.WebhookResult result = paymentGatewayService.handleStripeWebhook(
                body,
                request.getHeader("Stripe-Signature"),
                ServletUtils.getClientIP(request)
        );
        writeWebhookResponse(response, result);
    }

    @PostMapping("/creem/webhook")
    public void creem(HttpServletRequest request, HttpServletResponse response, @RequestBody String body) throws IOException {
        PaymentGatewayService.WebhookResult result = paymentGatewayService.handleCreemWebhook(
                body,
                request.getHeader("creem-signature"),
                ServletUtils.getClientIP(request)
        );
        writeWebhookResponse(response, result);
    }

    @PostMapping("/waffo/webhook")
    public void waffo(HttpServletRequest request, HttpServletResponse response, @RequestBody String body) throws IOException {
        PaymentGatewayService.WebhookResult result = paymentGatewayService.handleWaffoWebhook(
                body,
                request.getHeader("X-SIGNATURE"),
                ServletUtils.getClientIP(request)
        );
        writeWebhookResponse(response, result);
    }

    @PostMapping("/waffo-pancake/webhook/{env}")
    public void waffoPancake(@PathVariable String env,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             @RequestBody String body) throws IOException {
        // 路径环境段校验（test/prod，Pancake Dashboard 注册时绑定）
        if (!"test".equals(env) && !"prod".equals(env)) {
            response.setStatus(404);
            response.getWriter().write("unknown env");
            return;
        }

        String signature = request.getHeader("X-Waffo-Signature");
        WaffoPancakeWebhookEvent event;
        try {
            event = waffoPancakeService.verifyWebhookEvent(body, signature);
        } catch (WaffoPancakeWebhookException e) {
            log.error("Waffo Pancake webhook 验签失败 env={} reason={}: {}", env, e.getReason(), e.getMessage());
            response.setStatus(401);
            response.getWriter().write("invalid signature");
            return;
        }

        // mode 与路径 env 一致性校验（防 webhook 注册错位）
        String mode = event.getMode() == null ? "" : event.getMode().trim();
        if (!env.equalsIgnoreCase(mode)) {
            response.setStatus(200);
            response.getWriter().write("OK");
            return;
        }

        // 首期仅处理 order.completed（订阅事件 WP-05 落地）
        if (!"order.completed".equals(event.getEventType())) {
            response.setStatus(200);
            response.getWriter().write("OK");
            return;
        }

        // 按 tradeNo 前缀分发：WAFFO_PANCAKE_SUB- → 订阅；其他 → 充值
        String rawTradeNo = event.getData() == null ? null : event.getData().getOrderMerchantExternalId();
        boolean isSubscription = rawTradeNo != null && rawTradeNo.startsWith(WaffoPancakeService.TRADE_NO_PREFIX_SUBSCRIPTION);
        if (isSubscription) {
            // 订阅订单解析（含 identity 校验）
            String subTradeNo;
            try {
                subTradeNo = waffoPancakeService.resolveSubscriptionTradeNo(event);
            } catch (RuntimeException e) {
                // 订单未找到 / identity 不匹配：200 OK 防 Pancake 对永久不可解事件无效重试
                log.error("Waffo Pancake webhook 订阅订单解析失败 env={} tradeNo={}: {}",
                        env, rawTradeNo, e.getMessage());
                response.setStatus(200);
                response.getWriter().write("OK");
                return;
            }
            try {
                subscriptionPlanService.completeExternalOrder(subTradeNo, body, "waffo_pancake", "");
                response.setStatus(200);
                response.getWriter().write("OK");
            } catch (Exception e) {
                // 订阅完成失败：500 让 Pancake 重试
                log.error("Waffo Pancake webhook 订阅完成失败 env={} tradeNo={}", env, subTradeNo, e);
                response.setStatus(500);
                response.getWriter().write("retry");
            }
            return;
        }

        // 充值订单解析（含 identity 校验）
        String tradeNo;
        try {
            tradeNo = waffoPancakeService.resolveTradeNo(event);
        } catch (RuntimeException e) {
            // 订单未找到 / identity 不匹配：200 OK 防 Pancake 对永久不可解事件无效重试
            log.error("Waffo Pancake webhook 充值订单解析失败 env={} tradeNo={}: {}",
                    env, rawTradeNo, e.getMessage());
            response.setStatus(200);
            response.getWriter().write("OK");
            return;
        }

        // 充值完成
        try {
            topupService.rechargeWaffoPancake(tradeNo);
            response.setStatus(200);
            response.getWriter().write("OK");
        } catch (Exception e) {
            // 充值处理失败：500 让 Pancake 重试
            log.error("Waffo Pancake webhook 充值完成失败 env={} tradeNo={}", env, tradeNo, e);
            response.setStatus(500);
            response.getWriter().write("retry");
        }
    }

    private Integer getUserId(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null || userId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.not_logged_in"));
        }
        return userId;
    }

    private Result<?> calculateGatewayAmount(HttpServletRequest request,
                                                       Long amount,
                                                       String minTopupOption,
                                                       String unitPriceOption) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        if (amount == null) {
            throw new ResultException(R.errorPrompt("参数错误"));
        }
        long minTopup = getOptionInt(minTopupOption, 1);
        if (amount < minTopup) {
            throw new ResultException(R.errorPrompt("充值数量不能小于 " + minTopup));
        }
        Integer userId = getUserId(request);
        String group = userService.getUserGroup(userId);
        BigDecimal payMoney = topupService.calculateGatewayPayMoney(amount, group, unitPriceOption);
        if (payMoney.compareTo(new BigDecimal("0.01")) <= 0) {
            throw new ResultException(R.errorPrompt("充值金额过低"));
        }
        return R.success(payMoney.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private int getOptionInt(String key, int defaultValue) {
        return topupService.getIntSetting(key, defaultValue);
    }

    private void handleEpayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EpayService.VerifyResult verifyResult = epayService.verify(request);
        if (!verifyResult.isVerifyStatus()
                || !EpayService.STATUS_TRADE_SUCCESS.equals(verifyResult.getTradeStatus())) {
            writePlainText(response, "fail");
            return;
        }
        try {
            topupService.completeEpayOrder(verifyResult.getServiceTradeNo(), verifyResult.getType());
            writePlainText(response, "success");
        } catch (Exception e) {
            writePlainText(response, "fail");
        }
    }

    private void writePlainText(HttpServletResponse response, String text) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(text);
        response.getWriter().flush();
    }

    private void writeWebhookResponse(HttpServletResponse response, PaymentGatewayService.WebhookResult result) throws IOException {
        response.setStatus(result.getStatus());
        if (result.getHeaders() != null) {
            for (Map.Entry<String, String> entry : result.getHeaders().entrySet()) {
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }
        if (response.getContentType() == null) {
            response.setContentType("text/plain;charset=UTF-8");
        }
        if (result.getBody() != null && !result.getBody().isEmpty()) {
            response.getWriter().write(result.getBody());
        }
        response.getWriter().flush();
    }

    @Data
    public static class AmountRequest {
        @NotNull(message = "amount 不能为空")
        private Long amount;
    }

    @Data
    public static class EpayPayRequest {
        @NotNull(message = "amount 不能为空")
        private Long amount;
        @NotBlank(message = "payment_method 不能为空")
        private String paymentMethod;
    }

    @Data
    public static class StripePayRequest {
        @NotNull(message = "amount 不能为空")
        private Long amount;
        @NotBlank(message = "payment_method 不能为空")
        private String paymentMethod;
        private String successUrl;
        private String cancelUrl;
    }

    @Data
    public static class CreemPayRequest {
        @NotBlank(message = "product_id 不能为空")
        private String productId;
        @NotBlank(message = "payment_method 不能为空")
        private String paymentMethod;
    }

    @Data
    public static class WaffoPayRequest {
        @NotNull(message = "amount 不能为空")
        private Long amount;
        private Integer payMethodIndex;
        private String payMethodType;
        private String payMethodName;
    }
}
