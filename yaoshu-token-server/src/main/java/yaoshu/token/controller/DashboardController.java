package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.vo.DashboardVO.SubscriptionResponse;
import yaoshu.token.pojo.vo.DashboardVO.UsageResponse;
import yaoshu.token.service.DashboardService;

/**
 * Dashboard Billing 控制器  * <p>
 * 认证：TokenAuth（由 TokenAuthFilter 设置 request attribute "token_id" 和 "id"）
 * 路由组：old_api — 使用 OpenAI 兼容格式（非 Result 格式）
 */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取用户订阅摘要      */
    @GetMapping({"/dashboard/billing/subscription", "/v1/dashboard/billing/subscription"})
    public SubscriptionResponse getSubscription(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        Integer tokenId = (Integer) request.getAttribute("token_id");
        return dashboardService.getSubscription(userId, tokenId);
    }

    /**
     * 获取用户用量信息      */
    @GetMapping({"/dashboard/billing/usage", "/v1/dashboard/billing/usage"})
    public UsageResponse getUsage(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        Integer tokenId = (Integer) request.getAttribute("token_id");
        return dashboardService.getUsage(userId, tokenId);
    }
}
