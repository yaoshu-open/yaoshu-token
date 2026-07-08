package yaoshu.token.middleware;

import ai.yue.library.data.redis.client.Redis;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import yaoshu.token.constant.CommonConstants;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 请求限流过滤器  * <p>
 * 基于 IP 的滑动窗口限流。Redis 模式使用 Redis List（LPUSH/LINDEX/LTRIM）实现
 * 与 Go 原项目完全一致的滑动窗口算法；Redis 不可用时回退到内存限流。
 * <p>
 * Go 限流策略（由 OptionService 管理启用/参数）：
 * <ul>
 * <li>GlobalAPIRateLimit — /api/* 全局 API 限流（mark=GA）</li>
 * <li>GlobalWebRateLimit — 全局 Web 限流（mark=GW）</li>
 * <li>CriticalRateLimit — 关键操作限流（mark=CT）</li>
 * <li>DownloadRateLimit — 下载限流（mark=DW）</li>
 * <li>UploadRateLimit — 上传限流（mark=UP）</li>
 * </ul>
 * 本 Filter 处理 /api/* 路径，使用 GlobalAPIRateLimit 配置。
 */
@Slf4j
public class RateLimitFilter implements Filter {

    /** Go 时间格式：2006-01-02T15:04:05.000Z */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final Redis redis;

    /** 内存限流存储：key → 时间戳队列（[旧 ← 新]） */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> memoryStore = new java.util.concurrent.ConcurrentHashMap<>();

    public RateLimitFilter(Redis redis) {
        this.redis = redis;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 全局 API 限流未启用则直接放行
        if (!CommonConstants.globalApiRateLimitEnable) {
            chain.doFilter(request, response);
            return;
        }

        int maxRequestNum = CommonConstants.globalApiRateLimitNum;
        long duration = CommonConstants.globalApiRateLimitDuration;
        String mark = "GA";
        String clientIP = ai.yue.library.web.util.ServletUtils.getClientIP(req);
        String key = "rateLimit:" + mark + clientIP;

        boolean allowed;
        try {
            allowed = redisRateLimit(key, maxRequestNum, duration);
        } catch (Exception e) {
            log.warn("Redis 限流不可用，回退到内存限流: {}", e.getMessage());
            allowed = memoryRateLimit(mark + clientIP, maxRequestNum, duration);
        }

        if (!allowed) {
            resp.setStatus(429);
            resp.setContentType("application/json; charset=UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"请求频率过高，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Redis 滑动窗口限流      * <p>
     * 算法：用 Redis List 存储请求时间戳，LPUSH 新时间戳到头部。
     * List 长度 < maxRequestNum 时直接放行；否则检查尾部（最旧）时间戳是否在窗口外。
     */
    private boolean redisRateLimit(String key, int maxRequestNum, long duration) {
        RedissonClient client = redis.getRedisson();
        RList<String> list = client.getList(key);
        int listLength = list.size();

        if (listLength < maxRequestNum) {
            // 未达限制，记录本次请求
            list.add(0, nowFormatted());
            list.expire(CommonConstants.rateLimitKeyExpirationDuration);
            return true;
        }

        // 检查最旧的时间戳（尾部）
        String oldTimeStr = list.get(listLength - 1);
        long oldTime = parseFormattedTime(oldTimeStr);
        long now = nowEpochSeconds();

        if (now - oldTime < duration) {
            // 窗口内已达限制
            list.expire(CommonConstants.rateLimitKeyExpirationDuration);
            return false;
        }

        // 窗口外，移除旧记录并添加新记录
        list.add(0, nowFormatted());
        // LTRIM: 保留 0 到 maxRequestNum-1
        list.trim(0, maxRequestNum - 1);
        list.expire(CommonConstants.rateLimitKeyExpirationDuration);
        return true;
    }

    /**
     * 内存滑动窗口限流      * <p>
     * 算法：用数组存储请求时间戳，长度 < max 时追加；
     * 否则检查头部（最旧）时间戳，窗口外则移除头部并追加新值。
     */
    private synchronized boolean memoryRateLimit(String key, int maxRequestNum, long duration) {
        long now = nowEpochSeconds();
        long[] queue = memoryStore.get(key);

        if (queue == null || queue.length < maxRequestNum) {
            // 首次或未满，追加
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

        // 已满，检查头部（最旧）
        if (now - queue[0] >= duration) {
            // 窗口外，移除头部追加尾部
            long[] newQueue = new long[queue.length];
            System.arraycopy(queue, 1, newQueue, 0, queue.length - 1);
            newQueue[queue.length - 1] = now;
            memoryStore.put(key, newQueue);
            return true;
        }

        // 窗口内已达限制
        return false;
    }

    // ======================== 辅助方法 ========================

    private String nowFormatted() {
        return LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMAT);
    }

    private long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private long parseFormattedTime(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, TIME_FORMAT).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return 0;
        }
    }
}
