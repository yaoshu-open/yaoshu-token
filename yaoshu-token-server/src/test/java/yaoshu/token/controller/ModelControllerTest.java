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
import yaoshu.token.pojo.entity.ModelMeta;
import yaoshu.token.pojo.ipo.ModelIPO;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.ModelSyncService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ModelController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelController — 模型管理 R.success / ResultException 分支")
class ModelControllerTest {

    @Mock
    private ModelService modelService;

    @Mock
    private ModelSyncService modelSyncService;

    @InjectMocks
    private ModelController controller;

    @Test
    @DisplayName("getAll User 路径返回 channelId2Models（R.success）")
    void getAllUserPathSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/models");
        when(modelService.getChannelId2Models()).thenReturn(Map.of(1, List.of("gpt-4o")));

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getAll Admin 路径 role<2 抛 ResultException（code=600）")
    void getAllAdminPathLowRoleThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/models/");
        when(request.getAttribute("role")).thenReturn(1);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getAll(request));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getAll Admin 路径 role>=2 返回分页+vendor_counts（R.success）")
    void getAllAdminPathSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/models/");
        when(request.getAttribute("role")).thenReturn(10);
        when(modelService.getAll()).thenReturn(List.of());
        when(modelService.getVendorModelCounts()).thenReturn(Map.of());

        Result<?> result = controller.getAll(request);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("search 返回模型分页（R.success）")
    void searchSuccess() {
        when(modelService.search("gpt")).thenReturn(List.of());

        Result<?> result = controller.search("gpt");

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("get 不存在的模型抛 ResultException（code=600）")
    void getNotFoundThrows() {
        when(modelService.getById(99)).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.get(99));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("get 存在的模型成功（R.success）")
    void getFoundSuccess() {
        ModelMeta model = new ModelMeta();
        model.setId(1);
        when(modelService.getById(1)).thenReturn(model);

        Result<?> result = controller.get(1);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("create 名称为空抛 ResultException（code=600）")
    void createNameEmptyThrows() {
        ModelIPO.Create ipo = new ModelIPO.Create();

        ResultException ex = assertThrows(ResultException.class, () -> controller.create(ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("create 成功（R.success）")
    void createSuccess() {
        ModelIPO.Create ipo = new ModelIPO.Create();
        ipo.setModelName("gpt-4o");

        Result<?> result = controller.create(ipo);

        assertTrue(result.isFlag());
        verify(modelService).create(any(ModelMeta.class));
    }

    @Test
    @DisplayName("update 缺少 id 抛 ResultException（code=600）")
    void updateMissingIdThrows() {
        ModelIPO.Update ipo = new ModelIPO.Update();

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("update 模型不存在抛 ResultException（code=600）")
    void updateNotFoundThrows() {
        ModelIPO.Update ipo = new ModelIPO.Update();
        ipo.setId(99);
        when(modelService.getById(99)).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("delete 删除失败抛 ResultException（code=600）")
    void deleteFailedThrows() {
        when(modelService.delete(1)).thenReturn(false);

        ResultException ex = assertThrows(ResultException.class, () -> controller.delete(1));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("delete 删除成功（R.success）")
    void deleteSuccess() {
        when(modelService.delete(1)).thenReturn(true);

        Result<?> result = controller.delete(1);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("syncUpstreamPreview 返回预览（R.success）")
    void syncUpstreamPreviewSuccess() {
        when(modelSyncService.preview("zh")).thenReturn(Map.of("count", 5));

        Result<?> result = controller.syncUpstreamPreview("zh");

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getMissing 返回缺失模型（R.success）")
    void getMissingSuccess() {
        when(modelSyncService.getMissingModels()).thenReturn(List.of("model-x"));

        Result<?> result = controller.getMissing();

        assertTrue(result.isFlag());
    }
}
