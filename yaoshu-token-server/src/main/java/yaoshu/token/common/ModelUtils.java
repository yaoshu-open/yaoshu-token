package yaoshu.token.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.mapper.UserMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 批量更新工具  * <p>
 * 批量聚合用户配额、Token 配额、渠道配额等增量数据，定时刷入数据库。
 *
 * @author yaoshu
 */
@Slf4j
@Component
public class ModelUtils {

    /**
     * 批量更新间隔（秒），对齐 Go common.BatchUpdateInterval
     */
    private static final int BATCH_UPDATE_INTERVAL = 5;

    // 批量更新类型
    public static final int BATCH_UPDATE_TYPE_USER_QUOTA = 0;
    public static final int BATCH_UPDATE_TYPE_TOKEN_QUOTA = 1;
    public static final int BATCH_UPDATE_TYPE_USED_QUOTA = 2;
    public static final int BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA = 3;
    public static final int BATCH_UPDATE_TYPE_REQUEST_COUNT = 4;
    private static final int BATCH_UPDATE_TYPE_COUNT = 5;

    private final Map<Integer, Integer>[] batchUpdateStores = new Map[BATCH_UPDATE_TYPE_COUNT];
    private final Object[] batchUpdateLocks = new Object[BATCH_UPDATE_TYPE_COUNT];

    private final TokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final ChannelMapper channelMapper;

    private ScheduledExecutorService scheduler;

    public ModelUtils(TokenMapper tokenMapper, UserMapper userMapper, ChannelMapper channelMapper) {
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
        this.channelMapper = channelMapper;
        for (int i = 0; i < BATCH_UPDATE_TYPE_COUNT; i++) {
            batchUpdateStores[i] = new HashMap<>();
            batchUpdateLocks[i] = new Object();
        }
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-updater");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::batchUpdate, BATCH_UPDATE_INTERVAL, BATCH_UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 添加一条增量记录
     */
    public void addNewRecord(int type, int id, int value) {
        synchronized (batchUpdateLocks[type]) {
            batchUpdateStores[type].merge(id, value, Integer::sum);
        }
    }

    /**
     * 批量刷入数据库
     */
    private void batchUpdate() {
        // 检查是否有数据需要更新
        boolean hasData = false;
        for (int i = 0; i < BATCH_UPDATE_TYPE_COUNT; i++) {
            synchronized (batchUpdateLocks[i]) {
                if (!batchUpdateStores[i].isEmpty()) {
                    hasData = true;
                    break;
                }
            }
        }
        if (!hasData) {
            return;
        }

        log.info("batch update started");

        // 快照当前数据并重置
        @SuppressWarnings("unchecked")
        Map<Integer, Integer>[] stores = new Map[BATCH_UPDATE_TYPE_COUNT];
        for (int i = 0; i < BATCH_UPDATE_TYPE_COUNT; i++) {
            synchronized (batchUpdateLocks[i]) {
                stores[i] = new HashMap<>(batchUpdateStores[i]);
                batchUpdateStores[i] = new HashMap<>();
            }
        }

        // 先处理非用户聚合的批次
        for (int i = 0; i < BATCH_UPDATE_TYPE_COUNT; i++) {
            if (i == BATCH_UPDATE_TYPE_USER_QUOTA || i == BATCH_UPDATE_TYPE_USED_QUOTA || i == BATCH_UPDATE_TYPE_REQUEST_COUNT) {
                continue;
            }
            for (Map.Entry<Integer, Integer> entry : stores[i].entrySet()) {
                int key = entry.getKey();
                int value = entry.getValue();
                switch (i) {
                    case BATCH_UPDATE_TYPE_TOKEN_QUOTA:
                        increaseTokenQuota(key, value);
                        break;
                    case BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA:
                        updateChannelUsedQuota(key, value);
                        break;
                    default:
                        break;
                }
            }
        }

        // 处理用户聚合批次（UserQuota/UsedQuota/RequestCount 需要聚合到一个事务）
        Map<Integer, Integer> userQuotaStore = stores[BATCH_UPDATE_TYPE_USER_QUOTA];
        Map<Integer, Integer> usedQuotaStore = stores[BATCH_UPDATE_TYPE_USED_QUOTA];
        Map<Integer, Integer> requestCountStore = stores[BATCH_UPDATE_TYPE_REQUEST_COUNT];

        Map<Integer, Void> userIds = new HashMap<>();
        for (Integer key : userQuotaStore.keySet()) {
            userIds.put(key, null);
        }
        for (Integer key : usedQuotaStore.keySet()) {
            userIds.put(key, null);
        }
        for (Integer key : requestCountStore.keySet()) {
            userIds.put(key, null);
        }

        for (Integer userId : userIds.keySet()) {
            updateUserQuotaUsedQuotaAndRequestCount(
                    userId,
                    userQuotaStore.getOrDefault(userId, 0),
                    usedQuotaStore.getOrDefault(userId, 0),
                    requestCountStore.getOrDefault(userId, 0));
        }
        log.info("batch update finished");
    }

    // ===== 数据库批量更新 ===== 
    /**
     * Token 配额批量更新      * remain_quota += value，used_quota -= value，刷新 accessed_time。
     */
    private void increaseTokenQuota(int tokenId, int value) {
        try {
            tokenMapper.batchUpdateTokenQuota(tokenId, value, System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            log.error("批量更新 Token 配额失败 tokenId={} value={}: {}", tokenId, value, e.getMessage());
        }
    }

    /**
     * 渠道已用配额批量更新。      */
    private void updateChannelUsedQuota(int channelId, int value) {
        try {
            channelMapper.increaseUsedQuota(channelId, value);
        } catch (Exception e) {
            log.error("批量更新渠道已用配额失败 channelId={} value={}: {}", channelId, value, e.getMessage());
        }
    }

    /**
     * 用户配额聚合批量更新      * quota += userQuota，used_quota += usedQuota，request_count += requestCount。
     */
    private void updateUserQuotaUsedQuotaAndRequestCount(int userId, int userQuota, int usedQuota, int requestCount) {
        if (userQuota == 0 && usedQuota == 0 && requestCount == 0) {
            return;
        }
        try {
            userMapper.batchUpdateUserQuota(userId, userQuota, usedQuota, requestCount);
        } catch (Exception e) {
            log.error("批量更新用户配额失败 userId={} quota={} usedQuota={} requestCount={}: {}",
                    userId, userQuota, usedQuota, requestCount, e.getMessage());
        }
    }
}
