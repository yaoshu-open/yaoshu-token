package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.pojo.ipo.DeploymentIPO;
import yaoshu.token.service.DeploymentService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DeploymentController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentController — 部署管理 R.success / ResultException 分支")
class DeploymentControllerTest {

    @Mock
    private DeploymentService deploymentService;

    @InjectMocks
    private DeploymentController controller;

    @Test
    @DisplayName("getSettings 返回部署设置（R.success）")
    void getSettingsSuccess() {
        when(deploymentService.getSettings()).thenReturn(Map.of("ready", true));

        Result<?> result = controller.getSettings();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("getAll 返回部署列表（R.success）")
    void getAllSuccess() {
        when(deploymentService.getAllDeployments("running", 1, 50)).thenReturn(Map.of("total", 0));

        Result<?> result = controller.getAll("running", 1, 50);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("testConnection 测试连接（R.success）")
    void testConnectionSuccess() {
        DeploymentIPO.TestConnection ipo = new DeploymentIPO.TestConnection();
        ipo.setApiKey("sk-x");
        when(deploymentService.testConnection("sk-x")).thenReturn(Map.of("ok", true));

        Result<?> result = controller.testConnection(ipo);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("checkName 检查名称可用性（R.success）")
    void checkNameSuccess() {
        when(deploymentService.checkName("my-deploy")).thenReturn(Map.of("available", true));

        Result<?> result = controller.checkName("my-deploy");

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("create 创建部署（R.success）")
    void createSuccess() {
        Map<String, Object> body = Map.of("name", "d1");
        when(deploymentService.createDeployment(body)).thenReturn(Map.of("id", "d1"));

        Result<?> result = controller.create(body);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("updateName 名称为空抛 ResultException（code=600）")
    void updateNameEmptyThrows() {
        DeploymentIPO.UpdateName ipo = new DeploymentIPO.UpdateName();
        ipo.setName("   ");

        ResultException ex = assertThrows(ResultException.class, () -> controller.updateName("d1", ipo));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("updateName 成功（R.success）")
    void updateNameSuccess() {
        DeploymentIPO.UpdateName ipo = new DeploymentIPO.UpdateName();
        ipo.setName("new-name");
        when(deploymentService.updateDeploymentName("d1", "new-name")).thenReturn(Map.of("ok", true));

        Result<?> result = controller.updateName("d1", ipo);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("delete 删除部署（R.success）")
    void deleteSuccess() {
        when(deploymentService.deleteDeployment("d1")).thenReturn(Map.of("deleted", true));

        Result<?> result = controller.delete("d1");

        assertTrue(result.isFlag());
        verify(deploymentService).deleteDeployment("d1");
    }
}
