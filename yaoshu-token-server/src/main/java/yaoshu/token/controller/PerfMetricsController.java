package yaoshu.token.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.service.PerfMetricsService;

/**
 * 性能指标控制器  * <p>
 * 认证：混合（summary/all 用 HeaderNavModulePublicOrUserAuth("pricing")）
 */
@RestController
@RequestMapping("/api/perf-metrics")
@RequiredArgsConstructor
public class PerfMetricsController {

    private final PerfMetricsService perfMetricsService;

    /**
     * 获取性能指标汇总      */
    @GetMapping("/summary")
    public Result<?> getSummary(@RequestParam(defaultValue = "24") int hours) {
        // Go: activeGroups = keys(GroupRatioCopy) + "auto"
        List<String> activeGroups = new java.util.ArrayList<>(GroupRatioConfig.getGroupRatioCopy().keySet());
        activeGroups.add("auto");

        var result = perfMetricsService.querySummaryAll(hours, activeGroups);
        return R.success(result);
    }

    /**
     * 获取单模型性能指标      */
    @GetMapping
    public Result<?> getAll(@RequestParam(required = false) String model,
                             @RequestParam(required = false) String group,
                             @RequestParam(defaultValue = "24") int hours) {
        if (model == null || model.isEmpty()) {
            return R.errorPrompt("model is required");
        }

        var result = perfMetricsService.query(model, group != null ? group : "", hours);
        return R.success(result);
    }
}
