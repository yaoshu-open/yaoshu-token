package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.UserSubscription;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SubscriptionPlanService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅管理控制器（Admin）  * <p>
 * 认证：AdminAuth（全部）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/subscription/admin")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final OptionService optionService;

    private static final String PAYMENT_COMPLIANCE_KEY = "payment_setting.compliance_confirmed";

    @GetMapping("/plans")
    public Result<?> listPlans() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubscriptionPlan plan : subscriptionPlanService.listAllPlans()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("plan", plan);
            result.add(item);
        }
        return R.success(result);
    }

    @PostMapping("/plans")
    public Result<?> createPlan(@Valid @RequestBody UpsertPlanRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        return R.success(subscriptionPlanService.createPlan(body.getPlan()));
    }

    @PutMapping("/plans/{id}")
    public Result<?> updatePlan(@PathVariable int id, @Valid @RequestBody UpsertPlanRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        subscriptionPlanService.updatePlan(id, body.getPlan());
        return R.success();
    }

    @PatchMapping("/plans/{id}")
    public Result<?> updatePlanStatus(@PathVariable int id,
                                                @Valid @RequestBody UpdatePlanStatusRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        subscriptionPlanService.updatePlanStatus(id, body.getEnabled());
        return R.success();
    }

    @PostMapping("/bind")
    public Result<?> bind(@Valid @RequestBody BindSubscriptionRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        String msg = subscriptionPlanService.adminBindSubscription(body.getUserId(), body.getPlanId());
        return msg.isEmpty() ? R.success() : R.success(msg);
    }

    @GetMapping("/users/{id}/subscriptions")
    public Result<?> listUserSubscriptions(@PathVariable int id) {
        return R.success(wrapSubscriptions(subscriptionPlanService.listAllUserSubscriptions(id)));
    }

    @PostMapping("/users/{id}/subscriptions")
    public Result<?> createUserSubscription(@PathVariable int id,
                                                      @Valid @RequestBody CreateUserSubscriptionRequest body) {
        if (!isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        String msg = subscriptionPlanService.adminBindSubscription(id, body.getPlanId());
        return msg.isEmpty() ? R.success() : R.success(msg);
    }

    @PostMapping("/user_subscriptions/{id}/invalidate")
    public Result<?> invalidateUserSubscription(@PathVariable int id) {
        String msg = subscriptionPlanService.adminInvalidateUserSubscription(id);
        return msg.isEmpty() ? R.success() : R.success(msg);
    }

    @DeleteMapping("/user_subscriptions/{id}")
    public Result<?> deleteUserSubscription(@PathVariable int id) {
        String msg = subscriptionPlanService.adminDeleteUserSubscription(id);
        return msg.isEmpty() ? R.success() : R.success(msg);
    }

    private boolean isPaymentComplianceConfirmed() {
        return "true".equalsIgnoreCase(optionService.getValue(PAYMENT_COMPLIANCE_KEY));
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

    @Data
    public static class UpsertPlanRequest {
        @NotNull(message = "plan 不能为空")
        private SubscriptionPlan plan;
    }

    @Data
    public static class UpdatePlanStatusRequest {
        @NotNull(message = "enabled 不能为空")
        private Boolean enabled;
    }

    @Data
    public static class BindSubscriptionRequest {
        @NotNull(message = "user_id 不能为空")
        private Integer userId;
        @NotNull(message = "plan_id 不能为空")
        private Integer planId;
    }

    @Data
    public static class CreateUserSubscriptionRequest {
        @NotNull(message = "plan_id 不能为空")
        private Integer planId;
    }
}
