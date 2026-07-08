package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.service.OAuthManagementService;
import yaoshu.token.service.OAuthManagementService.CustomOAuthProviderView;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CustomOAuthController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuthController — OAuth Provider CRUD R.success / ResultException 分支")
class CustomOAuthControllerTest {

    @Mock
    private OAuthManagementService oauthManagementService;

    @InjectMocks
    private CustomOAuthController controller;

    @Test
    @DisplayName("getAll 返回 Provider 列表（R.success）")
    void getAllSuccess() {
        CustomOAuthProviderView provider = mock(CustomOAuthProviderView.class);
        when(oauthManagementService.listProviders()).thenReturn(List.of(provider));

        Result<?> result = controller.getAll();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("get 单个 Provider 成功（R.success）")
    void getSuccess() {
        CustomOAuthProviderView provider = mock(CustomOAuthProviderView.class);
        when(oauthManagementService.getProvider(1)).thenReturn(provider);

        Result<?> result = controller.get(1);

        assertTrue(result.isFlag());
        assertEquals(provider, result.getData());
    }

    @Test
    @DisplayName("get Service 抛 RuntimeException 转 ResultException（code=600）")
    void getRuntimeToResultException() {
        when(oauthManagementService.getProvider(99)).thenThrow(new RuntimeException("不存在"));

        ResultException ex = assertThrows(ResultException.class, () -> controller.get(99));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("create 成功（R.success）")
    void createSuccess() {
        OAuthManagementService.CreateProviderCommand cmd = new OAuthManagementService.CreateProviderCommand();
        CustomOAuthProviderView created = mock(CustomOAuthProviderView.class);
        when(oauthManagementService.createProvider(cmd)).thenReturn(created);

        Result<?> result = controller.create(cmd);

        assertTrue(result.isFlag());
        assertEquals(created, result.getData());
    }

    @Test
    @DisplayName("update 成功（R.success）")
    void updateSuccess() {
        OAuthManagementService.UpdateProviderCommand cmd = new OAuthManagementService.UpdateProviderCommand();
        CustomOAuthProviderView view = mock(CustomOAuthProviderView.class);
        when(oauthManagementService.updateProvider(1, cmd)).thenReturn(view);

        Result<?> result = controller.update(1, cmd);

        assertTrue(result.isFlag());
        assertEquals(view, result.getData());
    }

    @Test
    @DisplayName("update Service 抛异常转 ResultException（code=600）")
    void updateRuntimeToResultException() {
        OAuthManagementService.UpdateProviderCommand cmd = new OAuthManagementService.UpdateProviderCommand();
        when(oauthManagementService.updateProvider(eq(1), any())).thenThrow(new RuntimeException("冲突"));

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(1, cmd));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("delete 成功（R.success）")
    void deleteSuccess() {
        Result<?> result = controller.delete(1);

        assertTrue(result.isFlag());
        verify(oauthManagementService).deleteProvider(1);
    }

    @Test
    @DisplayName("delete Service 抛异常转 ResultException（code=600）")
    void deleteRuntimeToResultException() {
        doThrow(new RuntimeException("删除失败")).when(oauthManagementService).deleteProvider(1);

        ResultException ex = assertThrows(ResultException.class, () -> controller.delete(1));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("fetchDiscovery 成功（R.success）")
    void fetchDiscoverySuccess() {
        OAuthManagementService.DiscoveryCommand cmd = new OAuthManagementService.DiscoveryCommand();
        when(oauthManagementService.fetchDiscovery(cmd)).thenReturn(Map.of("issuer", "https://x"));

        Result<?> result = controller.fetchDiscovery(cmd);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("fetchDiscovery Service 抛异常转 ResultException（code=600）")
    void fetchDiscoveryRuntimeToResultException() {
        OAuthManagementService.DiscoveryCommand cmd = new OAuthManagementService.DiscoveryCommand();
        when(oauthManagementService.fetchDiscovery(cmd)).thenThrow(new RuntimeException("连接失败"));

        ResultException ex = assertThrows(ResultException.class, () -> controller.fetchDiscovery(cmd));
        assertEquals(600, ex.getResult().getCode());
    }
}
