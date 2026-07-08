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
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.service.TaskService;
import yaoshu.token.service.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TaskLogController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskLogController — 任务日志 R.success / ResultException 分支")
class TaskLogControllerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private UserService userService;

    @InjectMocks
    private TaskLogController controller;

    @Test
    @DisplayName("getAll 管理员查询所有任务（R.success）")
    void getAllSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("platform")).thenReturn("suno");
        when(taskService.taskGetAllTasks(any())).thenReturn(List.of());

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getSelf 有效 userId 返回用户任务（R.success）")
    void getSelfValidUserSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(taskService.taskGetAllUserTask(eq(5), any())).thenReturn(List.of());

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
    @DisplayName("getAll 含任务时补全 username（R.success）")
    void getAllWithTasks() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Task task = new Task();
        task.setUserId(3);
        when(taskService.taskGetAllTasks(any())).thenReturn(List.of(task));

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
    }
}
