package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.service.OAuthManagementService;

/**
 * 自定义 OAuth Provider 控制器  * <p>
 * 认证：AdminAuth（与 ChannelController 同级，系统管理功能）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/custom-oauth-provider")
@RequiredArgsConstructor
public class CustomOAuthController {

    private final OAuthManagementService oauthManagementService;

    @PostMapping("/discovery")
    public Result<?> fetchDiscovery(@RequestBody OAuthManagementService.DiscoveryCommand body) {
        try {
            return R.success(oauthManagementService.fetchDiscovery(body));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @GetMapping("/")
    public Result<?> getAll() {
        return R.success(oauthManagementService.listProviders());
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id) {
        try {
            return R.success(oauthManagementService.getProvider(id));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PostMapping("/")
    public Result<?> create(@Valid @RequestBody OAuthManagementService.CreateProviderCommand body) {
        try {
            return R.success(oauthManagementService.createProvider(body));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable int id,
                            @Valid @RequestBody OAuthManagementService.UpdateProviderCommand body) {
        try {
            return R.success(oauthManagementService.updateProvider(id, body));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        try {
            oauthManagementService.deleteProvider(id);
            return R.success("删除成功");
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }
}
