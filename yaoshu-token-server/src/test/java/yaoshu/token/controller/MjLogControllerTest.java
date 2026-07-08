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
import yaoshu.token.pojo.entity.Midjourney;
import yaoshu.token.service.MidjourneyService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SystemService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MjLogController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MjLogController — MJ 日志 R.success / ResultException 分支")
class MjLogControllerTest {

    @Mock
    private MidjourneyService midjourneyService;

    @Mock
    private SystemService systemService;

    @Mock
    private OptionService optionService;

    @InjectMocks
    private MjLogController controller;

    @Test
    @DisplayName("getAll 管理员查询所有 MJ 任务（R.success）")
    void getAllSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("channel_id")).thenReturn("1");
        when(request.getParameter("mj_id")).thenReturn(null);
        when(midjourneyService.getAllTasks(1, null, null, null)).thenReturn(List.of());

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getSelf 有效 userId 返回用户任务（R.success）")
    void getSelfValidUserSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(request.getParameter("mj_id")).thenReturn(null);
        when(midjourneyService.getAllUserTask(5, null, null, null)).thenReturn(List.of());

        Result<?> result = controller.getSelf(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getSelf userId 无效（<=0）抛 ResultException（code=600）")
    void getSelfInvalidUserThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(0);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getSelf(request));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("getSelf userId 为 null 抛 ResultException（code=600）")
    void getSelfNullUserThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getSelf(request));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getAll 启用转发时重写 image_url（R.success，覆盖 rewrite 分支）")
    void getAllWithForwardEnabled() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("channel_id")).thenReturn(null);
        when(request.getParameter("mj_id")).thenReturn(null);
        Midjourney m = new Midjourney();
        m.setMjId("mj1");
        m.setImageUrl("https://old/img.png");
        when(midjourneyService.getAllTasks(null, null, null, null)).thenReturn(List.of(m));
        when(systemService.isMjForwardEnabled()).thenReturn(true);
        when(optionService.getValue("ServerAddress")).thenReturn("https://my.site");

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
    }
}
