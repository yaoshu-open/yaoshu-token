package yaoshu.token.service;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.common.ModelUtils;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.pojo.dto.TaskBillingContext;
import yaoshu.token.pojo.dto.TaskPrivateData;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.pojo.entity.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步任务计费服务  * <p>
 * 实际预扣费由 BillingSession（PreConsumeBilling）完成，本类负责：
 * 1. LogTaskConsumption — 记录任务消费日志和统计
 * 2. RefundTaskQuota — 任务失败时退还预扣额度
 * 3. RecalculateTaskQuota — 任务完成后差额结算
 * 4. RecalculateTaskQuotaByTokens — 按 token 重算费用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskBillingService {

    private final UserService userService;
    private final TokenMapper tokenMapper;
    private final LogMapper logMapper;
    private final SubscriptionService subscriptionService;
    private final ModelUtils modelUtils;    /** 日志类型常量*/     private static final int LOG_TYPE_CONSUME = 2;
    private static final int LOG_TYPE_REFUND = 6;

    /** 资金来源标识 */
    private static final String BILLING_SOURCE_SUBSCRIPTION = "subscription";

    // ======================== 日志记录 ========================

    /**
     * 记录任务消费日志和统计信息（仅记录，不涉及实际扣费）      * <p>
     * 实际扣费已由 BillingSession 完成。
     *
     * @param info      中转上下文信息（简化版，仅取必要字段）
     * @param userId    用户 ID
     * @param channelId 渠道 ID
     * @param tokenId   令牌 ID
     * @param tokenName 令牌名称
     * @param modelName 模型名
     * @param quota     消费额度
     * @param group     分组
     * @param other     附加信息
     */
    public void logTaskConsumption(int userId, int channelId, int tokenId, String tokenName,
                                    String modelName, int quota, String group,
                                    Map<String, Object> other) {
        try {
            Log logEntry = new Log();
            logEntry.setUserId(userId);
            logEntry.setType(LOG_TYPE_CONSUME);
            logEntry.setChannelId(channelId);
            logEntry.setModelName(modelName);
            logEntry.setTokenId(tokenId);
            logEntry.setTokenName(tokenName);
            logEntry.setQuota(quota);
            logEntry.setGroup(group);
            logEntry.setCreatedAt(System.currentTimeMillis() / 1000);
            if (other != null) {
                logEntry.setOther(Convert.toJSONString(other));
            }
            logMapper.insert(logEntry);

            // 批量更新用户已用额度和渠道已用额度
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_USED_QUOTA, userId, quota);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_REQUEST_COUNT, userId, 1);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA, channelId, quota);
        } catch (Exception e) {
            log.error("记录任务消费日志失败: {}", e.getMessage());
        }
    }

    // ======================== 退款 ========================

    /**
     * 统一的任务失败退款逻辑      * <p>
     * 退还预扣的 quota 给用户（支持钱包和订阅），并退还令牌额度。
     *
     * @param task   任务对象（含 privateData 计费信息）
     * @param reason 退款原因
     */
    public void refundTaskQuota(Task task, String reason) {
        if (task.getQuota() == null || task.getQuota() == 0) {
            return;
        }
        int quota = task.getQuota();

        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData == null) {
            log.warn("无法解析任务私有数据，跳过退款 task={}", task.getTaskId());
            return;
        }

        // 1. 退还资金来源（钱包或订阅）
        try {
            taskAdjustFunding(task, privateData, -quota);
        } catch (Exception e) {
            log.warn("退还资金来源失败 task {}: {}", task.getTaskId(), e.getMessage());
            return;
        }

        // 2. 退还令牌额度
        taskAdjustTokenQuota(task, privateData, -quota);

        // 3. 记录退款日志
        Map<String, Object> other = buildBillingOther(privateData, task);
        other.put("task_id", task.getTaskId());
        other.put("reason", reason);
        recordTaskBillingLog(task, privateData, quota, LOG_TYPE_REFUND, reason, other);
    }

    // ======================== 差额结算 ========================

    /**
     * 通用的异步差额结算      * <p>
     * actualQuota 是任务完成后的实际应扣额度，与预扣额度做差额结算。
     *
     * @param task         任务对象
     * @param actualQuota  实际应扣额度
     * @param reason       结算原因（用于日志）
     */
    public void recalculateTaskQuota(Task task, int actualQuota, String reason) {
        if (actualQuota <= 0) {
            return;
        }
        int preConsumedQuota = task.getQuota() != null ? task.getQuota() : 0;
        int quotaDelta = actualQuota - preConsumedQuota;

        if (quotaDelta == 0) {
            log.info("任务 {} 预扣费准确（{}，{}）", task.getTaskId(), actualQuota, reason);
            return;
        }

        log.info("任务 {} 差额结算：delta={}（实际：{}，预扣：{}，{}）",
                task.getTaskId(), quotaDelta, actualQuota, preConsumedQuota, reason);

        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData == null) {
            log.warn("无法解析任务私有数据，跳过差额结算 task={}", task.getTaskId());
            return;
        }

        // 调整资金来源
        try {
            taskAdjustFunding(task, privateData, quotaDelta);
        } catch (Exception e) {
            log.error("差额结算资金调整失败 task {}: {}", task.getTaskId(), e.getMessage());
            return;
        }

        // 调整令牌额度
        taskAdjustTokenQuota(task, privateData, quotaDelta);

        task.setQuota(actualQuota);

        int logType;
        int logQuota;
        if (quotaDelta > 0) {
            logType = LOG_TYPE_CONSUME;
            logQuota = quotaDelta;
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_USED_QUOTA, task.getUserId(), quotaDelta);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_REQUEST_COUNT, task.getUserId(), 0);
            modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA, task.getChannelId(), quotaDelta);
        } else {
            logType = LOG_TYPE_REFUND;
            logQuota = -quotaDelta;
        }

        Map<String, Object> other = buildBillingOther(privateData, task);
        other.put("task_id", task.getTaskId());
        other.put("pre_consumed_quota", preConsumedQuota);
        other.put("actual_quota", actualQuota);
        recordTaskBillingLog(task, privateData, logQuota, logType, reason, other);
    }

    /**
     * 根据实际 token 消耗重新计费      * <p>
     * 当任务成功且返回了 totalTokens 时，根据模型倍率和分组倍率重新计算实际扣费额度。
     *
     * @param task        任务对象
     * @param totalTokens 实际 token 消耗
     */
    public void recalculateTaskQuotaByTokens(Task task, int totalTokens) {
        if (totalTokens <= 0) {
            return;
        }

        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData == null || privateData.getBillingContext() == null) {
            return;
        }

        TaskBillingContext bc = privateData.getBillingContext();
        if (bc.getModelRatio() == null || bc.getModelRatio() <= 0) {
            // 固定价格不按 token 重算
            return;
        }

        // 计算实际应扣费额度: totalTokens * modelRatio * groupRatio * otherMultiplier
        double otherMultiplier = 1.0;
        if (bc.getOtherRatios() != null) {
            for (Double r : bc.getOtherRatios().values()) {
                if (r != null && r != 1.0 && r > 0) {
                    otherMultiplier *= r;
                }
            }
        }

        double groupRatio = bc.getGroupRatio() != null ? bc.getGroupRatio() : 1.0;
        int actualQuota = (int) (totalTokens * bc.getModelRatio() * groupRatio * otherMultiplier);

        String reason = String.format("token重算：tokens=%d, modelRatio=%.2f, groupRatio=%.2f, otherMultiplier=%.4f",
                totalTokens, bc.getModelRatio(), groupRatio, otherMultiplier);
        recalculateTaskQuota(task, actualQuota, reason);
    }

    // ======================== 内部辅助方法 ========================

    /**
     * 调整任务的资金来源（钱包或订阅）      * <p>
     * delta > 0 表示扣费，delta < 0 表示退还
     */
    private void taskAdjustFunding(Task task, TaskPrivateData privateData, int delta) {
        if (isSubscriptionTask(privateData)) {
            Integer subId = privateData.getSubscriptionId();
            if (subId != null && subId > 0) {
                subscriptionService.postConsumeDelta(subId, delta);
            }
            return;
        }
        if (delta > 0) {
            userService.decreaseUserQuota(task.getUserId(), delta);
        } else if (delta < 0) {
            userService.increaseUserQuota(task.getUserId(), -delta);
        }
    }

    /**
     * 调整任务的令牌额度      * <p>
     * delta > 0 表示扣费，delta < 0 表示退还。
     * 需要通过 tokenId 运行时获取 key（用于 Redis 缓存操作）。
     */
    private void taskAdjustTokenQuota(Task task, TaskPrivateData privateData, int delta) {
        if (privateData.getTokenId() == null || privateData.getTokenId() <= 0 || delta == 0) {
            return;
        }
        int tokenId = privateData.getTokenId();

        // 运行时获取 token key（用于缓存失效等操作）
        String tokenKey = resolveTokenKey(tokenId, task.getTaskId());
        if (tokenKey == null || tokenKey.isEmpty()) {
            return;
        }

        try {
            if (delta > 0) {
                tokenMapper.decreaseRemainQuota(tokenId, delta);
                tokenMapper.increaseUsedQuota(tokenId, delta);
            } else {
                tokenMapper.increaseRemainQuotaSafe(tokenId, -delta);
            }
        } catch (Exception e) {
            log.warn("调整令牌额度失败 (delta={}, task={}): {}", delta, task.getTaskId(), e.getMessage());
        }
    }

    /**
     * 通过 tokenId 获取令牌 Key      */
    private String resolveTokenKey(int tokenId, String taskId) {
        try {
            Token token = tokenMapper.selectById(tokenId);
            if (token == null) {
                log.warn("获取令牌 key 失败 (tokenId={}, task={}): token not found", tokenId, taskId);
                return "";
            }
            return token.getKey();
        } catch (Exception e) {
            log.warn("获取令牌 key 失败 (tokenId={}, task={}): {}", tokenId, taskId, e.getMessage());
            return "";
        }
    }

    /**
     * 判断任务是否通过订阅计费      */
    private boolean isSubscriptionTask(TaskPrivateData privateData) {
        return BILLING_SOURCE_SUBSCRIPTION.equals(privateData.getBillingSource())
                && privateData.getSubscriptionId() != null
                && privateData.getSubscriptionId() > 0;
    }

    /**
     * 从 BillingContext 构建日志 Other 字段      */
    private Map<String, Object> buildBillingOther(TaskPrivateData privateData, Task task) {
        Map<String, Object> other = new HashMap<>();
        TaskBillingContext bc = privateData.getBillingContext();
        if (bc != null) {
            other.put("model_price", bc.getModelPrice());
            if (bc.getModelRatio() != null && bc.getModelRatio() > 0) {
                other.put("model_ratio", bc.getModelRatio());
            }
            other.put("group_ratio", bc.getGroupRatio());
            if (bc.getOtherRatios() != null && !bc.getOtherRatios().isEmpty()) {
                other.putAll(bc.getOtherRatios());
            }
        }
        return other;
    }

    /**
     * 记录任务计费日志      */
    private void recordTaskBillingLog(Task task, TaskPrivateData privateData, int quota,
                                       int logType, String content, Map<String, Object> other) {
        try {
            Log logEntry = new Log();
            logEntry.setUserId(task.getUserId());
            logEntry.setType(logType);
            logEntry.setContent(content != null ? content : "");
            logEntry.setChannelId(task.getChannelId());

            // 模型名优先从 BillingContext 获取
            String modelName = "";
            if (privateData.getBillingContext() != null && privateData.getBillingContext().getOriginModelName() != null) {
                modelName = privateData.getBillingContext().getOriginModelName();
            }
            logEntry.setModelName(modelName);
            logEntry.setQuota(quota);
            logEntry.setTokenId(privateData.getTokenId());
            logEntry.setGroup(task.getGroup());
            logEntry.setCreatedAt(System.currentTimeMillis() / 1000);
            if (other != null) {
                logEntry.setOther(Convert.toJSONString(other));
            }
            logMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("记录任务计费日志失败: {}", e.getMessage());
        }
    }

    /**
     * 解析任务的 privateData JSON      */
    private TaskPrivateData parsePrivateData(String privateDataJson) {
        if (privateDataJson == null || privateDataJson.isEmpty()) {
            return null;
        }
        try {
            return Convert.toJavaBean(privateDataJson, TaskPrivateData.class);
        } catch (Exception e) {
            log.warn("解析 privateData 失败: {}", e.getMessage());
            return null;
        }
    }
}
