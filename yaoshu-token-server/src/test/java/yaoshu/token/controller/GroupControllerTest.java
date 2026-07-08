package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.config.AutoGroupConfig;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.service.GroupService;
import yaoshu.token.service.UserService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GroupController 白盒测试，验证静态 GroupRatioConfig / GroupService 调用的 R.success 分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupController — 用户分组 R.success 分支")
class GroupControllerTest {

    @Mock
    private GroupService groupService;

    @Mock
    private UserService userService;

    @InjectMocks
    private GroupController controller;

    @Test
    @DisplayName("getAll 返回 GroupRatioConfig 配置的所有分组名（R.success）")
    void getAllSuccess() {
        try (MockedStatic<GroupRatioConfig> mocked = mockStatic(GroupRatioConfig.class)) {
            mocked.when(GroupRatioConfig::getGroupRatioCopy)
                    .thenReturn(Map.of("default", 1.0, "vip", 0.5));

            Result<?> result = controller.getAll();

            assertTrue(result.isFlag());
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
        }
    }

    @Test
    @DisplayName("getSelfGroups 已登录用户返回可用分组（R.success）")
    void getSelfGroupsLoggedInSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(5);
        when(userService.getUserGroup(5)).thenReturn("default");
        try (MockedStatic<GroupService> gsMock = mockStatic(GroupService.class);
             MockedStatic<GroupRatioConfig> ignored = mockStatic(GroupRatioConfig.class)) {
            gsMock.when(() -> GroupService.getUserUsableGroups("default"))
                    .thenReturn(Map.of("default", "默认分组"));
            gsMock.when(() -> GroupService.getUserGroupRatio("default", "default"))
                    .thenReturn(1.0);

            Result<?> result = controller.getSelfGroups(request);

            assertTrue(result.isFlag());
            assertNotNull(result.getData());
        }
    }

    @Test
    @DisplayName("getSelfGroups userId 为 null 时回退 default 分组（R.success）")
    void getSelfGroupsNullUserFallback() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);
        // userId null 时 Controller 不调 userService.getUserGroup，直接用 "default"
        try (MockedStatic<GroupService> gsMock = mockStatic(GroupService.class);
             MockedStatic<GroupRatioConfig> ignored = mockStatic(GroupRatioConfig.class)) {
            gsMock.when(() -> GroupService.getUserUsableGroups("default"))
                    .thenReturn(Map.of("default", "默认"));
            gsMock.when(() -> GroupService.getUserGroupRatio(anyString(), anyString()))
                    .thenReturn(1.0);

            Result<?> result = controller.getSelfGroups(request);

            assertTrue(result.isFlag());
        }
    }

    @Test
    @DisplayName("getUserGroups 未登录入口（userId null）返回全局分组（R.success）")
    void getUserGroupsAnonymousSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);
        try (MockedStatic<GroupService> gsMock = mockStatic(GroupService.class);
             MockedStatic<GroupRatioConfig> ignored = mockStatic(GroupRatioConfig.class)) {
            gsMock.when(() -> GroupService.getUserUsableGroups(""))
                    .thenReturn(Map.of("default", "默认"));
            gsMock.when(() -> GroupService.getUserGroupRatio("", "default"))
                    .thenReturn(1.0);

            Result<?> result = controller.getUserGroups(request);

            assertTrue(result.isFlag());
        }
    }

    @Test
    @DisplayName("getUserGroups 含 auto 分组时追加 AutoGroupConfig 条目（R.success）")
    void getUserGroupsContainsAuto() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);
        try (MockedStatic<GroupService> gsMock = mockStatic(GroupService.class);
             MockedStatic<GroupRatioConfig> grcIgnored = mockStatic(GroupRatioConfig.class);
             MockedStatic<AutoGroupConfig> autoMock = mockStatic(AutoGroupConfig.class)) {
            gsMock.when(() -> GroupService.getUserUsableGroups(""))
                    .thenReturn(Map.of("default", "默认", "auto", "自动"));
            gsMock.when(() -> GroupService.getUserGroupRatio(anyString(), anyString()))
                    .thenReturn(1.0);
            autoMock.when(AutoGroupConfig::getAutoGroups).thenReturn(List.of("ch1", "ch2"));

            Result<?> result = controller.getUserGroups(request);

            assertTrue(result.isFlag());
            Map<?, ?> data = (Map<?, ?>) result.getData();
            assertTrue(data.containsKey("auto"));
        }
    }
}
