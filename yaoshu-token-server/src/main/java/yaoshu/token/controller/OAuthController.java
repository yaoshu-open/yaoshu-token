package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.ipo.OAuthIPO;
import yaoshu.token.service.OAuthManagementService;
import yaoshu.token.service.oauthruntime.OAuthRuntimeService;

import java.util.Map;

/**
 * OAuth 控制器  * <p>
 * 认证：混合（大部分无认证，bindings/unbind 用 UserAuth）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthManagementService oauthManagementService;
    private final OAuthRuntimeService oauthRuntimeService;

    // ======================== 通用 OAuth ========================

    @GetMapping("/oauth/state")
    public Result<?> generateState(HttpServletRequest request,
                                             @RequestParam(value = "aff", required = false) String aff) {
        String state = cn.hutool.v7.core.util.RandomUtil.randomLettersAndNumbers(12);
        jakarta.servlet.http.HttpSession session = request.getSession(true);
        // aff 邀请码非空时一并暂存，供回调时绑定邀请关系
        if (aff != null && !aff.isEmpty()) {
            session.setAttribute("aff", aff);
        }
        session.setAttribute("oauth_state", state);

        return R.success(state);
    }

    @PostMapping("/oauth/email/bind")
    public Result<?> emailBind(@Valid @RequestBody OAuthIPO.EmailBind ipo, HttpServletRequest request) {
        return oauthRuntimeService.bindEmail(
                ipo.getEmail(),
                ipo.getCode(),
                request
        );
    }

    @PostMapping("/oauth/email/unbind")
    public Result<?> emailUnbind(@Valid @RequestBody OAuthIPO.EmailUnbind ipo, HttpServletRequest request) {
        return oauthRuntimeService.unbindEmail(ipo.getCode(), request);
    }

    @GetMapping("/oauth/{provider}")
    public Result<?> handleOAuth(@PathVariable String provider, HttpServletRequest request) {
        return oauthRuntimeService.handleOAuth(provider, request);
    }

    @GetMapping("/user/oauth/bindings")
    public Result<?> getBindings(HttpServletRequest request) {
        Integer userId = getUserId(request);
        return R.success(oauthManagementService.listUserBindings(userId));
    }

    @DeleteMapping("/user/oauth/bindings/{provider_id:\\d+}")
    public Result<?> unbind(HttpServletRequest request, @PathVariable("provider_id") String providerId) {
        try {
            int parsedProviderId = Integer.parseInt(providerId);
            oauthManagementService.unbindUserOAuth(getUserId(request), parsedProviderId);
            return R.success();
        } catch (NumberFormatException e) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("oauth.invalid_provider_id")));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    // ======================== 微信 ========================

    @GetMapping("/oauth/wechat")
    public Result<?> wechatAuth(@RequestParam(value = "code", required = false) String code,
                                          HttpServletRequest request) {
        return oauthRuntimeService.handleWeChatAuth(code, request);
    }

    @RequestMapping(value = "/oauth/wechat/bind", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<?> wechatBind(HttpServletRequest request,
                                          @RequestBody(required = false) OAuthIPO.WechatBind ipo,
                                          @RequestParam(value = "code", required = false) String code) {
        String actualCode = code;
        if (actualCode == null && ipo != null) {
            actualCode = ipo.getCode();
        }
        return oauthRuntimeService.bindWeChat(actualCode, request);
    }

    // ======================== Telegram ========================

    @GetMapping("/oauth/telegram/login")
    public Result<?> telegramLogin(HttpServletRequest request) {
        return oauthRuntimeService.handleTelegramLogin(request);
    }

    @GetMapping("/oauth/telegram/bind")
    public Result<?> telegramBind(HttpServletRequest request, HttpServletResponse response) {
        try {
            response.sendRedirect(oauthRuntimeService.bindTelegram(request));
            return R.success();
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private Integer getUserId(HttpServletRequest request) {
        Object id = request.getAttribute("id");
        if (id instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(id));
    }
}
