package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.controller.PaymentController.AmountRequest;
import yaoshu.token.controller.PaymentController.EpayPayRequest;
import yaoshu.token.controller.PaymentController.StripePayRequest;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.service.EpayService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.PaymentGatewayService;
import yaoshu.token.service.SubscriptionPlanService;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.WaffoPancakeService;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PaymentController 白盒测试，聚焦支付操作的合规 guard 分支与成功路径。
 * <p>
 * Webhook 端点直接写 HttpServletResponse（不返回 Result），属 IT 职责，不在此覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController — 支付操作 R.success / ResultException 分支")
class PaymentControllerTest {

    @Mock
    private TopupService topupService;
    @Mock
    private UserService userService;
    @Mock
    private EpayService epayService;
    @Mock
    private PaymentGatewayService paymentGatewayService;
    @Mock
    private WaffoPancakeService waffoPancakeService;
    @Mock
    private OptionService optionService;
    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @InjectMocks
    private PaymentController controller;

    private HttpServletRequest requestWithUser(int userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(userId);
        return request;
    }

    @Test
    @DisplayName("requestEpay 合规未确认抛 ResultException（code=600）")
    void requestEpayComplianceNotConfirmed() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(false);
        EpayPayRequest body = new EpayPayRequest();
        body.setAmount(100L);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.requestEpay(mock(HttpServletRequest.class), body));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("requestEpay epay 未启用抛 ResultException（code=600）")
    void requestEpayNotEnabled() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        when(topupService.isEpayTopupEnabled()).thenReturn(false);
        EpayPayRequest body = new EpayPayRequest();
        body.setAmount(100L);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.requestEpay(mock(HttpServletRequest.class), body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("requestEpay 成功返回 {data,url}（R.success）")
    void requestEpaySuccess() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        when(topupService.isEpayTopupEnabled()).thenReturn(true);
        TopUp topUp = new TopUp();
        topUp.setTradeNo("T123");
        topUp.setMoney(10.0);
        when(topupService.createPendingEpayOrder(5, 100L, "alipay")).thenReturn(topUp);
        EpayService.PurchaseRequest pr = mock(EpayService.PurchaseRequest.class);
        when(pr.getParams()).thenReturn(Map.of("out_trade_no", "T123"));
        when(pr.getUrl()).thenReturn("https://pay.example.com");
        when(epayService.buildCallbackUrl("/api/user/epay/notify")).thenReturn("https://cb.example.com");
        when(epayService.buildConsoleReturnUrl("/console/log")).thenReturn("https://console.example.com");
        when(epayService.buildPurchaseRequest(eq("alipay"), eq("T123"), anyString(), anyString(),
                anyString(), anyString())).thenReturn(pr);
        EpayPayRequest body = new EpayPayRequest();
        body.setAmount(100L);
        body.setPaymentMethod("alipay");

        Result<?> result = controller.requestEpay(requestWithUser(5), body);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("requestAmount 金额低于最小值抛 ResultException（code=600）")
    void requestAmountTooLowThrows() {
        when(topupService.getMinTopup()).thenReturn(100L);
        AmountRequest body = new AmountRequest();
        body.setAmount(10L);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.requestAmount(mock(HttpServletRequest.class), body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("requestAmount 成功返回应付金额（R.success）")
    void requestAmountSuccess() {
        when(topupService.getMinTopup()).thenReturn(1L);
        when(userService.getUserGroup(5)).thenReturn("default");
        when(topupService.calculatePayMoney(100L, "default")).thenReturn(new BigDecimal("9.90"));
        AmountRequest body = new AmountRequest();
        body.setAmount(100L);

        Result<?> result = controller.requestAmount(requestWithUser(5), body);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("requestStripePay 合规未确认抛 ResultException（code=600）")
    void requestStripePayComplianceNotConfirmed() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(false);
        StripePayRequest body = new StripePayRequest();

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.requestStripePay(mock(HttpServletRequest.class), body));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("requestStripePay 成功（R.success）")
    void requestStripePaySuccess() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        PaymentGatewayService.GatewayLaunchResult launch = mock(PaymentGatewayService.GatewayLaunchResult.class);
        when(launch.getData()).thenReturn(Map.of("client_secret", "cs_x"));
        when(paymentGatewayService.createStripeTopup(eq(5), any())).thenReturn(launch);
        StripePayRequest body = new StripePayRequest();
        body.setAmount(100L);

        Result<?> result = controller.requestStripePay(requestWithUser(5), body);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("requestStripePay Service 抛异常转 ResultException（code=600）")
    void requestStripePayRuntimeToResultException() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        when(paymentGatewayService.createStripeTopup(eq(5), any()))
                .thenThrow(new RuntimeException("网关不可用"));
        StripePayRequest body = new StripePayRequest();
        body.setAmount(100L);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.requestStripePay(requestWithUser(5), body));
        assertEquals(600, ex.getResult().getCode());
    }
}
