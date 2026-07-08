package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.service.RankingsService;
import yaoshu.token.service.RankingsService.RankingsSnapshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RankingsController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RankingsController — 排行榜 R.success 分支")
class RankingsControllerTest {

    @Mock
    private RankingsService rankingsService;

    @InjectMocks
    private RankingsController controller;

    @Test
    @DisplayName("getRankings 默认 week 周期返回快照（R.success）")
    void getRankingsDefaultPeriod() {
        RankingsSnapshot snapshot = mock(RankingsSnapshot.class);
        when(rankingsService.getRankingsSnapshot("week")).thenReturn(snapshot);

        Result<?> result = controller.getRankings("week");

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertEquals(snapshot, result.getData());
    }

    @Test
    @DisplayName("getRankings all 周期返回快照（R.success）")
    void getRankingsAllPeriod() {
        RankingsSnapshot snapshot = mock(RankingsSnapshot.class);
        when(rankingsService.getRankingsSnapshot("all")).thenReturn(snapshot);

        Result<?> result = controller.getRankings("all");

        assertTrue(result.isFlag());
        assertEquals(snapshot, result.getData());
    }
}
