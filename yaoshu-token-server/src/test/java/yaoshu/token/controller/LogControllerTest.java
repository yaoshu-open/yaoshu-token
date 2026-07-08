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
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.service.LogManagementService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LogController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LogController — 日志查询 R.success / ResultException 分支")
class LogControllerTest {

    @Mock
    private LogManagementService logManagementService;

    @InjectMocks
    private LogController controller;

    @Test
    @DisplayName("getAll 管理员查询所有日志（R.success）")
    void getAllSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("type")).thenReturn("1");
        when(logManagementService.getAllLogs(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("deleteHistory 删除历史日志返回数量（R.success）")
    void deleteHistorySuccess() {
        when(logManagementService.deleteOldLogs(1000L)).thenReturn(50L);

        Result<?> result = controller.deleteHistory(1000L);

        assertTrue(result.isFlag());
        assertEquals(50L, result.getData());
    }

    @Test
    @DisplayName("getStat 返回统计汇总（R.success）")
    void getStatSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("type")).thenReturn(null);
        when(logManagementService.sumUsedQuota(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LogManagementService.StatResult(1000, 50, 2000));

        Result<?> result = controller.getStat(request);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getSelfStat 返回当前用户统计（R.success）")
    void getSelfStatSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("username")).thenReturn("alice");
        when(request.getParameter("type")).thenReturn(null);
        when(logManagementService.sumUsedQuota(any(), any(), any(), any(), eq("alice"), any(), any(), any()))
                .thenReturn(new LogManagementService.StatResult(500, 10, 800));

        Result<?> result = controller.getSelfStat(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getUserLogs 返回用户日志（R.success）")
    void getUserLogsSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(request.getParameter("type")).thenReturn(null);
        when(logManagementService.getUserLogs(eq(5), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new Log()));

        Result<?> result = controller.getUserLogs(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getByKey 有效 tokenId 返回日志（R.success）")
    void getByKeyValidTokenSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("token_id")).thenReturn(8);
        when(logManagementService.getByTokenId(8)).thenReturn(List.of());

        Result<?> result = controller.getByKey(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getChannelAffinityCache ruleName 为空抛 ResultException（code=600）")
    void getChannelAffinityCacheEmptyRuleNameThrows() {
        ResultException ex = assertThrows(ResultException.class,
                () -> controller.getChannelAffinityCache("  ", null, "fp"));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getChannelAffinityCache keyFp 为空抛 ResultException（code=600）")
    void getChannelAffinityCacheEmptyKeyFpThrows() {
        ResultException ex = assertThrows(ResultException.class,
                () -> controller.getChannelAffinityCache("rule1", null, "  "));
        assertEquals(600, ex.getResult().getCode());
    }
}
