package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.config.SystemSettingConfig;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.service.passkey.PasskeyService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PasskeyController 白盒测试，聚焦 guard 分支（mockStatic StpUtil + PasskeySetting）。
 * <p>
 * WebAuthn 核心 RP/finish 流程依赖外部库不可控，白盒聚焦"未启用/未登录"等 guard 分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasskeyController — Passkey guard 分支 R.success / ResultException")
class PasskeyControllerTest {

    @Mock
    private PasskeyService passkeyService;

    @Mock
    private PasskeyMapper passkeyMapper;

    @Mock
    private TwoFaMapper twoFaMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private PasskeyController controller;

    /** 构造禁用的 PasskeySetting mock（stub 在调用前完成，避免嵌套 when） */
    private SystemSettingConfig.PasskeySetting disabledSetting() {
        SystemSettingConfig.PasskeySetting setting = mock(SystemSettingConfig.PasskeySetting.class);
        when(setting.isEnabled()).thenReturn(false);
        return setting;
    }

    @Test
    @DisplayName("loginBegin Passkey 未启用抛 ResultException（code=600）")
    void loginBeginDisabledThrows() {
        SystemSettingConfig.PasskeySetting setting = disabledSetting();
        try (MockedStatic<SystemSettingConfig.PasskeySetting> mocked =
                     mockStatic(SystemSettingConfig.PasskeySetting.class)) {
            mocked.when(SystemSettingConfig.PasskeySetting::current).thenReturn(setting);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.loginBegin(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
            assertFalse(ex.getResult().isFlag());
        }
    }

    @Test
    @DisplayName("loginFinish Passkey 未启用抛 ResultException（code=600）")
    void loginFinishDisabledThrows() {
        SystemSettingConfig.PasskeySetting setting = disabledSetting();
        try (MockedStatic<SystemSettingConfig.PasskeySetting> mocked =
                     mockStatic(SystemSettingConfig.PasskeySetting.class)) {
            mocked.when(SystemSettingConfig.PasskeySetting::current).thenReturn(setting);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.loginFinish(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
        }
    }

    @Test
    @DisplayName("status 未登录抛 ResultException（code=600）")
    void statusNotLoggedInThrows() {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.status(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
        }
    }

    @Test
    @DisplayName("status 已登录但无凭证返回 enabled=false（R.success）")
    void statusEnabledNoCredential() {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsInt).thenReturn(5);
            when(passkeyMapper.selectByUserId(5)).thenReturn(null);

            Result<?> result = controller.status(mock(HttpServletRequest.class));

            assertTrue(result.isFlag());
            assertNotNull(result.getData());
        }
    }

    @Test
    @DisplayName("registerBegin 未登录抛 ResultException（code=600）")
    void registerBeginNotLoggedInThrows() {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.registerBegin(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
        }
    }

    @Test
    @DisplayName("registerBegin 已登录但 Passkey 未启用抛 ResultException（code=600）")
    void registerBeginDisabledThrows() {
        SystemSettingConfig.PasskeySetting setting = disabledSetting();
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
             MockedStatic<SystemSettingConfig.PasskeySetting> psMock =
                     mockStatic(SystemSettingConfig.PasskeySetting.class)) {
            stpMock.when(StpUtil::isLogin).thenReturn(true);
            stpMock.when(StpUtil::getLoginIdAsInt).thenReturn(5);
            psMock.when(SystemSettingConfig.PasskeySetting::current).thenReturn(setting);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.registerBegin(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
        }
    }

    @Test
    @DisplayName("delete 未登录抛 ResultException（code=600）")
    void deleteNotLoggedInThrows() {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            ResultException ex = assertThrows(ResultException.class,
                    () -> controller.delete(mock(HttpServletRequest.class)));
            assertEquals(600, ex.getResult().getCode());
        }
    }
}
