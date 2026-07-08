package yaoshu.token.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard Billing 相关 VO  */
public final class DashboardVO {

    private DashboardVO() {
    }

    /**
     * OpenAI 订阅响应      */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionResponse {
        private String object;
        private Boolean hasPaymentMethod;
        private Double softLimitUsd;
        private Double hardLimitUsd;
        private Double systemHardLimitUsd;
        private Long accessUntil;
    }

    /**
     * OpenAI 用量响应      */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageResponse {
        private String object;
        private Double totalUsage;
    }
}
