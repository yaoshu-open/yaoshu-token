package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ai.yue.library.base.view.Result;
import yaoshu.token.service.oauthruntime.OAuthRuntimeService;

/**
 * OAuth 社交登录控制器（Discord/GitHub/LinuxDO/OIDC）
 * <p>
 * 合并 Go controller/discord.go + github.go + linuxdo.go + oidc.go + wechat.go + telegram.go
 * 认证：无 + CriticalRateLimit
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class SocialLoginController {

    private final OAuthRuntimeService oauthRuntimeService;

    // ======================== Discord ========================
    @GetMapping("/discord")
    public Result<?> discord(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("discord", request); }

    @GetMapping("/discord/callback")
    public Result<?> discordCallback(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("discord", request); }

    // ======================== GitHub ========================
    @GetMapping("/github")
    public Result<?> github(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("github", request); }

    @GetMapping("/github/callback")
    public Result<?> githubCallback(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("github", request); }

    // ======================== LinuxDO ========================
    @GetMapping("/linuxdo")
    public Result<?> linuxDo(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("linuxdo", request); }

    @GetMapping("/linuxdo/callback")
    public Result<?> linuxDoCallback(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("linuxdo", request); }

    // ======================== OIDC ========================
    @GetMapping("/oidc")
    public Result<?> oidc(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("oidc", request); }

    @GetMapping("/oidc/callback")
    public Result<?> oidcCallback(HttpServletRequest request) { return oauthRuntimeService.handleOAuth("oidc", request); }
}
