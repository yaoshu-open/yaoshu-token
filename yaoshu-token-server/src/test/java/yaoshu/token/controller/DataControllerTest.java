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
import yaoshu.token.mapper.QuotaMapper;
import yaoshu.token.pojo.entity.QuotaData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DataController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataController — 配额数据 R.success / ResultException 分支")
class DataControllerTest {

    @Mock
    private QuotaMapper quotaMapper;

    @InjectMocks
    private DataController controller;

    @Test
    @DisplayName("getAllQuotaDates 按 username 过滤成功（R.success）")
    void getAllByUsernameSuccess() {
        when(quotaMapper.selectList(any())).thenReturn(List.of());

        Result<?> result = controller.getAllQuotaDates(null, null, "alice");

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("getAllQuotaDates 无 username 走聚合查询成功（R.success）")
    void getAllAggregatedSuccess() {
        when(quotaMapper.getAllQuotaDatesAgg(any(), any())).thenReturn(List.of());

        Result<?> result = controller.getAllQuotaDates(1000L, 2000L, null);

        assertTrue(result.isFlag());
        verify(quotaMapper).getAllQuotaDatesAgg(1000L, 2000L);
    }

    @Test
    @DisplayName("getAllQuotaDates Service 异常转 ResultException（code=600）")
    void getAllRuntimeToResultException() {
        when(quotaMapper.getAllQuotaDatesAgg(any(), any())).thenThrow(new RuntimeException("DB错误"));

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.getAllQuotaDates(null, null, null));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getQuotaDatesByUser 成功（R.success）")
    void getQuotaDatesByUserSuccess() {
        when(quotaMapper.getQuotaDataGroupByUser(any(), any())).thenReturn(List.of());

        Result<?> result = controller.getQuotaDatesByUser(1000L, 2000L);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getSelfQuotaDates 时间跨度超 1 个月抛 ResultException（code=600）")
    void getSelfSpanTooLargeThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        long start = 1_000_000L;
        long end = start + 3_000_000L; // 远超 2592000

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.getSelfQuotaDates(request, start, end));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("getSelfQuotaDates 未登录抛 ResultException（code=600）")
    void getSelfNotLoggedInThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.getSelfQuotaDates(request, null, null));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getSelfQuotaDates 正常查询成功（R.success）")
    void getSelfSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(quotaMapper.selectList(any())).thenReturn(List.of(new QuotaData()));

        Result<?> result = controller.getSelfQuotaDates(request, 1000L, 2000L);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }
}
