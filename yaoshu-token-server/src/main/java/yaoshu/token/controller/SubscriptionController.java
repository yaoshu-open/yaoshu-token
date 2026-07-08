package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.entity.SubscriptionOrder;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserSubscription;
import yaoshu.token.service.EpayService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.PaymentGatewayService;
import yaoshu.token.service.SubscriptionPlanService;
import yaoshu.token.service.WaffoPancakeService;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCheckoutSession;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCreateSessionParams;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePriceSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;

/**
 * 订阅用户控制器  * <p>
 * Dashboard 路由已由 {@link DashboardController} 承接，本控制器仅保留订阅用户操作。
 */
@RestController
@SaCheckLogin
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final TopupService topupService;
    private final UserService userService;
    private final OptionService optionService;
    private final EpayService epayService;
    private final PaymentGatewayService paymentGatewayService;
    private final WaffoPancakeService waffoPancakeService;

    private static final String PAYMENT_COMPLIANCE_KEY = "payment_setting.compliance_confirmed";

    // ======================== 订阅用户操作 ========================

    @GetMapping("/subscription/plans")
    public Result<?> getPlans() {
        if (!isPaymentComplianceConfirmed()) {
            return R.success(List.of());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubscriptionPlan plan : subscriptionPlanService.listEnabledPlans()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("plan", plan);
            result.add(item);
        }
        return R.success(result);
    }

    @GetMapping("/subscription/self")
    public Result<?> getSelf(HttpServletRequest request) {
        Integer userId = getUserId(request);
        User user = userService.getById(userId, true);
        String pref = normalizeBillingPreference(extractBillingPreference(user));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billing_preference", pref);
        data.put("subscriptions", wrapSubscriptions(subscriptionPlanService.listActiveUserSubscriptions(userId)));
        data.put("all_subscriptions", wrapSubscriptions(subscriptionPlanService.listAllUserSubscriptions(userId)));
        return R.success(data);
    }

    @PutMapping("/subscription/self/preference")
    public Result<?> updatePreference(HttpServletRequest request,
                                                @Valid @RequestBody BillingPreferenceRequest body) {
        Integer userId = getUserId(request);
        User user = userService.getById(userId, true);
        if (user == null) {
            throw new ResultException(R.errorPrompt("用户不存在"));
        }
        String pref = normalizeBillingPreference(body.getBillingPreference());
        com.alibaba.fastjson2.JSONObject setting = user.getSetting() == null || user.getSetting().isBlank()
                ? new com.alibaba.fastjson2.JSONObject()
                : Convert.toJSONObject(user.getSetting());
        if (setting == null) {
            setting = new com.alibaba.fastjson2.JSONObject();
        }
        setting.put("billing_preference", pref);
        user.setSetting(Convert.toJSONString(setting));
        userService.updateUser(user, false);
        return R.success(Map.of("billing_preference", pref));
    }

    @PostMapping("/subscription/balance/pay")
    public Result<?> requestBalancePay(HttpServletRequest request,
                                                 @Valid @RequestBody SubscriptionBalancePayRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        subscriptionPlanService.purchaseWithBalance(userId, body.getPlanId());
        return R.success(null);
    }

    @PostMapping("/subscription/epay/pay")
    public Result<?> requestEpay(HttpServletRequest request,
                                           @Valid @RequestBody SubscriptionEpayPayRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        if (!topupService.isEpayTopupEnabled()) {
            throw new ResultException(R.errorPrompt("当前管理员未配置支付信息"));
        }
        if (!topupService.containsPayMethod(body.getPaymentMethod())) {
            throw new ResultException(R.errorPrompt("支付方式不存在"));
        }
        Integer userId = getUserId(request);
        SubscriptionPlan plan = subscriptionPlanService.getPlanById(body.getPlanId());
        SubscriptionOrder order = subscriptionPlanService.createPendingEpayOrder(userId, body.getPlanId(), body.getPaymentMethod());
        EpayService.PurchaseRequest purchaseRequest = epayService.buildPurchaseRequest(
                body.getPaymentMethod(),
                order.getTradeNo(),
                "SUB:" + plan.getTitle(),
                BigDecimal.valueOf(order.getMoney()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                epayService.buildCallbackUrl("/api/subscription/epay/notify"),
                epayService.buildCallbackUrl("/api/subscription/epay/return")
        );
                return R.success(Map.of("data", purchaseRequest.getParams(), "url", purchaseRequest.getUrl()));
    }

    @PostMapping("/subscription/stripe/pay")
    public Result<?> requestStripePay(HttpServletRequest request,
                                                @Valid @RequestBody SubscriptionGatewayPayRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        try {
            PaymentGatewayService.GatewayLaunchResult launchResult = paymentGatewayService.createStripeSubscription(userId, body.getPlanId());
                    return R.success(launchResult.getData());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/subscription/creem/pay")
    public Result<?> requestCreemPay(HttpServletRequest request,
                                               @Valid @RequestBody SubscriptionGatewayPayRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        try {
            PaymentGatewayService.GatewayLaunchResult launchResult = paymentGatewayService.createCreemSubscription(userId, body.getPlanId());
                    return R.success(launchResult.getData());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/subscription/waffo-pancake/pay")
    public Result<?> requestWaffoPancakePay(HttpServletRequest request,
                                                      @Valid @RequestBody SubscriptionGatewayPayRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        if (body.getPlanId() == null || body.getPlanId() <= 0) {
            throw new ResultException(R.errorPrompt("参数错误"));
        }
        // 加载 plan 并校验 enabled + waffoPancakeProductId 非空
        SubscriptionPlan plan = subscriptionPlanService.getPlanById(body.getPlanId());
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            throw new ResultException(R.errorPrompt("套餐未启用"));
        }
        if (plan.getWaffoPancakeProductId() == null || plan.getWaffoPancakeProductId().isBlank()) {
            throw new ResultException(R.errorPrompt("该套餐未配置 WaffoPancakeProductId"));
        }
        // 订阅只校验 merchantId + privateKey（productId 由 plan 提供，不要求全局 ProductID）
        String merchantId = optionService.getValue("WaffoPancakeMerchantID");
        String privateKey = optionService.getValue("WaffoPancakePrivateKey");
        if (merchantId == null || merchantId.isBlank() || privateKey == null || privateKey.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 未配置或密钥无效"));
        }

        Integer userId = getUserId(request);
        // 生成订阅 tradeNo（前缀 WAFFO_PANCAKE_SUB-，webhook 分发依赖前缀匹配）
        String tradeNo = WaffoPancakeService.generateTradeNo(userId, true);

        // 创建 pending 订阅订单（plan 校验 + 购买上限 + 创建订单由 createPendingExternalOrder 封装）
        try {
            subscriptionPlanService.createPendingExternalOrder(
                    userId, body.getPlanId(), "waffo_pancake", "waffo_pancake", tradeNo);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }

        // 调 createCheckoutSession（productId = plan.waffoPancakeProductId，OnetimeProduct 代订阅 RFC §3.5）
        String amountStr = BigDecimal.valueOf(plan.getPriceAmount() == null ? 0D : plan.getPriceAmount())
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
        WaffoPancakeCreateSessionParams params = WaffoPancakeCreateSessionParams.builder()
                .productId(plan.getWaffoPancakeProductId())
                .buyerIdentity(WaffoPancakeService.buyerIdentityFromUserId(userId))
                .orderMerchantExternalId(tradeNo)
                .expiresInSeconds(45 * 60)
                .priceSnapshot(new WaffoPancakePriceSnapshot(amountStr, "saas"))
                .build();
        try {
            WaffoPancakeCheckoutSession session = waffoPancakeService.createCheckoutSession(params);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("checkout_url", session.getCheckoutUrl());
            data.put("session_id", session.getSessionId());
            data.put("expires_at", session.getExpiresAt());
            data.put("order_id", tradeNo);
            data.put("token", session.getToken());
            data.put("token_expires_at", session.getTokenExpiresAt());
                    return R.success(data);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt("拉起支付失败"));
        }
    }

    // ======================== Epay 回调 ========================

    @PostMapping("/subscription/epay/notify")
    public void epayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayNotify(request, response);
    }

    @GetMapping("/subscription/epay/notify")
    public void epayNotifyGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayNotify(request, response);
    }

    @GetMapping("/subscription/epay/return")
    public void epayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayReturn(request, response);
    }

    @PostMapping("/subscription/epay/return")
    public void epayReturnPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleEpayReturn(request, response);
    }

    private boolean isPaymentComplianceConfirmed() {
        return "true".equalsIgnoreCase(optionService.getValue(PAYMENT_COMPLIANCE_KEY));
    }

    private Integer getUserId(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null || userId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.not_logged_in"));
        }
        return userId;
    }

    private String extractBillingPreference(User user) {
        if (user == null || user.getSetting() == null || user.getSetting().isBlank()) {
            return "subscription_first";
        }
        com.alibaba.fastjson2.JSONObject setting = Convert.toJSONObject(user.getSetting());
        return setting == null ? "subscription_first" : setting.getString("billing_preference");
    }

    private String normalizeBillingPreference(String pref) {
        String value = pref == null ? "" : pref.trim();
        switch (value) {
            case "subscription_first":
            case "wallet_first":
            case "subscription_only":
            case "wallet_only":
                return value;
            default:
                return "subscription_first";
        }
    }

    private List<Map<String, Object>> wrapSubscriptions(List<UserSubscription> subscriptions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserSubscription subscription : subscriptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subscription", subscription);
            result.add(item);
        }
        return result;
    }

    private void handleEpayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EpayService.VerifyResult verifyResult = epayService.verify(request);
        if (!verifyResult.isVerifyStatus()
                || !EpayService.STATUS_TRADE_SUCCESS.equals(verifyResult.getTradeStatus())) {
            writePlainText(response, "fail");
            return;
        }
        try {
            subscriptionPlanService.completeExternalOrder(
                    verifyResult.getServiceTradeNo(),
                    Convert.toJSONString(epayService.extractParams(request)),
                    "epay",
                    verifyResult.getType()
            );
            writePlainText(response, "success");
        } catch (Exception e) {
            writePlainText(response, "fail");
        }
    }

    private void handleEpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EpayService.VerifyResult verifyResult = epayService.verify(request);
        if (!verifyResult.isVerifyStatus()) {
            response.sendRedirect(epayService.buildConsoleReturnUrl("/console/topup?pay=fail"));
            return;
        }
        if (EpayService.STATUS_TRADE_SUCCESS.equals(verifyResult.getTradeStatus())) {
            try {
                subscriptionPlanService.completeExternalOrder(
                        verifyResult.getServiceTradeNo(),
                        Convert.toJSONString(epayService.extractParams(request)),
                        "epay",
                        verifyResult.getType()
                );
                response.sendRedirect(epayService.buildConsoleReturnUrl("/console/topup?pay=success"));
                return;
            } catch (Exception e) {
                response.sendRedirect(epayService.buildConsoleReturnUrl("/console/topup?pay=fail"));
                return;
            }
        }
        response.sendRedirect(epayService.buildConsoleReturnUrl("/console/topup?pay=pending"));
    }

    private void writePlainText(HttpServletResponse response, String text) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(text);
        response.getWriter().flush();
    }

    @Data
    public static class BillingPreferenceRequest {
        @NotBlank(message = "billing_preference 不能为空")
        private String billingPreference;
    }

    @Data
    public static class SubscriptionBalancePayRequest {
        @NotNull(message = "plan_id 不能为空")
        private Integer planId;
    }

    @Data
    public static class SubscriptionEpayPayRequest {
        @NotNull(message = "plan_id 不能为空")
        private Integer planId;
        @NotBlank(message = "payment_method 不能为空")
        private String paymentMethod;
    }

    @Data
    public static class SubscriptionGatewayPayRequest {
        @NotNull(message = "plan_id 不能为空")
        private Integer planId;
    }
}
