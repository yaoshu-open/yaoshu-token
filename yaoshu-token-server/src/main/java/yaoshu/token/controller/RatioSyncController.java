package yaoshu.token.controller;

import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.service.RatioSyncService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 比率同步控制器  * <p>
 * 认证：RootAuth（全部）
 */
@RestController
@SaCheckRole("root")
@RequestMapping("/api/ratio_sync")
@RequiredArgsConstructor
public class RatioSyncController {

    private final RatioSyncService ratioSyncService;

    @GetMapping("/channels")
    public Result<?> getChannels() {
        return R.success(ratioSyncService.getSyncableChannels());
    }

    @PostMapping("/fetch")
    public Result<?> fetchRatios(@RequestBody Map<String, Object> body) {
        return R.success(ratioSyncService.fetch(body == null ? new LinkedHashMap<>() : body));
    }
}
