package yaoshu.token.middleware;

import ai.yue.library.data.redis.client.Redis;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import yaoshu.token.service.OptionService;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 模型请求限流中间件  * <p>
 * 按已认证用户 ID 维度限流（并非按模型名维度，而是限制用户在时间窗口内的模型请求总数/成功数）。
 * <p>
 * 双层限流策略：
 * <ol>
 * <li>成功请求数限制（successMaxCount）— 只统计 HTTP &lt; 400 的请求</li>
 * <li>总请求数限制（totalMaxCount）— 统计所有请求（含失败），totalMaxCount=0 时跳过</li>
 * </ol>
 * 支持按用户分组配置不同的限流参数（ModelRequestRateLimitGroup）。
 */
@Slf4j
public class ModelRateLimitFilter implements Filter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /** 成功请求限流 mark */
    private static final String SUCCESS_MARK = "MRRLS";
    /** 总请求限流 mark */
    private static final String TOTAL_MARK = "MRRL";

    private final Redis redis;
    private final OptionService optionService;

    /** 内存限流存储 */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> memoryStore = new java.util.concurrent.ConcurrentHashMap<>();

    public ModelRateLimitFilter(Redis redis, OptionService optionService) {
        this.redis = redis;
        this.optionService = optionService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 限流未启用则直接放行
        if (!isRateLimitEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 获取已认证的用户 ID（由前置 AuthFilter 注入）
        Object userIdAttr = request.getAttribute("id");
        if (userIdAttr == null) {
            // 未认证用户不做限流（由前置认证拦截）
            chain.doFilter(request, response);
            return;
        }
        String userId = String.valueOf(userIdAttr);

        long duration = getDurationMinutes() * 60;
        int totalMaxCount = getTotalMaxCount();
        int successMaxCount = getSuccessMaxCount();

        // 获取分组限流配置（覆盖全局配置）
        String group = getGroupFromRequest(request);
        int[] groupLimits = getGroupRateLimit(group);
        if (groupLimits != null) {
            totalMaxCount = groupLimits[0];
            successMaxCount = groupLimits[1];
        }

        // 1. 检查成功请求数限制
        String successKey = "rateLimit:" + SUCCESS_MARK + ":" + userId;
        boolean successAllowed;
        try {
            successAllowed = checkRedisRateLimit(successKey, successMaxCount, duration);
        } catch (Exception e) {
            log.warn("Redis 限流不可用，回退到内存限流: {}", e.getMessage());
            successAllowed = checkMemoryRateLimit(SUCCESS_MARK + userId, successMaxCount, duration);
        }

        if (!successAllowed) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    429,
                    String.format("您已达到请求数限制：%d分钟内最多请求%d次", getDurationMinutes(), successMaxCount));
            return;
        }

        // 2. 检查总请求数限制（totalMaxCount=0 时跳过）
        if (totalMaxCount > 0) {
            String totalKey = "rateLimit:" + TOTAL_MARK + ":" + userId;
            boolean totalAllowed;
            try {
                totalAllowed = checkRedisRateLimit(totalKey, totalMaxCount, duration);
            } catch (Exception e) {
                totalAllowed = checkMemoryRateLimit(TOTAL_MARK + userId, totalMaxCount, duration);
            }
            if (!totalAllowed) {
                MiddlewareUtils.abortWithOpenAiMessage(response,
                        429,
                        String.format("您已达到总请求数限制：%d分钟内最多请求%d次，包括失败次数，请检查您的请求是否正确",
                                getDurationMinutes(), totalMaxCount));
                return;
            }
        }

        // 3. 处理请求
        chain.doFilter(request, response);

        // 4. 请求成功（HTTP < 400）则记录成功请求
        if (response.getStatus() < 400) {
            try {
                recordRedisRequest(successKey, successMaxCount);
            } catch (Exception e) {
                // 记录失败不影响请求
                log.debug("记录成功请求到 Redis 失败: {}", e.getMessage());
            }
        }
    }

    // ======================== Redis 限流检查 ========================

    /**
     * Redis 限流检查      * <p>
     * maxCount=0 表示不限制。检查 List 长度和最旧时间戳是否在窗口外。
     */
    private boolean checkRedisRateLimit(String key, int maxCount, long duration) {
        if (maxCount == 0) {
            return true;
        }
        RedissonClient client = redis.getRedisson();
        RList<String> list = client.getList(key);
        int length = list.size();

        if (length < maxCount) {
            return true;
        }

        // 检查时间窗口
        String oldTimeStr = list.get(length - 1);
        long oldTime = parseFormattedTime(oldTimeStr);
        long now = nowEpochSeconds();

        if (now - oldTime < duration) {
            list.expire(Duration.ofMinutes(getDurationMinutes()));
            return false;
        }
        return true;
    }

    /**
     * 记录 Redis 请求      */
    private void recordRedisRequest(String key, int maxCount) {
        if (maxCount == 0) {
            return;
        }
        RedissonClient client = redis.getRedisson();
        RList<String> list = client.getList(key);
        list.add(0, nowFormatted());
        list.trim(0, maxCount - 1);
        list.expire(Duration.ofMinutes(getDurationMinutes()));
    }

    // ======================== 内存限流（Redis 不可用时回退） ========================

    /**
     * 内存限流检查      */
    private synchronized boolean checkMemoryRateLimit(String key, int maxCount, long duration) {
        if (maxCount == 0) {
            return true;
        }
        long now = nowEpochSeconds();
        long[] queue = memoryStore.get(key);

        if (queue == null || queue.length < maxCount) {
            // 未满，记录
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

        if (now - queue[0] >= duration) {
            long[] newQueue = new long[queue.length];
            System.arraycopy(queue, 1, newQueue, 0, queue.length - 1);
            newQueue[queue.length - 1] = now;
            memoryStore.put(key, newQueue);
            return true;
        }
        return false;
    }

    // ======================== 配置读取 ========================

    private boolean isRateLimitEnabled() {
        return getBoolOption("ModelRequestRateLimitEnabled");
    }

    private int getDurationMinutes() {
        return getIntOption("ModelRequestRateLimitDurationMinutes", 1);
    }

    private int getTotalMaxCount() {
        return getIntOption("ModelRequestRateLimitCount", 0);
    }

    private int getSuccessMaxCount() {
        return getIntOption("ModelRequestRateLimitSuccessCount", 1000);
    }

    /**
     * 获取分组限流配置      * <p>
     * options key: "ModelRequestRateLimitGroup"，value 为 JSON {"group": [total, success]}
     *
     * @return [totalCount, successCount] 或 null（未配置）
     */
    private int[] getGroupRateLimit(String group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        String jsonStr = optionService.getValue("ModelRequestRateLimitGroup");
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }
        try {
            com.alibaba.fastjson2.JSONObject obj = ai.yue.library.base.convert.Convert.toJSONObject(jsonStr);
            com.alibaba.fastjson2.JSONArray limits = obj.getJSONArray(group);
            if (limits != null && limits.size() == 2) {
                return new int[]{limits.getIntValue(0), limits.getIntValue(1)};
            }
        } catch (Exception e) {
            log.debug("解析 ModelRequestRateLimitGroup 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从请求属性获取用户分组（由前置认证 Filter 注入）
     */
    private String getGroupFromRequest(HttpServletRequest request) {
        Object tokenGroup = request.getAttribute("token_group");
        if (tokenGroup instanceof String s && !s.isEmpty()) {
            return s;
        }
        Object userGroup = request.getAttribute("user_group");
        if (userGroup instanceof String s && !s.isEmpty()) {
            return s;
        }
        return null;
    }

    // ======================== 辅助方法 ========================

    private boolean getBoolOption(String key) {
        String value = optionService.getValue(key);
        return "true".equalsIgnoreCase(value);
    }

    private int getIntOption(String key, int defaultValue) {
        String value = optionService.getValue(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

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
