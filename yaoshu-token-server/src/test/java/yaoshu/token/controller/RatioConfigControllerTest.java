package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.config.ratio.ExposeRatioConfig;
import yaoshu.token.service.PricingService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RatioConfigController 白盒测试，验证静态 ExposeRatioConfig 的 guard 分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RatioConfigController — 倍率配置 R.success / ResultException 分支")
class RatioConfigControllerTest {

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private RatioConfigController controller;

    @Test
    @DisplayName("getConfig 倍率暴露启用时返回快照（R.success）")
    void getConfigEnabledSuccess() {
        Map<String, Map<String, Double>> snapshot = Map.of("model_ratio", Map.of("gpt-4o", 1.0));
        try (MockedStatic<ExposeRatioConfig> mocked = mockStatic(ExposeRatioConfig.class)) {
            mocked.when(() -> ExposeRatioConfig.getExposeRatio("enabled")).thenReturn(1.0);
            when(pricingService.getRatioExposureSnapshot()).thenReturn(snapshot);

            Result<?> result = controller.getConfig();

            assertTrue(result.isFlag());
            assertEquals(200, result.getCode());
            assertEquals(snapshot, result.getData());
        }
    }

    @Test
    @DisplayName("getConfig 倍率暴露未启用（enabled=null）抛 ResultException（code=600）")
    void getConfigDisabledNullThrows() {
        try (MockedStatic<ExposeRatioConfig> mocked = mockStatic(ExposeRatioConfig.class)) {
            mocked.when(() -> ExposeRatioConfig.getExposeRatio("enabled")).thenReturn(null);

            ResultException ex = assertThrows(ResultException.class, () -> controller.getConfig());
            assertEquals(600, ex.getResult().getCode());
            assertFalse(ex.getResult().isFlag());
        }
    }

    @Test
    @DisplayName("getConfig 倍率暴露未启用（enabled<=0）抛 ResultException（code=600）")
    void getConfigDisabledZeroThrows() {
        try (MockedStatic<ExposeRatioConfig> mocked = mockStatic(ExposeRatioConfig.class)) {
            mocked.when(() -> ExposeRatioConfig.getExposeRatio("enabled")).thenReturn(0.0);

            ResultException ex = assertThrows(ResultException.class, () -> controller.getConfig());
            assertEquals(600, ex.getResult().getCode());
        }
    }
}
