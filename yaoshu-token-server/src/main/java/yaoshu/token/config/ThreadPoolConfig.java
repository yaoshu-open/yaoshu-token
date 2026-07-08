package yaoshu.token.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import yaoshu.token.service.SysLogService;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置  */
@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /** Relay 转发专用线程池 Bean 名称 */
    public static final String RELAY_EXECUTOR = "relayTaskExecutor";

    /**
     * Relay 转发专用线程池
     * <p>
     * 核心线程数 8，最大 Integer.MAX_VALUE（与 Go 的 math.MaxInt32 等效），
     * 队列容量 0（SynchronousQueue 语义，拒绝策略 CallerRunsPolicy）。
     */
    @Bean(name = RELAY_EXECUTOR)
    public ThreadPoolTaskExecutor relayTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("relay-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
