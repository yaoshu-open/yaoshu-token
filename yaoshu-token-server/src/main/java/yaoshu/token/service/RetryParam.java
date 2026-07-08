package yaoshu.token.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;

/**
 * 渠道重试参数  * <p>
 * 用于渠道选择的重试计数器。resetNextTry 机制用于 auto 分组跨组切换时
 * 阻止 IncreaseRetry 递增（当前请求仍需走当前分组，下次重试才走下一组）。
 */
@Data
public class RetryParam {
    private HttpServletRequest request;
    private String tokenGroup;
    private String modelName;
    private Integer retry;
    private boolean resetNextTry;

    public RetryParam() {
        this.retry = 0;
    }

    public RetryParam(HttpServletRequest request, String tokenGroup, String modelName) {
        this.request = request;
        this.tokenGroup = tokenGroup;
        this.modelName = modelName;
        this.retry = 0;
    }

    /**
     * 获取当前重试次数      */
    public int getRetry() {
        return retry != null ? retry : 0;
    }

    /**
     * 设置重试次数      */
    public void setRetry(int retry) {
        this.retry = retry;
    }

    /**
     * 递增重试次数      * <p>
     * 若 resetNextTry 为 true（跨组切换标记），本次跳过递增并重置标记。
     */
    public void increaseRetry() {
        if (resetNextTry) {
            resetNextTry = false;
            return;
        }
        if (retry == null) {
            retry = 0;
        }
        retry++;
    }

    /**
     * 标记下次重试不递增      */
    public void resetRetryNextTry() {
        this.resetNextTry = true;
    }
}
