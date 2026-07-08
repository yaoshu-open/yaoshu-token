package yaoshu.token.controller;

import ai.yue.library.base.view.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SystemService;
import yaoshu.token.service.SetupService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SystemController 白盒测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemController — 系统公开端点 R.success 分支")
class SystemControllerTest {

    @Mock
    private SystemService systemService;

    @Mock
    private OptionService optionService;

    @Mock
    private SetupService setupService;

    @InjectMocks
    private SystemController controller;

    @Test
    @DisplayName("getStatus 返回系统状态聚合（R.success）")
    void getStatusSuccess() {
        when(systemService.getStartTime()).thenReturn(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(systemService.getUserAgreement()).thenReturn("条款内容");
        when(systemService.getPrivacyPolicy()).thenReturn("隐私政策");
        when(setupService.isInitialized()).thenReturn(true);

        Result<?> result = controller.getStatus();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getUserAgreement 返回用户协议（R.success）")
    void getUserAgreementSuccess() {
        when(systemService.getUserAgreement()).thenReturn("协议文本");

        Result<?> result = controller.getUserAgreement();

        assertTrue(result.isFlag());
        assertEquals("协议文本", result.getData());
    }

    @Test
    @DisplayName("getPrivacyPolicy 返回隐私政策（R.success）")
    void getPrivacyPolicySuccess() {
        when(systemService.getPrivacyPolicy()).thenReturn("政策文本");

        Result<?> result = controller.getPrivacyPolicy();

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getNotice 返回公告（R.success）")
    void getNoticeSuccess() {
        when(systemService.getOptionValue("Notice")).thenReturn("公告内容");

        Result<?> result = controller.getNotice();

        assertTrue(result.isFlag());
        assertEquals("公告内容", result.getData());
    }

    @Test
    @DisplayName("getAbout 返回关于信息（R.success）")
    void getAboutSuccess() {
        when(systemService.getOptionValue("About")).thenReturn("关于");

        Result<?> result = controller.getAbout();

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("getHomePageContent 返回首页内容（R.success）")
    void getHomePageContentSuccess() {
        when(systemService.getOptionValue("HomePageContent")).thenReturn("首页");

        Result<?> result = controller.getHomePageContent();

        assertTrue(result.isFlag());
    }
}
