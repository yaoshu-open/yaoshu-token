package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.config.ratio.ExposeRatioConfig;
import yaoshu.token.service.PricingService;

/**
 * 倍率配置控制器  * <p>
 * 返回前端可消费的倍率数据：model_ratio / completion_ratio / cache_ratio / create_cache_ratio / model_price，
 * 数据源由 {@link PricingService#getRatioExposureSnapshot()} 聚合 PricingSnapshot 中每个模型的真实倍率/价格字段，
 * 与 Go {@code ratio_setting.GetExposedData()} 行为完全一致。
 * <p>
 * 认证：无 + CriticalRateLimit（由 RateLimitFilter 处理）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RatioConfigController {

    private final PricingService pricingService;

    /**
     * 获取倍率配置      */
    @GetMapping("/ratio_config")
    public Result<?> getConfig() {
        // 检查是否启用暴露倍率（Go: IsExposeRatioEnabled）
        Double enabled = ExposeRatioConfig.getExposeRatio("enabled");
        if (enabled == null || enabled <= 0) {
            throw new ResultException(R.errorPrompt("倍率配置接口未启用"));
        }
        return R.success(pricingService.getRatioExposureSnapshot());
    }
}
