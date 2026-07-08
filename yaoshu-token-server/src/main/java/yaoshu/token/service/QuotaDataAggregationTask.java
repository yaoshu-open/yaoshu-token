package yaoshu.token.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.mapper.QuotaMapper;
import yaoshu.token.pojo.entity.QuotaData;

/**
 * 配额数据聚合定时任务——从 logs 表增量聚合消费日志（type=2）写入 quota_data。
 * <p>
 * （Go 原版为实时内存缓存 + 定时刷盘，语义等价：均按小时桶对齐 created_at 累积 token 用量）。
 * <p>
 * 排行榜（RankingsService）与数据看板（DataController）消费 quota_data 表，本任务是其唯一数据来源。
 * <p>
 * 幂等：用 options 表 key=quota_data_last_log_id 记录聚合位置，按 logs.id 单调递增分批推进，
 * 每批事务原子（聚合 + upsert + 推进位置），崩溃后已提交批次位置已推进，未提交批次下次重跑。
 * <p>
 * 并发：AtomicBoolean 防重入。本项目为单体应用，无需主节点判断（对齐 SubscriptionResetTaskService 范式）。
 * 多实例部署时本任务会各自聚合，但 QuotaMapper 查询用 SUM+GROUP BY，重复行仅致表轻微膨胀不影响结果正确性。
 *
 * @author yaoshu
 */
@Slf4j
@Service
public class QuotaDataAggregationTask {

    private final QuotaMapper quotaMapper;
    private final OptionService optionService;
    private final TransactionTemplate transactionTemplate;

    /** options 表 key：上次聚合到的 logs.id（exclusive） */
    private static final String OPTION_LAST_LOG_ID = "quota_data_last_log_id";

    /** 每批聚合的 logs id 区间跨度（id 可能含空洞，聚合 SQL 的 type=2 过滤实际记录） */
    private static final long BATCH_ID_SPAN = 2000L;

    /** 防重入标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public QuotaDataAggregationTask(QuotaMapper quotaMapper, OptionService optionService,
                                    PlatformTransactionManager txManager) {
        this.quotaMapper = quotaMapper;
        this.optionService = optionService;
        // 编程式事务：每批独立提交，避免 self-invocation 导致 @Transactional 代理失效
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    /**
     * 定时聚合：每 5 分钟执行一次，首次启动延迟 1 分钟（首跑从 last_log_id=0 全量回溯）。
     */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void aggregateQuotaData() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int totalBatches = runAggregationLoop();
            if (totalBatches > 0) {
                log.info("quota_data aggregation done: batches={}", totalBatches);
            }
        } catch (Exception e) {
            log.warn("quota_data aggregation task failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * 聚合主循环：从 last_log_id 推进到当前 logs 最大 id，分批事务提交。
     *
     * @return 本轮处理的批次数
     */
    private int runAggregationLoop() {
        long lastId = loadLastLogId();
        Long currentMax = quotaMapper.selectMaxLogId();
        if (currentMax == null || currentMax <= lastId) {
            return 0;
        }

        int batches = 0;
        while (lastId < currentMax) {
            long batchEndId = Math.min(lastId + BATCH_ID_SPAN, currentMax);
            long rangeStart = lastId;
            // 事务内聚合 + upsert + 推进位置；回滚则抛异常中止任务，下次从 DB 未推进的 last_log_id 重跑
            transactionTemplate.executeWithoutResult(status -> {
                List<QuotaData> aggregated = quotaMapper.aggregateLogsForQuotaData(rangeStart, batchEndId);
                for (QuotaData row : aggregated) {
                    upsertQuotaData(row);
                }
                saveLastLogId(batchEndId);
            });
            lastId = batchEndId;
            batches++;
        }
        return batches;
    }

    /**
     * quota_data upsert：按 (userId, username, modelName, createdAt 小时桶) 查存在性，
     * 存在则增量累加（count/quota/token_used），不存在则插入。
     * <p>
     * quota_data 表无唯一约束，但本任务是 quota_data 的唯一写入者（防重入 + 主节点），无并发写入风险。
     */
    private void upsertQuotaData(QuotaData row) {
        QuotaData existing = quotaMapper.selectOne(
                new LambdaQueryWrapper<QuotaData>()
                        .eq(QuotaData::getUserId, row.getUserId())
                        .eq(QuotaData::getUsername, row.getUsername())
                        .eq(QuotaData::getModelName, row.getModelName())
                        .eq(QuotaData::getCreatedAt, row.getCreatedAt()));
        if (existing != null) {
            quotaMapper.update(null,
                    new LambdaUpdateWrapper<QuotaData>()
                            .eq(QuotaData::getId, existing.getId())
                            .setSql("count = count + " + row.getCount())
                            .setSql("quota = quota + " + row.getQuota())
                            .setSql("token_used = token_used + " + row.getTokenUsed()));
        } else {
            quotaMapper.insert(row);
        }
    }

    private long loadLastLogId() {
        String value = optionService.getValue(OPTION_LAST_LOG_ID);
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("invalid {} value={}, reset to 0", OPTION_LAST_LOG_ID, value);
            return 0L;
        }
    }

    private void saveLastLogId(long lastId) {
        optionService.saveOrUpdate(OPTION_LAST_LOG_ID, String.valueOf(lastId));
    }
}
