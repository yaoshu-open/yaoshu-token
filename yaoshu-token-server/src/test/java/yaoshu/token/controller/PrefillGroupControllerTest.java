package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.mapper.PrefillGroupMapper;
import yaoshu.token.pojo.entity.PrefillGroup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PrefillGroupController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrefillGroupController — 预填分组 CRUD R.success / ResultException 分支")
class PrefillGroupControllerTest {

    @Mock
    private PrefillGroupMapper prefillGroupMapper;

    @InjectMocks
    private PrefillGroupController controller;

    @Test
    @DisplayName("getAll 返回分组列表（R.success）")
    void getAllSuccess() {
        when(prefillGroupMapper.selectList(any())).thenReturn(List.of());

        Result<?> result = controller.getAll(null);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getAll 按 type 过滤成功（R.success）")
    void getAllWithType() {
        when(prefillGroupMapper.selectList(any())).thenReturn(List.of());

        Result<?> result = controller.getAll("chat");

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("create name 为空抛 ResultException（code=600）")
    void createNameEmptyThrows() {
        PrefillGroup group = new PrefillGroup();
        group.setName("");

        ResultException ex = assertThrows(ResultException.class, () -> controller.create(group));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("create type 为空抛 ResultException（code=600）")
    void createTypeEmptyThrows() {
        PrefillGroup group = new PrefillGroup();
        group.setName("g1");
        group.setType("");

        ResultException ex = assertThrows(ResultException.class, () -> controller.create(group));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("create 名称重复抛 ResultException（code=600）")
    void createDuplicateNameThrows() {
        PrefillGroup group = new PrefillGroup();
        group.setName("dup");
        group.setType("chat");
        when(prefillGroupMapper.selectCount(any())).thenReturn(1L);

        ResultException ex = assertThrows(ResultException.class, () -> controller.create(group));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("create 成功（R.success，设置时间戳并 insert）")
    void createSuccess() {
        PrefillGroup group = new PrefillGroup();
        group.setName("g1");
        group.setType("chat");
        when(prefillGroupMapper.selectCount(any())).thenReturn(0L);

        Result<?> result = controller.create(group);

        assertTrue(result.isFlag());
        verify(prefillGroupMapper).insert(group);
        assertNotNull(group.getCreatedTime());
    }

    @Test
    @DisplayName("update 缺少 id 抛 ResultException（code=600）")
    void updateMissingIdThrows() {
        PrefillGroup group = new PrefillGroup();

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(group));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("update 名称重复抛 ResultException（code=600）")
    void updateDuplicateNameThrows() {
        PrefillGroup group = new PrefillGroup();
        group.setId(1);
        group.setName("dup");
        when(prefillGroupMapper.selectCount(any())).thenReturn(1L);

        ResultException ex = assertThrows(ResultException.class, () -> controller.update(group));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("update 成功（R.success）")
    void updateSuccess() {
        PrefillGroup group = new PrefillGroup();
        group.setId(1);
        group.setName("g1");
        when(prefillGroupMapper.selectCount(any())).thenReturn(0L);

        Result<?> result = controller.update(group);

        assertTrue(result.isFlag());
        verify(prefillGroupMapper).updateById(group);
    }

    @Test
    @DisplayName("delete 成功（R.success）")
    void deleteSuccess() {
        Result<?> result = controller.delete(7);

        assertTrue(result.isFlag());
        verify(prefillGroupMapper).deleteById(7);
    }
}
