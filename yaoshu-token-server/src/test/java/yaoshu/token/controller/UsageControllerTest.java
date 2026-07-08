package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.service.TokenService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * UsageController 白盒测试，验证 token 用量查询的 R.success / ResultException 分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsageController — Token 用量 R.success / ResultException 分支")
class UsageControllerTest {

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UsageController controller;

    private HttpServletRequest requestWithAuth(String header) {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(header);
        return request;
    }

    private Token buildToken() {
        Token token = new Token();
        token.setName("my-token");
        token.setRemainQuota(500L);
        token.setUsedQuota(300L);
        token.setUnlimitedQuota(false);
        token.setModelLimitsEnabled(false);
        token.setExpiredTime(0L);
        return token;
    }

    @Test
    @DisplayName("getTokenUsage Bearer token 查询成功（R.success flag=true）")
    void getTokenUsageBearerSuccess() {
        HttpServletRequest request = requestWithAuth("Bearer sk-abc123");
        Token token = buildToken();
        when(tokenService.getByKey("sk-abc123")).thenReturn(token);

        Result<?> result = controller.getTokenUsage(request);

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("getTokenUsage 无 Authorization 头抛 ResultException（code=600）")
    void getTokenUsageNoAuthHeader() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getTokenUsage(request));
        assertEquals(600, ex.getResult().getCode());
        assertFalse(ex.getResult().isFlag());
    }

    @Test
    @DisplayName("getTokenUsage 空 Authorization 头抛 ResultException（code=600）")
    void getTokenUsageEmptyAuthHeader() {
        HttpServletRequest request = requestWithAuth("");

        ResultException ex = assertThrows(ResultException.class, () -> controller.getTokenUsage(request));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getTokenUsage 无效 token（service 返回 null）抛 ResultException（code=600）")
    void getTokenUsageInvalidToken() {
        HttpServletRequest request = requestWithAuth("Bearer sk-bad");
        when(tokenService.getByKey("sk-bad")).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class, () -> controller.getTokenUsage(request));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("getTokenUsage 裸 token（无 Bearer 前缀）也能解析查询成功")
    void getTokenUsageRawToken() {
        HttpServletRequest request = requestWithAuth("sk-xyz");
        when(tokenService.getByKey("sk-xyz")).thenReturn(buildToken());

        Result<?> result = controller.getTokenUsage(request);

        assertTrue(result.isFlag());
    }
}
