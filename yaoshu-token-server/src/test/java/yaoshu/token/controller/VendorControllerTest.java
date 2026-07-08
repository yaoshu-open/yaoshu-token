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
import yaoshu.token.pojo.entity.Vendor;
import yaoshu.token.pojo.ipo.VendorIPO;
import yaoshu.token.service.VendorService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VendorController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VendorController — 供应商 CRUD R.success / ResultException 分支")
class VendorControllerTest {

    @Mock
    private VendorService vendorService;

    @InjectMocks
    private VendorController controller;

    private HttpServletRequest emptyRequest() {
        return org.mockito.Mockito.mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("getAll 返回供应商分页（R.success）")
    void getAllSuccess() {
        when(vendorService.getAll()).thenReturn(List.of());

        Result<?> result = controller.getAll(emptyRequest());

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("get 不存在的供应商抛 ResultException（code=600）")
    void getNotFoundThrows() {
        when(vendorService.getById(99)).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.get(99));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("get 存在的供应商成功（R.success）")
    void getFoundSuccess() {
        Vendor vendor = new Vendor();
        vendor.setId(1);
        when(vendorService.getById(1)).thenReturn(vendor);

        Result<?> result = controller.get(1);

        assertTrue(result.isFlag());
        assertEquals(vendor, result.getData());
    }

    @Test
    @DisplayName("create 成功（R.success）")
    void createSuccess() {
        VendorIPO.Create ipo = new VendorIPO.Create();
        ipo.setName("openai");
        ipo.setStatus(1);
        Vendor created = new Vendor();
        created.setId(1);
        when(vendorService.create(any(Vendor.class))).thenReturn(created);

        Result<?> result = controller.create(ipo);

        assertTrue(result.isFlag());
        assertEquals(created, result.getData());
    }

    @Test
    @DisplayName("update 缺少 id 抛 ResultException（code=600）")
    void updateMissingIdThrows() {
        VendorIPO.Update ipo = new VendorIPO.Update();

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("update 供应商不存在抛 ResultException（code=600）")
    void updateNotFoundThrows() {
        VendorIPO.Update ipo = new VendorIPO.Update();
        ipo.setId(99);
        when(vendorService.getById(99)).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("update 成功（R.success）")
    void updateSuccess() {
        VendorIPO.Update ipo = new VendorIPO.Update();
        ipo.setId(1);
        ipo.setName("updated");
        Vendor existing = new Vendor();
        existing.setId(1);
        when(vendorService.getById(1)).thenReturn(existing);
        when(vendorService.update(any(Vendor.class))).thenReturn(existing);

        Result<?> result = controller.update(ipo);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("delete 成功（R.success）")
    void deleteSuccess() {
        Result<?> result = controller.delete(1);

        assertTrue(result.isFlag());
        verify(vendorService).delete(1);
    }
}
