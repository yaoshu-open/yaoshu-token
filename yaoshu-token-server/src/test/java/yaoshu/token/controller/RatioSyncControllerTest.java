package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.service.RatioSyncService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RatioSyncController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RatioSyncController — 比率同步 R.success 分支")
class RatioSyncControllerTest {

    @Mock
    private RatioSyncService ratioSyncService;

    @InjectMocks
    private RatioSyncController controller;

    @Test
    @DisplayName("getChannels 返回可同步渠道列表（R.success）")
    void getChannelsSuccess() {
        List<Map<String, Object>> channels = List.of(Map.of("id", 1, "name", "ch1"));
        when(ratioSyncService.getSyncableChannels()).thenReturn(channels);

        Result<?> result = controller.getChannels();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertEquals(channels, result.getData());
    }

    @Test
    @DisplayName("fetchRatios 传 body 成功（R.success）")
    void fetchRatiosWithBody() {
        Map<String, Object> body = Map.of("channel_id", 1);
        Map<String, Object> data = Map.of("fetched", 5);
        when(ratioSyncService.fetch(body)).thenReturn(data);

        Result<?> result = controller.fetchRatios(body);

        assertTrue(result.isFlag());
        assertEquals(data, result.getData());
    }

    @Test
    @DisplayName("fetchRatios body 为 null 时回退空 map（R.success）")
    void fetchRatiosNullBodyFallback() {
        Map<String, Object> data = Map.of("fetched", 0);
        when(ratioSyncService.fetch(any())).thenReturn(data);

        Result<?> result = controller.fetchRatios(null);

        assertTrue(result.isFlag());
        assertEquals(data, result.getData());
    }
}
