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
import yaoshu.token.pojo.entity.Redemption;
import yaoshu.token.pojo.ipo.RedemptionIPO;
import yaoshu.token.service.RedemptionService;
import yaoshu.token.service.TopupService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedemptionController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedemptionController — 兑换码 CRUD R.success / ResultException 分支")
class RedemptionControllerTest {

    @Mock
    private RedemptionService redemptionService;

    @Mock
    private TopupService topupService;

    @InjectMocks
    private RedemptionController controller;

    @Test
    @DisplayName("getAll 返回兑换码分页（R.success）")
    void getAllSuccess() {
        when(redemptionService.getAll()).thenReturn(List.of());

        Result<?> result = controller.getAll(mock(HttpServletRequest.class));

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("get 不存在的兑换码抛 ResultException（code=600）")
    void getNotFoundThrows() {
        when(redemptionService.getById(99)).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.get(99));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("get 存在的兑换码成功（R.success）")
    void getFoundSuccess() {
        Redemption r = new Redemption();
        r.setId(1);
        when(redemptionService.getById(1)).thenReturn(r);

        Result<?> result = controller.get(1);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("add 合规未确认抛 ResultException（code=600）")
    void addComplianceNotConfirmed() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(false);
        RedemptionIPO.Create ipo = new RedemptionIPO.Create();

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.add(mock(HttpServletRequest.class), ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("add name 为空抛 ResultException（code=600）")
    void addNameEmptyThrows() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        RedemptionIPO.Create ipo = new RedemptionIPO.Create();
        ipo.setName("");

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.add(mock(HttpServletRequest.class), ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("add name 超 20 字符抛 ResultException（code=600）")
    void addNameTooLongThrows() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        RedemptionIPO.Create ipo = new RedemptionIPO.Create();
        ipo.setName("a".repeat(21));
        ipo.setCount(1);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.add(mock(HttpServletRequest.class), ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("add count>100 抛 ResultException（code=600）")
    void addCountTooLargeThrows() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        RedemptionIPO.Create ipo = new RedemptionIPO.Create();
        ipo.setName("valid");
        ipo.setCount(101);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.add(mock(HttpServletRequest.class), ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("add 全部校验通过返回生成的 key（R.success）")
    void addSuccess() {
        when(topupService.isPaymentComplianceConfirmed()).thenReturn(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(3);
        RedemptionIPO.Create ipo = new RedemptionIPO.Create();
        ipo.setName("valid");
        ipo.setCount(5);
        ipo.setQuota(1000);
        when(redemptionService.add(eq(3), any(Redemption.class))).thenReturn(List.of("KEY-1", "KEY-2"));

        Result<?> result = controller.add(request, ipo);

        assertTrue(result.isFlag());
        assertEquals(List.of("KEY-1", "KEY-2"), result.getData());
    }

    @Test
    @DisplayName("update 缺少 id 抛 ResultException（code=600）")
    void updateMissingIdThrows() {
        RedemptionIPO.Update ipo = new RedemptionIPO.Update();

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.update(ipo, mock(HttpServletRequest.class)));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("deleteInvalid 返回删除数量（R.success）")
    void deleteInvalidSuccess() {
        when(redemptionService.deleteInvalid()).thenReturn(5L);

        Result<?> result = controller.deleteInvalid();

        assertTrue(result.isFlag());
        assertEquals(5L, result.getData());
    }

    @Test
    @DisplayName("delete 成功（R.success）")
    void deleteSuccess() {
        Result<?> result = controller.delete(1);

        assertTrue(result.isFlag());
        verify(redemptionService).delete(1);
    }
}
