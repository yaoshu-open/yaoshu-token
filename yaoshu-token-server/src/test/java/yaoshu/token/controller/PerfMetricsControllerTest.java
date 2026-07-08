package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.service.PerfMetricsService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PerfMetricsController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerfMetricsController — 性能指标 R.success / 错误提示分支")
class PerfMetricsControllerTest {

    @Mock
    private PerfMetricsService perfMetricsService;

    @InjectMocks
    private PerfMetricsController controller;

    @Test
    @DisplayName("getSummary 返回全局汇总（R.success，mockStatic GroupRatioConfig）")
    void getSummarySuccess() {
        try (MockedStatic<GroupRatioConfig> mocked = mockStatic(GroupRatioConfig.class)) {
            mocked.when(GroupRatioConfig::getGroupRatioCopy)
                    .thenReturn(Map.of("default", 1.0));
            when(perfMetricsService.querySummaryAll(anyInt(), anyList()))
                    .thenReturn(mock(PerfMetricsService.SummaryAllResult.class));

            Result<?> result = controller.getSummary(24);

            assertTrue(result.isFlag());
            assertEquals(200, result.getCode());
        }
    }

    @Test
    @DisplayName("getAll model 为空时返回错误提示（flag=false，非异常）")
    void getAllModelEmptyReturnsErrorPrompt() {
        Result<?> result = controller.getAll(null, null, 24);

        assertFalse(result.isFlag());
        assertEquals(600, result.getCode());
    }

    @Test
    @DisplayName("getAll model 为空字符串时返回错误提示（flag=false）")
    void getAllModelBlankReturnsErrorPrompt() {
        Result<?> result = controller.getAll("", null, 24);

        assertFalse(result.isFlag());
    }

    @Test
    @DisplayName("getAll 指定 model 时返回单模型指标（R.success）")
    void getAllWithModelSuccess() {
        when(perfMetricsService.query(eq("gpt-4o"), eq(""), anyInt()))
                .thenReturn(mock(PerfMetricsService.PerfMetricsResult.class));

        Result<?> result = controller.getAll("gpt-4o", null, 24);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getAll 指定 model+group 时透传 group（R.success）")
    void getAllWithGroupSuccess() {
        when(perfMetricsService.query(eq("gpt-4o"), eq("vip"), anyInt()))
                .thenReturn(mock(PerfMetricsService.PerfMetricsResult.class));

        Result<?> result = controller.getAll("gpt-4o", "vip", 12);

        assertTrue(result.isFlag());
    }
}
