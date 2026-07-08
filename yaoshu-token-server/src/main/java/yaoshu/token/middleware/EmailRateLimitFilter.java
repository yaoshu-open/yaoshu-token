package yaoshu.token.middleware;

import ai.yue.library.data.redis.client.Redis;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;

import java.io.IOException;
import java.time.Duration;

/**
 * 邮箱验证码频率限制中间件  * <p>
 * 限制邮箱验证码的发送频率，防止短信/邮件轰炸。
 * <p>
 * 策略：30 秒内同一 IP 最多发送 2 次。Redis 模式用 INCR + TTL 实现；
 * Redis 不可用时回退到内存滑动窗口限流。
 */
@Slf4j
public class EmailRateLimitFilter implements Filter {

    /** 限流 mark */
    private static final String MARK = "EV";
    /** 30 秒内最多 2 次*/
    private static final int MAX_REQUESTS = 2;
    private static final int DURATION_SECONDS = 30;

    private final Redis redis;

    /** 内存限流存储：key → 时间戳队列 */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> memoryStore = new java.util.concurrent.ConcurrentHashMap<>();

    public EmailRateLimitFilter(Redis redis) {
        this.redis = redis;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String clientIP = ai.yue.library.web.util.ServletUtils.getClientIP(request);
        String key = "emailVerification:" + MARK + ":" + clientIP;
        String memoryKey = MARK + ":" + clientIP;

        boolean allowed;
        try {
            allowed = redisEmailRateLimit(key);
        } catch (Exception e) {
            log.warn("Redis 限流不可用，回退到内存限流: {}", e.getMessage());
            allowed = memoryEmailRateLimit(memoryKey);
        }

        if (!allowed) {
            // 计算剩余等待时间
            long waitSeconds = getRedisTtlSeconds(key);
            if (waitSeconds <= 0) {
                waitSeconds = DURATION_SECONDS;
            }
            response.setStatus(429);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"success\":false,\"message\":\"发送过于频繁，请等待 %d 秒后再试\"}", waitSeconds));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Redis 邮箱验证码限流      * <p>
     * 用 INCR 原子递增，首次设置 TTL。count <= MAX_REQUESTS 时放行。
     */
    private boolean redisEmailRateLimit(String key) {
        RAtomicLong counter = redis.getRedisson().getAtomicLong(key);
        long count = counter.incrementAndGet();

        // 第一次设置过期时间
        if (count == 1) {
            counter.expire(Duration.ofSeconds(DURATION_SECONDS));
        }

        return count <= MAX_REQUESTS;
    }

    /**
     * 内存限流（Redis 不可用时回退）      */
    private synchronized boolean memoryEmailRateLimit(String key) {
        long now = System.currentTimeMillis() / 1000;
        long[] queue = memoryStore.get(key);

        if (queue == null || queue.length < MAX_REQUESTS) {
            long[] newQueue;
            if (queue == null) {
                newQueue = new long[]{now};
            } else {
                newQueue = new long[queue.length + 1];
                System.arraycopy(queue, 0, newQueue, 0, queue.length);
                newQueue[queue.length] = now;
            }
            memoryStore.put(key, newQueue);
            return true;
        }

        // 已满，检查头部（最旧）时间戳
        if (now - queue[0] >= DURATION_SECONDS) {
            long[] newQueue = new long[queue.length];
            System.arraycopy(queue, 1, newQueue, 0, queue.length - 1);
            newQueue[queue.length - 1] = now;
            memoryStore.put(key, newQueue);
            return true;
        }

        return false;
    }

    /**
     * 获取 Redis key 的剩余 TTL（秒）
     */
    private long getRedisTtlSeconds(String key) {
        try {
            return redis.getRedisson().getAtomicLong(key).remainTimeToLive() / 1000;
        } catch (Exception e) {
            return DURATION_SECONDS;
        }
    }
}
