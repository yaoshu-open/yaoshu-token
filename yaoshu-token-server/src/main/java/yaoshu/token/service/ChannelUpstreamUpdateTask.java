package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 渠道上游模型变更检测定时任务  * <p>
 * 周期性检测所有开启了 upstreamModelUpdateCheckEnabled 的渠道的上游模型变更。
 * 检测到变更时通过 {@link ChannelManagementService#detectAllUpstreamModelUpdates()} 触发通知（B3）。
 * <p>
 * 使用 AtomicBoolean 防止任务重入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelUpstreamUpdateTask {

    private final ChannelManagementService channelManagementService;

    /** 防重入标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 定时检测上游模型变更      * <p>
     * 每 10 分钟执行一次（fixedDelay = 上次执行结束后等待 10 分钟）
     */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void detectUpstreamModelUpdates() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            channelManagementService.detectAllUpstreamModelUpdates();
        } catch (Exception e) {
            log.warn("channel upstream model update task failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
