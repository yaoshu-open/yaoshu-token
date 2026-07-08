package yaoshu.token.controller;

import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.service.RankingsService;
import yaoshu.token.service.RankingsService.RankingsSnapshot;

/**
 * 排行榜控制器  * <p>
 * 认证：HeaderNavModuleAuth("rankings")
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingsController {

    private final RankingsService rankingsService;

    /**
     * 获取排行榜快照      *
     * @param period 统计周期（today/week/month/year/all），默认 week
     */
    @GetMapping
    public Result<?> getRankings(@RequestParam(defaultValue = "week") String period) {
        RankingsSnapshot snapshot = rankingsService.getRankingsSnapshot(period);
        return R.success(snapshot);
    }
}
