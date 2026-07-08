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
import yaoshu.token.config.operation.CheckinSettingConfig;
import yaoshu.token.service.CheckinService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * CheckinController 白盒测试，验证 R.success / ResultException 分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckinController — 签到 R.success / ResultException 分支")
class CheckinControllerTest {

    @Mock
    private CheckinService checkinService;

    @InjectMocks
    private CheckinController controller;

    private HttpServletRequest mockRequest(int userId) {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(userId);
        return request;
    }

    private CheckinSettingConfig enabledSetting() {
        CheckinSettingConfig setting = new CheckinSettingConfig();
        setting.setEnabled(true);
        setting.setMinQuota(1000);
        setting.setMaxQuota(10000);
        return setting;
    }

    @Test
    @DisplayName("getStatus 签到启用时返回 enabled/stats 数据（R.success flag=true code=200）")
    void getStatusEnabledSuccess() {
        HttpServletRequest request = mockRequest(5);
        when(request.getParameter("month")).thenReturn("2026-06");
        when(checkinService.getSetting()).thenReturn(enabledSetting());
        Map<String, Object> stats = Map.of("count", 3);
        when(checkinService.getUserCheckinStats(5, "2026-06")).thenReturn(stats);

        Result<?> result = controller.getStatus(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getStatus 签到未启用抛 ResultException（code=600 flag=false）")
    void getStatusDisabledThrows() {
        HttpServletRequest request = mockRequest(5);
        CheckinSettingConfig disabled = new CheckinSettingConfig();
        disabled.setEnabled(false);
        when(checkinService.getSetting()).thenReturn(disabled);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getStatus(request));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("getStatus 默认月份（month 为空时取当前月）成功")
    void getStatusDefaultMonth() {
        HttpServletRequest request = mockRequest(5);
        when(request.getParameter("month")).thenReturn(null);
        when(checkinService.getSetting()).thenReturn(enabledSetting());
        when(checkinService.getUserCheckinStats(org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Map.of());

        Result<?> result = controller.getStatus(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("doCheckin 成功返回签到数据（R.success）")
    void doCheckinSuccess() {
        HttpServletRequest request = mockRequest(5);
        Map<String, Object> data = Map.of("quota", 1000);
        when(checkinService.doCheckin(5)).thenReturn(data);

        Result<?> result = controller.doCheckin(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertEquals(data, result.getData());
    }

    @Test
    @DisplayName("doCheckin Service 抛 RuntimeException 转为 ResultException（code=600）")
    void doCheckinRuntimeToResultException() {
        HttpServletRequest request = mockRequest(5);
        when(checkinService.doCheckin(5)).thenThrow(new RuntimeException("今日已签到"));

        ResultException ex = assertThrows(ResultException.class, () -> controller.doCheckin(request));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }
}
