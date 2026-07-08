package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.controller.SubscriptionAdminController.BindSubscriptionRequest;
import yaoshu.token.controller.SubscriptionAdminController.UpsertPlanRequest;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SubscriptionPlanService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * SubscriptionAdminController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionAdminController — 订阅管理 R.success / ResultException 分支")
class SubscriptionAdminControllerTest {

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @Mock
    private OptionService optionService;

    @InjectMocks
    private SubscriptionAdminController controller;

    private void complianceConfirmed() {
        when(optionService.getValue("payment_setting.compliance_confirmed")).thenReturn("true");
    }

    @Test
    @DisplayName("listPlans 返回计划列表（R.success）")
    void listPlansSuccess() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(1);
        when(subscriptionPlanService.listAllPlans()).thenReturn(List.of(plan));

        Result<?> result = controller.listPlans();

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("createPlan 合规未确认抛 ResultException（code=600）")
    void createPlanComplianceNotConfirmed() {
        when(optionService.getValue("payment_setting.compliance_confirmed")).thenReturn("false");
        UpsertPlanRequest body = new UpsertPlanRequest();
        body.setPlan(new SubscriptionPlan());

        ResultException ex = assertThrows(ResultException.class, () -> controller.createPlan(body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("createPlan 合规已确认成功（R.success）")
    void createPlanSuccess() {
        complianceConfirmed();
        SubscriptionPlan plan = new SubscriptionPlan();
        UpsertPlanRequest body = new UpsertPlanRequest();
        body.setPlan(plan);
        when(subscriptionPlanService.createPlan(plan)).thenReturn(plan);

        Result<?> result = controller.createPlan(body);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("updatePlan 合规未确认抛 ResultException（code=600）")
    void updatePlanComplianceNotConfirmed() {
        when(optionService.getValue("payment_setting.compliance_confirmed")).thenReturn(null);
        UpsertPlanRequest body = new UpsertPlanRequest();
        body.setPlan(new SubscriptionPlan());

        ResultException ex = assertThrows(ResultException.class, () -> controller.updatePlan(1, body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("updatePlan 合规已确认成功（R.success）")
    void updatePlanSuccess() {
        complianceConfirmed();
        UpsertPlanRequest body = new UpsertPlanRequest();
        body.setPlan(new SubscriptionPlan());

        Result<?> result = controller.updatePlan(1, body);

        assertTrue(result.isFlag());
        verify(subscriptionPlanService).updatePlan(anyInt(), any());
    }

    @Test
    @DisplayName("updatePlanStatus 合规已确认成功（R.success）")
    void updatePlanStatusSuccess() {
        complianceConfirmed();
        SubscriptionAdminController.UpdatePlanStatusRequest body =
                new SubscriptionAdminController.UpdatePlanStatusRequest();
        body.setEnabled(true);

        Result<?> result = controller.updatePlanStatus(1, body);

        assertTrue(result.isFlag());
        verify(subscriptionPlanService).updatePlanStatus(1, true);
    }

    @Test
    @DisplayName("bind 合规未确认抛 ResultException（code=600）")
    void bindComplianceNotConfirmed() {
        when(optionService.getValue("payment_setting.compliance_confirmed")).thenReturn("false");
        BindSubscriptionRequest body = new BindSubscriptionRequest();
        body.setUserId(1);
        body.setPlanId(2);

        ResultException ex = assertThrows(ResultException.class, () -> controller.bind(body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("bind 成功且 msg 为空时返回 R.success()")
    void bindSuccessEmptyMsg() {
        complianceConfirmed();
        BindSubscriptionRequest body = new BindSubscriptionRequest();
        body.setUserId(1);
        body.setPlanId(2);
        when(subscriptionPlanService.adminBindSubscription(1, 2)).thenReturn("");

        Result<?> result = controller.bind(body);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("bind 成功且 msg 非空时返回 R.success(msg)")
    void bindSuccessWithMsg() {
        complianceConfirmed();
        BindSubscriptionRequest body = new BindSubscriptionRequest();
        body.setUserId(1);
        body.setPlanId(2);
        when(subscriptionPlanService.adminBindSubscription(1, 2)).thenReturn("已绑定");

        Result<?> result = controller.bind(body);

        assertTrue(result.isFlag());
        assertEquals("已绑定", result.getData());
    }

    @Test
    @DisplayName("listUserSubscriptions 返回用户订阅列表（R.success）")
    void listUserSubscriptionsSuccess() {
        when(subscriptionPlanService.listAllUserSubscriptions(5)).thenReturn(List.of());

        Result<?> result = controller.listUserSubscriptions(5);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("invalidateUserSubscription msg 为空时 R.success()")
    void invalidateSuccess() {
        when(subscriptionPlanService.adminInvalidateUserSubscription(anyInt())).thenReturn("");

        Result<?> result = controller.invalidateUserSubscription(3);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("deleteUserSubscription msg 为空时 R.success()，调用了 delete")
    void deleteUserSubscriptionSuccess() {
        when(subscriptionPlanService.adminDeleteUserSubscription(anyInt())).thenReturn("");

        Result<?> result = controller.deleteUserSubscription(3);

        assertTrue(result.isFlag());
        verify(subscriptionPlanService).adminDeleteUserSubscription(3);
    }
}
