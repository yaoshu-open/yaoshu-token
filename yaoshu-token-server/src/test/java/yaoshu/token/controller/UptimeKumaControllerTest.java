package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.service.OptionService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * UptimeKumaController 白盒测试。
 * <p>
 * 静态 final HttpClient 字段无法用纯 Mockito 注入，聚焦测试空配置分支
 * （optionService 返回 null/空时直接返回空列表，不触发 HTTP 抓取）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UptimeKumaController — 空配置 R.success 分支")
class UptimeKumaControllerTest {

    @Mock
    private OptionService optionService;

    @InjectMocks
    private UptimeKumaController controller;

    @Test
    @DisplayName("getStatus 配置为 null 时返回空列表（R.success）")
    void getStatusNullConfigSuccess() {
        when(optionService.getValue("console_setting.uptime_kuma_groups")).thenReturn(null);

        Result<?> result = controller.getStatus();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertTrue(((List<?>) result.getData()).isEmpty());
    }

    @Test
    @DisplayName("getStatus 配置为空字符串时返回空列表（R.success）")
    void getStatusEmptyConfigSuccess() {
        when(optionService.getValue("console_setting.uptime_kuma_groups")).thenReturn("");

        Result<?> result = controller.getStatus();

        assertTrue(result.isFlag());
        assertTrue(((List<?>) result.getData()).isEmpty());
    }
}
