package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import ai.yue.library.web.util.ServletUtils;
import com.github.pagehelper.PageInfo;
import com.github.pagehelper.page.PageMethod;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.controller.TopupController.TopupRequest;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.pojo.vo.TopupInfoVO;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TopupController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TopupController — 充值 R.success / ResultException 分支")
class TopupControllerTest {

    @Mock
    private TopupService topupService;

    @Mock
    private UserService userService;

    @InjectMocks
    private TopupController controller;

    private HttpServletRequest requestWithUser(int userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(userId);
        return request;
    }

    @Test
    @DisplayName("getInfo 返回充值信息（R.success + TopupInfoVO）")
    void getInfoSuccess() {
        HttpServletRequest request = requestWithUser(5);
        when(userService.getUserGroup(5)).thenReturn("vip");
        TopupInfoVO info = TopupInfoVO.builder().minTopup(1L).stripeMinTopup(1).build();
        when(topupService.getTopupInfo("vip")).thenReturn(info);

        Result<?> result = controller.getInfo(request);

        assertTrue(result.isFlag());
        assertEquals(info, result.getData());
    }

    @Test
    @DisplayName("getSelf 返回用户充值记录分页（PageInfo 包装）")
    void getSelfSuccess() {
        HttpServletRequest request = requestWithUser(5);
        when(request.getParameter("keyword")).thenReturn("kw");
        List<TopUp> topups = List.of(new TopUp());
        when(topupService.listUserTopups(5, "kw")).thenReturn(topups);

        try (MockedStatic<ServletUtils> suMock = mockStatic(ServletUtils.class);
             MockedStatic<PageMethod> pmMock = mockStatic(PageMethod.class)) {
            suMock.when(() -> ServletUtils.getRequest()).thenReturn(request);

            Result<?> result = controller.getSelf(request);

            assertTrue(result.isFlag());
            assertInstanceOf(PageInfo.class, result.getData());
            verify(topupService).listUserTopups(5, "kw");
        }
    }

    @Test
    @DisplayName("topup 合规未确认抛 ResultException（code=600）")
    void topupComplianceNotConfirmed() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(false);
        TopupRequest body = new TopupRequest();
        body.setKey("KEY-123");

        // 合规校验先于 userId 读取，使用未 stub 的 request 即可
        ResultException ex = assertThrows(ResultException.class,
                () -> controller.topup(mock(HttpServletRequest.class), body));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("topup 合规已确认且兑换成功返回 quota（R.success）")
    void topupRedeemSuccess() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        when(topupService.redeem("KEY-123", 5)).thenReturn(500);
        TopupRequest body = new TopupRequest();
        body.setKey("KEY-123");

        Result<?> result = controller.topup(requestWithUser(5), body);

        assertTrue(result.isFlag());
        assertEquals(500, result.getData());
    }
}
