package yaoshu.token.service.oauthruntime;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserOAuthBindingMapper;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.VerificationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OAuthRuntimeService.unbindEmail 白盒测试，聚焦解绑链路分支。
 * <p>
 * Mock 链路：StpUtil（登录态）+ VerificationService（验证码）+ UserMapper（数据访问）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthRuntimeService.unbindEmail — 邮箱解绑分支 ResultException / R.success")
class OAuthRuntimeServiceTest {

    @Mock
    private OAuthProviderRegistry providerRegistry;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserOAuthBindingMapper bindingMapper;

    @Mock
    private UserService userService;

    @Mock
    private OptionService optionService;

    @InjectMocks
    private OAuthRuntimeService oauthRuntimeService;

    @Mock
    private HttpServletRequest request;

    @BeforeAll
    static void initMybatisPlusLambda() {
        // 初始化 MyBatis-Plus Lambda 缓存，使 LambdaUpdateWrapper 能解析 User::getId
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @Test
    @DisplayName("未登录抛 ResultException")
    void unbindEmailNotLoggedInThrows() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::isLogin).thenReturn(false);

            assertThrows(ResultException.class, () ->
                    oauthRuntimeService.unbindEmail("123456", request));
        }
    }

    @Test
    @DisplayName("邮箱未绑定（email 为空）抛 ResultException")
    void unbindEmailNotBoundThrows() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::isLogin).thenReturn(true);
            stpMock.when(StpUtil::getLoginIdAsInt).thenReturn(1);

            User user = new User();
            user.setId(1);
            user.setEmail("");
            when(userMapper.selectOne(any())).thenReturn(user);

            assertThrows(ResultException.class, () ->
                    oauthRuntimeService.unbindEmail("123456", request));
        }
    }

    @Test
    @DisplayName("验证码错误抛 ResultException")
    void unbindEmailWrongCodeThrows() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
             MockedStatic<VerificationService> verMock = mockStatic(VerificationService.class)) {
            stpMock.when(StpUtil::isLogin).thenReturn(true);
            stpMock.when(StpUtil::getLoginIdAsInt).thenReturn(1);

            User user = new User();
            user.setId(1);
            user.setEmail("test@example.com");
            when(userMapper.selectOne(any())).thenReturn(user);

            verMock.when(() -> VerificationService.verifyCode(
                            "test@example.com", "wrong", VerificationService.EMAIL_VERIFICATION_PURPOSE))
                    .thenReturn(false);

            assertThrows(ResultException.class, () ->
                    oauthRuntimeService.unbindEmail("wrong", request));
        }
    }

    @Test
    @DisplayName("验证码正确解绑成功返回 R.success")
    void unbindEmailSuccess() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
             MockedStatic<VerificationService> verMock = mockStatic(VerificationService.class)) {
            stpMock.when(StpUtil::isLogin).thenReturn(true);
            stpMock.when(StpUtil::getLoginIdAsInt).thenReturn(1);

            User user = new User();
            user.setId(1);
            user.setEmail("test@example.com");
            when(userMapper.selectOne(any())).thenReturn(user);

            verMock.when(() -> VerificationService.verifyCode(
                            "test@example.com", "123456", VerificationService.EMAIL_VERIFICATION_PURPOSE))
                    .thenReturn(true);

            when(userMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            Result<?> result = oauthRuntimeService.unbindEmail("123456", request);

            assertNotNull(result);
            assertTrue(result.isFlag());
        }
    }
}
