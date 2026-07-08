package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.service.GroupService;
import yaoshu.token.service.PricingService;
import yaoshu.token.service.UserService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PricingController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PricingController — 定价 R.success 分支")
class PricingControllerTest {

    @Mock
    private PricingService pricingService;

    @Mock
    private UserService userService;

    @InjectMocks
    private PricingController controller;

    @Test
    @DisplayName("resetModelRatio 重置成功（R.success）")
    void resetModelRatioSuccess() {
        Result<?> result = controller.resetModelRatio();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        verify(pricingService).invalidatePricingCache();
    }

    @Test
    @DisplayName("getPricing 未登录用户返回过滤后定价（R.success，mockStatic）")
    void getPricingAnonymousSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(null);
        when(pricingService.getPricing()).thenReturn(List.of());
        when(pricingService.getVendors()).thenReturn(List.of());
        when(pricingService.getSupportedEndpointMap()).thenReturn(Map.of());
        try (MockedStatic<GroupRatioConfig> grcMock = mockStatic(GroupRatioConfig.class);
             MockedStatic<GroupService> gsMock = mockStatic(GroupService.class)) {
            grcMock.when(GroupRatioConfig::getGroupRatioCopy).thenReturn(Map.of("default", 1.0));
            gsMock.when(() -> GroupService.getUserUsableGroups("")).thenReturn(Map.of("default", "默认"));
            gsMock.when(() -> GroupService.getUserAutoGroup("")).thenReturn(List.of());

            Result<?> result = controller.getPricing(request);

            assertTrue(result.isFlag());
            assertNotNull(result.getData());
        }
    }
}
