package yaoshu.token.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.mapper.PerfMetricMapper;
import yaoshu.token.mapper.PerfMetricMapper.PerfMetricSummary;
import yaoshu.token.pojo.entity.PerfMetric;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerfMetricsService — 性能指标查询")
class PerfMetricsServiceTest {

    @Mock
    private PerfMetricMapper perfMetricMapper;

    @InjectMocks
    private PerfMetricsService service;

    // ======================== query() — 单模型详情 ========================

    @Test
    @DisplayName("查询单模型性能指标返回正确结构")
    void querySingleModel() {
        List<PerfMetric> rows = new ArrayList<>();
        PerfMetric m = new PerfMetric();
        m.setModelName("gpt-4o");
        m.setGroup("default");
        m.setBucketTs(System.currentTimeMillis() / 1000);
        m.setRequestCount(100L);
        m.setSuccessCount(95L);
        m.setTotalLatencyMs(50_000L);
        m.setTtftSumMs(5_000L);
        m.setTtftCount(100L);
        m.setOutputTokens(10_000L);
        rows.add(m);

        when(perfMetricMapper.getPerfMetrics(eq("gpt-4o"), eq(""), anyLong(), anyLong()))
                .thenReturn(rows);

        PerfMetricsService.PerfMetricsResult result = service.query("gpt-4o", "", 24);

        assertNotNull(result);
        assertEquals("gpt-4o", result.modelName());
        assertNotNull(result.groups());
        assertFalse(result.groups().isEmpty());

        PerfMetricsService.GroupResult gr = result.groups().get(0);
        assertEquals("default", gr.group());
        assertEquals(50, gr.avgTtftMs());           // 5000/100
        assertEquals(500, gr.avgLatencyMs());        // 50000/100
        assertEquals(95.0, gr.successRate());        // 95/100*100
        assertTrue(gr.avgTps() > 0);
        assertEquals(1, gr.series().size());
    }

    @Test
    @DisplayName("查询不存在的模型返回空分组")
    void queryNonExistentModel() {
        when(perfMetricMapper.getPerfMetrics(eq("unknown-model"), eq(""), anyLong(), anyLong()))
                .thenReturn(List.of());

        PerfMetricsService.PerfMetricsResult result = service.query("unknown-model", "", 24);

        assertNotNull(result);
        assertEquals("unknown-model", result.modelName());
        assertEquals(0, result.groups().size());
    }

    @Test
    @DisplayName("hours 边界值处理：0→24, >720→720")
    void queryHoursBounds() {
        when(perfMetricMapper.getPerfMetrics(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of());

        // hours=0 → 24
        PerfMetricsService.PerfMetricsResult r1 = service.query("gpt-4o", "", 0);
        assertNotNull(r1);

        // hours=1000 → 720（30天）
        PerfMetricsService.PerfMetricsResult r2 = service.query("gpt-4o", "", 1000);
        assertNotNull(r2);
    }

    // ======================== querySummaryAll() — 全局汇总 ========================

    @Test
    @DisplayName("查询全局汇总返回正确结构")
    void querySummaryAll() {
        List<PerfMetricSummary> rows = new ArrayList<>();
        PerfMetricSummary s = new PerfMetricSummary();
        s.setModelName("gpt-4o");
        s.setRequestCount(500L);
        s.setSuccessCount(480L);
        s.setTotalLatencyMs(250_000L);
        s.setGenerationMs(50_000L);
        s.setOutputTokens(100_000L);
        rows.add(s);

        when(perfMetricMapper.getPerfMetricsSummaryAll(anyLong(), anyLong(), anyList()))
                .thenReturn(rows);

        var result = service.querySummaryAll(24, List.of("default", "auto"));

        assertNotNull(result);
        assertEquals(1, result.models().size());

        PerfMetricsService.ModelSummary ms = result.models().get(0);
        assertEquals("gpt-4o", ms.modelName());
        assertEquals(500, ms.avgLatencyMs());    // 250000/500
        assertEquals(96.0, ms.successRate());     // 480/500*100
        assertEquals(500, ms.requestCount());
        assertTrue(ms.avgTps() > 0);              // 100000/(50000/1000) = 2000
    }

    @Test
    @DisplayName("查询空数据返回空列表")
    void querySummaryAllEmpty() {
        when(perfMetricMapper.getPerfMetricsSummaryAll(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of());

        var result = service.querySummaryAll(24, List.of());

        assertNotNull(result);
        assertEquals(0, result.models().size());
    }

    // ======================== groupsKey() ========================

    @Test
    @DisplayName("groupsKey 静态方法：null/空/多分组")
    void groupsKey() {
        assertEquals("_", PerfMetricsService.groupsKey(null));
        assertEquals("_", PerfMetricsService.groupsKey(List.of()));
        assertEquals("a,b,c", PerfMetricsService.groupsKey(List.of("a", "b", "c")));
        assertEquals("a,b", PerfMetricsService.groupsKey(List.of("b", "a"))); // sorted
    }
}
