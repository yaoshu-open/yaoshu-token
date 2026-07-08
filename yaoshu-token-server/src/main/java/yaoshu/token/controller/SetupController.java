package yaoshu.token.controller;

import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.ipo.SetupIPO;
import yaoshu.token.service.SetupService;

/**
 * 系统初始化控制器  * <p>
 * 公开接口（AuthFilter 白名单），用于首次部署时的管理员账号初始化。
 * GET 返回初始化状态，POST 创建 root 账号并标记已初始化。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    /**
     * 获取初始化状态      */
    @GetMapping("/setup")
    public Result<?> getSetup() {
        return R.success(setupService.getSetupStatus());
    }

    /**
     * 提交初始化      */
    @PostMapping("/setup")
    public Result<?> postSetup(@Valid @RequestBody SetupIPO ipo) {
        setupService.postSetup(ipo);
        return R.success(I18nUtils.get("setup.success"));
    }
}
