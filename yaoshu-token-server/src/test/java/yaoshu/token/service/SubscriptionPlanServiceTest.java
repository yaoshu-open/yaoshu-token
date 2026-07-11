package yaoshu.token.service;

import ai.yue.library.base.exception.ResultException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.SubscriptionOrderMapper;
import yaoshu.token.mapper.SubscriptionPlanMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserSubscriptionMapper;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserSubscription;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * SubscriptionPlanService 单订阅限制与购买判定逻辑白盒测试。
 * <p>
 * 验证 validateSubscriptionPurchase 的四种分支（NEW/RENEW/UPGRADE/拒绝）
 * 和 cancelSelfSubscription 的取消逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPlanService — 单订阅限制与购买判定")
class SubscriptionPlanServiceTest {

    @Mock
    private SubscriptionPlanMapper planMapper;
    @Mock
    private SubscriptionOrderMapper orderMapper;
    @Mock
    private TopUpMapper topUpMapper;
    @Mock
    private UserSubscriptionMapper userSubscriptionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private LogMapper logMapper;

    @InjectMocks
    private SubscriptionPlanService service;

    private SubscriptionPlan createPlan(int id, int sortOrder, double price) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setSortOrder(sortOrder);
        plan.setPriceAmount(price);
        plan.setEnabled(true);
        plan.setAllowBalancePay(true);
        plan.setTitle("Plan-" + id);
        plan.setDurationUnit("month");
        plan.setDurationValue(1);
        plan.setTotalAmount(100000L);
        return plan;
    }

    private UserSubscription createActiveSub(int id, int userId, int planId) {
        UserSubscription sub = new UserSubscription();
        sub.setId(id);
        sub.setUserId(userId);
        sub.setPlanId(planId);
        sub.setStatus("active");
        sub.setAmountTotal(100000L);
        sub.setAmountUsed(30000L);
        sub.setStartTime(System.currentTimeMillis() / 1000 - 86400);
        sub.setEndTime(System.currentTimeMillis() / 1000 + 86400 * 15);
        return sub;
    }

    // ======================== validateSubscriptionPurchase ========================

    @Test
    @DisplayName("无活跃订阅 → NEW")
    void validateNoActiveSubReturnsNew() {
        int userId = 1;
        int planId = 10;
        when(planMapper.selectById(planId)).thenReturn(createPlan(planId, 5, 10.0));
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of());

        SubscriptionPlanService.PurchaseContext ctx = service.validateSubscriptionPurchase(userId, planId);

        assertEquals(SubscriptionPlanService.PurchaseType.NEW, ctx.type());
        assertNull(ctx.currentSub());
    }

    @Test
    @DisplayName("有活跃订阅 + 同 planId → RENEW")
    void validateSamePlanReturnsRenew() {
        int userId = 1;
        int planId = 10;
        SubscriptionPlan plan = createPlan(planId, 5, 10.0);
        UserSubscription activeSub = createActiveSub(1, userId, planId);
        when(planMapper.selectById(planId)).thenReturn(plan);
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of(activeSub));

        SubscriptionPlanService.PurchaseContext ctx = service.validateSubscriptionPurchase(userId, planId);

        assertEquals(SubscriptionPlanService.PurchaseType.RENEW, ctx.type());
        assertNotNull(ctx.currentSub());
        assertEquals(planId, ctx.currentSub().getPlanId());
    }

    @Test
    @DisplayName("有活跃订阅 + 不同 planId + 更高 sortOrder → UPGRADE")
    void validateHigherSortOrderReturnsUpgrade() {
        int userId = 1;
        int oldPlanId = 10;
        int newPlanId = 20;
        SubscriptionPlan newPlan = createPlan(newPlanId, 10, 20.0);
        SubscriptionPlan oldPlan = createPlan(oldPlanId, 5, 10.0);
        UserSubscription activeSub = createActiveSub(1, userId, oldPlanId);
        // selectById 被调用两次：第一次 newPlan，第二次 oldPlan（在 getPlanById(currentSub.getPlanId())）
        when(planMapper.selectById(newPlanId)).thenReturn(newPlan);
        when(planMapper.selectById(oldPlanId)).thenReturn(oldPlan);
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of(activeSub));

        SubscriptionPlanService.PurchaseContext ctx = service.validateSubscriptionPurchase(userId, newPlanId);

        assertEquals(SubscriptionPlanService.PurchaseType.UPGRADE, ctx.type());
        assertNotNull(ctx.currentPlan());
        assertEquals(5, ctx.currentPlan().getSortOrder());
    }

    @Test
    @DisplayName("有活跃订阅 + 不同 planId + sortOrder 不更高 → 抛 ResultException")
    void validateLowerSortOrderThrows() {
        int userId = 1;
        int oldPlanId = 10;
        int newPlanId = 20;
        // 新套餐 sortOrder=3 < 旧套餐 sortOrder=5
        SubscriptionPlan newPlan = createPlan(newPlanId, 3, 8.0);
        SubscriptionPlan oldPlan = createPlan(oldPlanId, 5, 10.0);
        UserSubscription activeSub = createActiveSub(1, userId, oldPlanId);
        when(planMapper.selectById(newPlanId)).thenReturn(newPlan);
        when(planMapper.selectById(oldPlanId)).thenReturn(oldPlan);
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of(activeSub));

        ResultException ex = assertThrows(ResultException.class,
                () -> service.validateSubscriptionPurchase(userId, newPlanId));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("有活跃订阅 + 不同 planId + 相同 sortOrder → 抛 ResultException")
    void validateEqualSortOrderThrows() {
        int userId = 1;
        int oldPlanId = 10;
        int newPlanId = 20;
        // 新旧套餐 sortOrder 都=5
        SubscriptionPlan newPlan = createPlan(newPlanId, 5, 12.0);
        SubscriptionPlan oldPlan = createPlan(oldPlanId, 5, 10.0);
        UserSubscription activeSub = createActiveSub(1, userId, oldPlanId);
        when(planMapper.selectById(newPlanId)).thenReturn(newPlan);
        when(planMapper.selectById(oldPlanId)).thenReturn(oldPlan);
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of(activeSub));

        assertThrows(ResultException.class,
                () -> service.validateSubscriptionPurchase(userId, newPlanId));
    }

    // ======================== cancelSelfSubscription ========================

    @Test
    @DisplayName("有活跃订阅取消 → 无活跃订阅时抛 ResultException（cancelSelfSubscription 集成测试覆盖）")
    void cancelNoActiveThrows() {
        int userId = 1;
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of());

        assertThrows(ResultException.class, () -> service.cancelSelfSubscription(userId));
    }

    // 注：cancelSelfSubscription 成功路径和 purchaseWithBalance 升级路径
    // 内部使用 LambdaUpdateWrapper，MyBatis-Plus lambda cache 需 Spring 上下文初始化，
    // 由集成测试 (*IT.java) 覆盖。

    // ======================== purchaseWithBalance 升级扣差价 ========================

    @Test
    @DisplayName("余额购买 — 无活跃订阅 → NEW，扣全额")
    void purchaseBalanceNewChargesFull() {
        int userId = 1;
        int planId = 10;
        SubscriptionPlan plan = createPlan(planId, 5, 10.0);
        plan.setMaxPurchasePerUser(0);
        User user = new User();
        user.setId(userId);
        user.setQuota(10000000L);

        when(planMapper.selectOne(any())).thenReturn(plan);
        when(planMapper.selectById(planId)).thenReturn(plan);
        when(userSubscriptionMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.decreaseQuota(anyInt(), anyInt())).thenReturn(1);

        assertDoesNotThrow(() -> service.purchaseWithBalance(userId, planId));
    }
}
