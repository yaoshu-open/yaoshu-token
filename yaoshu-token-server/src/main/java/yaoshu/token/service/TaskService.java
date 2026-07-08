package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.mapper.TaskMapper;
import yaoshu.token.pojo.dto.SyncTaskQueryParams;
import yaoshu.token.pojo.entity.Task;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务服务  * <p>
 * 核心方法：任务记录 CRUD、任务状态查询、任务结果获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;

    // ======================== 静态辅助方法（无 DB 依赖） ========================

    /**
     * 构建任务提交响应      */
    public static Map<String, Object> buildTaskSubmitResponse(String taskId, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", taskId);
        response.put("status", status);
        return response;
    }

    /**
     * 构建任务查询响应      */
    public static Map<String, Object> buildTaskFetchResponse(String taskId, String status,
                                                              String videoUrl, String error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", taskId);
        response.put("status", status);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            response.put("video_url", videoUrl);
        }
        if (error != null && !error.isEmpty()) {
            response.put("error", error);
        }
        return response;
    }

    /**
     * 检查任务是否完成（成功或失败）
     */
    public static boolean isTaskDone(String status) {
        return TaskConstants.TASK_STATUS_SUCCESS.equals(status) || TaskConstants.TASK_STATUS_FAILURE.equals(status);
    }

    /**
     * 生成对外暴露的 task_xxxx 格式 ID      */
    public static String generateTaskID() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(32);
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return "task_" + sb;
    }

    // ======================== CRUD 方法 ======================== 
    /**
     * 创建任务      */
    public boolean insert(Task task) {
        long now = Instant.now().getEpochSecond();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return taskMapper.insert(task) > 0;
    }

    /**
     * 更新任务      */
    public boolean update(Task task) {
        task.setUpdatedAt(Instant.now().getEpochSecond());
        return taskMapper.updateById(task) > 0;
    }

    /**
     * CAS 条件更新      * <p>
     * 仅当当前 status 等于 fromStatus 时才执行更新，防止并发覆盖。
     * 用于计费/退款的终态转换（success/failure）。
     *
     * @return true 表示当前调用赢得了更新
     */
    public boolean updateWithStatus(Task task, String fromStatus) {
        task.setUpdatedAt(Instant.now().getEpochSecond());
        LambdaUpdateWrapper<Task> wrapper = Wrappers.<Task>lambdaUpdate(Task.class)
                .eq(Task::getId, task.getId())
                .eq(Task::getStatus, fromStatus);
        return taskMapper.update(task, wrapper) > 0;
    }

    /**
     * 按上游 task_id 批量无条件更新      * <p>
     * WARNING: 无 CAS 保护，不用于计费/退款的终态转换。
     */
    public boolean taskBulkUpdate(List<String> taskIds, Map<String, Object> params) {
        if (taskIds == null || taskIds.isEmpty()) {
            return true;
        }
        LambdaUpdateWrapper<Task> wrapper = Wrappers.<Task>lambdaUpdate(Task.class)
                .in(Task::getTaskId, taskIds);
        applyUpdateParams(wrapper, params);
        return taskMapper.update(null, wrapper) > 0;
    }

    /**
     * 按主键 ID 批量无条件更新      * <p>
     * WARNING: 无 CAS 保护，不用于计费/退款的终态转换。
     */
    public boolean taskBulkUpdateByID(List<Long> ids, Map<String, Object> params) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        LambdaUpdateWrapper<Task> wrapper = Wrappers.<Task>lambdaUpdate(Task.class)
                .in(Task::getId, ids);
        applyUpdateParams(wrapper, params);
        return taskMapper.update(null, wrapper) > 0;
    }

    /**
     * 按 task_id 查询任务（不限用户）      *
     * @return 任务对象，不存在返回 null
     */
    public Task getByOnlyTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        return taskMapper.selectOne(
                Wrappers.<Task>lambdaQuery(Task.class)
                        .eq(Task::getTaskId, taskId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 按 user_id + task_id 查询任务      */
    public Task getByTaskId(int userId, String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        return taskMapper.selectOne(
                Wrappers.<Task>lambdaQuery(Task.class)
                        .eq(Task::getUserId, userId)
                        .eq(Task::getTaskId, taskId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 按 user_id + 多个 task_id 批量查询      */
    public List<Task> getByTaskIds(int userId, List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery(Task.class)
                        .eq(Task::getUserId, userId)
                        .in(Task::getTaskId, taskIds)
        );
    }

    /**
     * 获取所有未完成的同步任务      * <p>
     * 条件：progress != '100%' AND status != FAILURE AND status != SUCCESS
     */
    public List<Task> getAllUnFinishSyncTasks(int limit) {
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery(Task.class)
                        .ne(Task::getProgress, "100%")
                        .ne(Task::getStatus, TaskConstants.TASK_STATUS_FAILURE)
                        .ne(Task::getStatus, TaskConstants.TASK_STATUS_SUCCESS)
                        .orderByAsc(Task::getId)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 获取超时未完成的任务      * <p>
     * 条件：progress != '100%' AND status NOT IN (FAILURE, SUCCESS) AND submit_time < cutoff
     */
    public List<Task> getTimedOutUnfinishedTasks(long cutoffUnix, int limit) {
        return taskMapper.selectList(
                Wrappers.<Task>lambdaQuery(Task.class)
                        .ne(Task::getProgress, "100%")
                        .notIn(Task::getStatus, TaskConstants.TASK_STATUS_FAILURE, TaskConstants.TASK_STATUS_SUCCESS)
                        .lt(Task::getSubmitTime, cutoffUnix)
                        .orderByAsc(Task::getSubmitTime)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 分页查询用户任务      */
    public List<Task> taskGetAllUserTask(int userId, SyncTaskQueryParams queryParams) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Task> query = Wrappers.<Task>lambdaQuery(Task.class)
                .eq(Task::getUserId, userId);
        applyQueryFilters(query, queryParams, false);
        query.orderByDesc(Task::getId);
        return taskMapper.selectList(query);
    }

    /**
     * 管理员分页查询所有任务      */
    public List<Task> taskGetAllTasks(SyncTaskQueryParams queryParams) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Task> query = Wrappers.<Task>lambdaQuery(Task.class);
        applyQueryFilters(query, queryParams, true);
        query.orderByDesc(Task::getId);
        return taskMapper.selectList(query);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 应用查询过滤条件      *
     * @param isAdminQuery true=管理员查询（支持 channelId/userId/userIds 过滤），false=用户查询
     */
    private void applyQueryFilters(LambdaQueryWrapper<Task> query, SyncTaskQueryParams params, boolean isAdminQuery) {
        if (params == null) {
            return;
        }
        if (params.getTaskId() != null && !params.getTaskId().isEmpty()) {
            query.eq(Task::getTaskId, params.getTaskId());
        }
        if (params.getAction() != null && !params.getAction().isEmpty()) {
            query.eq(Task::getAction, params.getAction());
        }
        if (params.getStatus() != null && !params.getStatus().isEmpty()) {
            query.eq(Task::getStatus, params.getStatus());
        }
        if (params.getPlatform() != null && !params.getPlatform().isEmpty()) {
            query.eq(Task::getPlatform, params.getPlatform());
        }
        if (params.getStartTimestamp() != null && params.getStartTimestamp() != 0) {
            query.ge(Task::getSubmitTime, params.getStartTimestamp());
        }
        if (params.getEndTimestamp() != null && params.getEndTimestamp() != 0) {
            query.le(Task::getSubmitTime, params.getEndTimestamp());
        }
        // 管理员独有过滤条件
        if (isAdminQuery) {
            if (params.getChannelId() != null && !params.getChannelId().isEmpty()) {
                query.eq(Task::getChannelId, Integer.parseInt(params.getChannelId()));
            }
            if (params.getUserId() != null && !params.getUserId().isEmpty()) {
                query.eq(Task::getUserId, Integer.parseInt(params.getUserId()));
            }
            if (params.getUserIds() != null && !params.getUserIds().isEmpty()) {
                query.in(Task::getUserId, params.getUserIds());
            }
        }
    }

    /**
     * 将 Map 参数应用到 UpdateWrapper      */
    @SuppressWarnings("unchecked")
    private void applyUpdateParams(LambdaUpdateWrapper<Task> wrapper, Map<String, Object> params) {
        if (params == null) {
            return;
        }
        params.forEach((key, value) -> {
            switch (key) {
                case "status" -> wrapper.set(Task::getStatus, (String) value);
                case "progress" -> wrapper.set(Task::getProgress, (String) value);
                case "fail_reason" -> wrapper.set(Task::getFailReason, (String) value);
                case "start_time" -> wrapper.set(Task::getStartTime, ((Number) value).longValue());
                case "finish_time" -> wrapper.set(Task::getFinishTime, ((Number) value).longValue());
                case "quota" -> wrapper.set(Task::getQuota, ((Number) value).intValue());
                case "data" -> wrapper.set(Task::getData, value instanceof String ? (String) value : value.toString());
                default -> log.debug("taskBulkUpdate: 未识别的更新字段 {}", key);
            }
        });
    }
}
