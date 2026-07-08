package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.dto.TaskBillingContext;
import yaoshu.token.pojo.dto.TaskPrivateData;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.task.common.TaskCommonHelper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 任务轮询服务  * <p>
 * 负责定时扫描未完成异步任务，按平台和渠道分组拉取上游状态，并在任务进入终态时执行退款或差额结算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPollingService {

    private static final long LEGACY_TASK_CUTOFF = 1740182400L;

    private final TaskService taskService;
    private final TaskBillingService taskBillingService;
    private final OptionService optionService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 启动任务轮询循环。      */
    @Scheduled(fixedDelay = 15_000L, initialDelay = 15_000L)
    public void startTaskPollingLoop() {
        if (!running.compareAndSet(false, true)) {
            log.debug("任务进度轮询仍在执行，跳过本轮");
            return;
        }
        try {
            log.info("任务进度轮询开始");
            sweepTimedOutTasks();
            List<Task> allTasks = taskService.getAllUnFinishSyncTasks(TaskConstants.TASK_QUERY_LIMIT);
            Map<String, List<Task>> platformTasks = allTasks.stream()
                    .filter(task -> task.getPlatform() != null && !task.getPlatform().isEmpty())
                    .collect(Collectors.groupingBy(Task::getPlatform, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<Task>> entry : platformTasks.entrySet()) {
                dispatchPlatformUpdate(entry.getKey(), entry.getValue());
            }
            log.info("任务进度轮询完成");
        } catch (Exception e) {
            log.error("任务进度轮询失败", e);
        } finally {
            running.set(false);
        }
    }

    /**
     * 独立清理超时任务，每次最多处理 100 条。
     */
    void sweepTimedOutTasks() {
        if (TaskConstants.TASK_TIMEOUT_MINUTES <= 0) {
            return;
        }
        long cutoff = Instant.now().getEpochSecond() - TaskConstants.TASK_TIMEOUT_MINUTES * 60L;
        List<Task> tasks = taskService.getTimedOutUnfinishedTasks(cutoff, 100);
        if (tasks.isEmpty()) {
            return;
        }

        String reason = "任务超时（" + TaskConstants.TASK_TIMEOUT_MINUTES + "分钟）";
        String legacyReason = "任务超时（旧系统遗留任务，不进行退款，请联系管理员）";
        long now = Instant.now().getEpochSecond();
        int timedOutCount = 0;

        for (Task task : tasks) {
            boolean legacy = task.getSubmitTime() != null && task.getSubmitTime() > 0 && task.getSubmitTime() < LEGACY_TASK_CUTOFF;
            String oldStatus = task.getStatus();
            task.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            task.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
            task.setFinishTime(now);
            task.setFailReason(legacy ? legacyReason : reason);

            boolean won = taskService.updateWithStatus(task, oldStatus);
            if (!won) {
                log.info("sweepTimedOutTasks: task {} 已被其他轮询推进，跳过", task.getTaskId());
                continue;
            }
            timedOutCount++;
            if (!legacy && task.getQuota() != null && task.getQuota() != 0) {
                taskBillingService.refundTaskQuota(task, reason);
            }
        }
        if (timedOutCount > 0) {
            log.info("sweepTimedOutTasks: timed out {} tasks", timedOutCount);
        }
    }

    /**
     * 按平台分发轮询更新。
     */
    void dispatchPlatformUpdate(String platform, List<Task> tasks) {
        if (TaskConstants.TASK_PLATFORM_MIDJOURNEY.equals(platform)) {
            log.debug("Midjourney 轮询由 Midjourney 链路处理，本服务跳过 {} 个任务", tasks.size());
            return;
        }

        Map<Integer, List<Task>> channelTasks = new LinkedHashMap<>();
        List<Long> nullTaskIds = new ArrayList<>();
        for (Task task : tasks) {
            String upstreamId = getUpstreamTaskId(task);
            if (upstreamId == null || upstreamId.isEmpty()) {
                nullTaskIds.add(task.getId());
                continue;
            }
            channelTasks.computeIfAbsent(task.getChannelId(), ignored -> new ArrayList<>()).add(task);
        }
        if (!nullTaskIds.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("status", TaskConstants.TASK_STATUS_FAILURE);
            params.put("progress", TaskCommonHelper.PROGRESS_COMPLETE);
            taskService.taskBulkUpdateByID(nullTaskIds, params);
            log.info("Fix null upstream task_id task success: {}", nullTaskIds);
        }
        for (Map.Entry<Integer, List<Task>> entry : channelTasks.entrySet()) {
            updateVideoTasks(platform, entry.getKey(), entry.getValue());
        }
    }

    private void updateVideoTasks(String platform, Integer channelId, List<Task> tasks) {
        if (channelId == null || tasks.isEmpty()) {
            return;
        }
        Channel channel = ChannelCacheService.cacheGetChannel(channelId);
        if (channel == null) {
            List<Long> failedIds = tasks.stream().map(Task::getId).toList();
            Map<String, Object> params = new HashMap<>();
            params.put("fail_reason", "Failed to get channel info, channel ID: " + channelId);
            params.put("status", TaskConstants.TASK_STATUS_FAILURE);
            params.put("progress", TaskCommonHelper.PROGRESS_COMPLETE);
            taskService.taskBulkUpdateByID(failedIds, params);
            throw new IllegalStateException("CacheGetChannel failed: " + channelId);
        }

        IAdaptor.ITaskAdaptor adaptor = RelayAdaptor.getTaskAdaptor(platform);
        if (adaptor == null) {
            throw new IllegalStateException("task adaptor not found: " + platform);
        }

        for (Task task : tasks) {
            try {
                updateVideoSingleTask(adaptor, channel, task);
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(I18nUtils.get("task.polling_interrupted"), e);
            } catch (Exception e) {
                log.error("Failed to update video task {}: {}", task.getTaskId(), e.getMessage());
            }
        }
    }

    private void updateVideoSingleTask(IAdaptor.ITaskAdaptor adaptor, Channel channel, Task task) throws Exception {
        String baseUrl = channel.getBaseUrl();
        String key = resolveTaskKey(channel, task);
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", getUpstreamTaskId(task));
        body.put("action", task.getAction());

        HttpResponse<?> resp = adaptor.fetchTask(baseUrl, key, body, null);
        byte[] responseBody = responseBodyBytes(resp);
        TaskSnapshot snapshot = TaskSnapshot.of(task);
        TaskInfo taskResult = parseTaskResult(adaptor, responseBody);
        if (taskResult.getStatus() == null || taskResult.getStatus().isEmpty()) {
            taskResult = TaskInfo.fail("upstream returned empty status");
        }

        boolean shouldRefund = false;
        boolean shouldSettle = false;
        int quota = task.getQuota() != null ? task.getQuota() : 0;
        long now = Instant.now().getEpochSecond();

        task.setStatus(taskResult.getStatus());
        switch (taskResult.getStatus()) {
            case TaskConstants.TASK_STATUS_SUBMITTED -> task.setProgress(TaskCommonHelper.PROGRESS_SUBMITTED);
            case TaskConstants.TASK_STATUS_QUEUED -> task.setProgress(TaskCommonHelper.PROGRESS_QUEUED);
            case TaskConstants.TASK_STATUS_IN_PROGRESS -> {
                task.setProgress(TaskCommonHelper.PROGRESS_IN_PROGRESS);
                if (task.getStartTime() == null || task.getStartTime() == 0) task.setStartTime(now);
            }
            case TaskConstants.TASK_STATUS_SUCCESS -> {
                task.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                if (task.getFinishTime() == null || task.getFinishTime() == 0) task.setFinishTime(now);
                applyResultUrl(task, taskResult.getUrl());
                shouldSettle = true;
            }
            case TaskConstants.TASK_STATUS_FAILURE -> {
                task.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                if (task.getFinishTime() == null || task.getFinishTime() == 0) task.setFinishTime(now);
                task.setFailReason(taskResult.getReason());
                shouldRefund = quota != 0;
            }
            default -> throw new IllegalStateException("unknown task status " + taskResult.getStatus() + " for task " + task.getTaskId());
        }
        if (taskResult.getProgress() != null && !taskResult.getProgress().isEmpty()) {
            task.setProgress(taskResult.getProgress());
        }
        task.setData(redactVideoResponseBody(responseBody));

        boolean done = TaskService.isTaskDone(task.getStatus());
        if (done && !Objects.equals(snapshot.getStatus(), task.getStatus())) {
            boolean won = taskService.updateWithStatus(task, snapshot.getStatus());
            if (!won) {
                log.warn("Task {} already transitioned by another process, skip billing", task.getTaskId());
                shouldRefund = false;
                shouldSettle = false;
            }
        } else if (!snapshot.equals(TaskSnapshot.of(task))) {
            taskService.updateWithStatus(task, snapshot.getStatus());
        }

        if (shouldSettle) {
            settleTaskBillingOnComplete(adaptor, task, taskResult);
        }
        if (shouldRefund) {
            taskBillingService.refundTaskQuota(task, task.getFailReason());
        }
    }

    private void settleTaskBillingOnComplete(IAdaptor.ITaskAdaptor adaptor, Task task, TaskInfo taskResult) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        TaskBillingContext billingContext = privateData != null ? privateData.getBillingContext() : null;
        if (billingContext != null && Boolean.TRUE.equals(billingContext.getPerCallBilling())) {
            log.info("任务 {} 按次计费，跳过差额结算", task.getTaskId());
            return;
        }
        int actualQuota = adaptor.adjustBillingOnComplete(task, taskResult);
        if (actualQuota > 0) {
            taskBillingService.recalculateTaskQuota(task, actualQuota, "adaptor计费调整");
            return;
        }
        if (taskResult.getTotalTokens() > 0) {
            taskBillingService.recalculateTaskQuotaByTokens(task, taskResult.getTotalTokens());
        }
    }

    private String getUpstreamTaskId(Task task) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData != null && privateData.getUpstreamTaskId() != null && !privateData.getUpstreamTaskId().isEmpty()) {
            return privateData.getUpstreamTaskId();
        }
        return task.getTaskId();
    }

    private String resolveTaskKey(Channel channel, Task task) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData != null && privateData.getKey() != null && !privateData.getKey().isEmpty()) {
            return privateData.getKey();
        }
        return channel.getKey();
    }

    private TaskPrivateData parsePrivateData(String privateDataJson) {
        if (privateDataJson == null || privateDataJson.isEmpty()) {
            return null;
        }
        try {
            TaskPrivateData privateData = Convert.toJavaBean(privateDataJson, TaskPrivateData.class);
            Map<?, ?> map = Convert.toJSONObject(privateDataJson);
            if (privateData.getUpstreamTaskId() == null && map.get("upstream_task_id") != null) {
                privateData.setUpstreamTaskId(String.valueOf(map.get("upstream_task_id")));
            }
            if (privateData.getResultUrl() == null && map.get("result_url") != null) {
                privateData.setResultUrl(String.valueOf(map.get("result_url")));
            }
            return privateData;
        } catch (Exception e) {
            log.warn("解析任务私有数据失败: {}", e.getMessage());
            return null;
        }
    }

    private TaskInfo parseTaskResult(IAdaptor.ITaskAdaptor adaptor, byte[] responseBody) throws Exception {
        Object result = adaptor.parseTaskResult(responseBody);
        if (result == null) {
            return TaskInfo.fail("upstream returned empty task result");
        }
        if (result instanceof TaskInfo taskInfo) {
            return taskInfo;
        }
        return Convert.toJavaBean(result, TaskInfo.class);
    }

    private byte[] responseBodyBytes(HttpResponse<?> resp) {
        Object body = resp != null ? resp.body() : null;
        if (body instanceof byte[] bytes) return bytes;
        if (body instanceof String text) return text.getBytes(StandardCharsets.UTF_8);
        if (body == null) return new byte[0];
        return Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8);
    }

    private void applyResultUrl(Task task, String url) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        if (privateData == null) {
            privateData = new TaskPrivateData();
        }
        if (url != null && !url.isEmpty() && !url.startsWith("data:")) {
            privateData.setResultUrl(url);
        } else {
            String serverAddress = optionService.getValue("ServerAddress");
            privateData.setResultUrl(TaskCommonHelper.buildProxyURL(serverAddress != null ? serverAddress : "", task.getTaskId()));
        }
        task.setPrivateData(Convert.toJSONString(privateData));
    }

    private String redactVideoResponseBody(byte[] body) {
        // GROUP-11 后续平台适配器会补齐按字段脱敏；当前保留原响应，避免丢失上游轮询信息。
        return new String(body, StandardCharsets.UTF_8);
    }

    @Data
    public static class TaskInfo {
        private String taskId;
        private String status;
        private String url;
        private String progress;
        private String reason;
        private int totalTokens;

        static TaskInfo fail(String reason) {
            TaskInfo info = new TaskInfo();
            info.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            info.setReason(reason);
            info.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
            return info;
        }
    }

    @Data
    private static class TaskSnapshot {
        private String status;
        private String progress;
        private Long startTime;
        private Long finishTime;
        private String failReason;
        private String privateData;
        private String data;

        static TaskSnapshot of(Task task) {
            TaskSnapshot snapshot = new TaskSnapshot();
            snapshot.setStatus(task.getStatus());
            snapshot.setProgress(task.getProgress());
            snapshot.setStartTime(task.getStartTime());
            snapshot.setFinishTime(task.getFinishTime());
            snapshot.setFailReason(task.getFailReason());
            snapshot.setPrivateData(task.getPrivateData());
            snapshot.setData(task.getData());
            return snapshot;
        }
    }
}
