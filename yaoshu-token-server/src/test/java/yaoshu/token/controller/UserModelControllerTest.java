package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.UserService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserModelController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserModelController — 用户模型列表 R.success 分支")
class UserModelControllerTest {

    @Mock
    private ModelService modelService;

    @Mock
    private UserService userService;

    @Mock
    private OptionService optionService;

    @InjectMocks
    private UserModelController controller;

    @Test
    @DisplayName("getUserModels 已登录用户返回模型列表（R.success，含 object=list）")
    void getUserModelsLoggedInSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        // SelfUseModeEnabled=true 简化分支（跳过第二次 getById 的 setting 解析）
        when(optionService.getValue("SelfUseModeEnabled")).thenReturn("true");
        User user = new User();
        user.setGroup("vip");
        when(userService.getById(5, false)).thenReturn(user);
        when(modelService.listUserOpenAIModels(eq("vip"), isNull(), isNull(),
                eq(false), eq(true))).thenReturn(List.of(Map.of("id", "gpt-4o")));

        Result<?> result = controller.getUserModels(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getUserModels userId 为 null 时回退 default 分组（R.success）")
    void getUserModelsNullUserFallbackDefault() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);
        when(optionService.getValue("SelfUseModeEnabled")).thenReturn("false");
        when(modelService.listUserOpenAIModels(eq("default"), isNull(), isNull(),
                eq(false), eq(false))).thenReturn(List.of());

        Result<?> result = controller.getUserModels(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getUserModels 用户分组为空时回退 default（R.success）")
    void getUserModelsEmptyGroupFallback() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(optionService.getValue("SelfUseModeEnabled")).thenReturn("true");
        User user = new User();
        user.setGroup("");
        when(userService.getById(5, false)).thenReturn(user);
        when(modelService.listUserOpenAIModels(eq("default"), isNull(), isNull(),
                eq(false), eq(true))).thenReturn(List.of());

        Result<?> result = controller.getUserModels(request);

        assertTrue(result.isFlag());
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
