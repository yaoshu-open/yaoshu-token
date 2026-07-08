package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.dto.SyncTaskQueryParams;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.TaskService;
import yaoshu.token.service.UserService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 任务日志控制器  * <p>
 * 路由：
 * <ul>
 *   <li>GET /api/task/      → AdminAuth：全表分页查询</li>
 *   <li>GET /api/task/self  → UserAuth：当前用户分页查询</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskLogController {

    private final TaskService taskService;
    private final UserService userService;

    /** 管理员查询所有任务 */
    @SaCheckRole("admin")
    @GetMapping("/task/")
    public Result<?> getAll(HttpServletRequest request) {
        SyncTaskQueryParams params = buildParams(request, true);

        List<Task> items = taskService.taskGetAllTasks(params);
        // PageHelper 自动 count；管理员视图补全 username
        PageInfo<Task> pageInfo = PageInfo.of(items);
        Map<Integer, String> userIdToName = loadUsernamesByIds(pageInfo.getList());
        List<Map<String, Object>> dto = pageInfo.getList().stream()
                .map(t -> taskToDto(t, userIdToName.get(t.getUserId())))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", dto);
        data.put("total", pageInfo.getTotal());
        return R.success(data);
    }

    /** 用户查询自己的任务 */
    @SaCheckLogin
    @GetMapping("/task/self")
    public Result<?> getSelf(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("id");
        int userId = userIdAttr instanceof Integer i ? i : 0;
        if (userId <= 0) {
            throw new ResultException(R.errorPrompt("invalid user"));
        }

        SyncTaskQueryParams params = buildParams(request, false);

        List<Task> items = taskService.taskGetAllUserTask(userId, params);
        PageInfo<Task> pageInfo = PageInfo.of(items);
        List<Map<String, Object>> dto = pageInfo.getList().stream()
                .map(t -> taskToDto(t, null))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", dto);
        data.put("total", pageInfo.getTotal());
        return R.success(data);
    }

    // ======================== 查询参数解析 ========================

    private SyncTaskQueryParams buildParams(HttpServletRequest request, boolean isAdmin) {
        SyncTaskQueryParams params = new SyncTaskQueryParams();
        params.setPlatform(request.getParameter("platform"));
        params.setTaskId(request.getParameter("task_id"));
        params.setStatus(request.getParameter("status"));
        params.setAction(request.getParameter("action"));
        params.setStartTimestamp(parseLong(request.getParameter("start_timestamp")));
        params.setEndTimestamp(parseLong(request.getParameter("end_timestamp")));
        if (isAdmin) {
            params.setChannelId(request.getParameter("channel_id"));
            params.setUserId(request.getParameter("user_id"));
        }
        return params;
    }

    // ======================== DTO 转换 ========================

    /**
     * 转换 Task 为前端 DTO      * <p>
     * 保留实体所有字段，可选追加 username（管理员视图）。
     */
    private Map<String, Object> taskToDto(Task task, String username) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", task.getId());
        dto.put("created_at", task.getCreatedAt());
        dto.put("updated_at", task.getUpdatedAt());
        dto.put("task_id", task.getTaskId());
        dto.put("platform", task.getPlatform());
        dto.put("user_id", task.getUserId());
        dto.put("group", task.getGroup());
        dto.put("channel_id", task.getChannelId());
        dto.put("quota", task.getQuota());
        dto.put("action", task.getAction());
        dto.put("status", task.getStatus());
        dto.put("fail_reason", task.getFailReason());
        dto.put("submit_time", task.getSubmitTime());
        dto.put("start_time", task.getStartTime());
        dto.put("finish_time", task.getFinishTime());
        dto.put("progress", task.getProgress());
        dto.put("properties", task.getProperties());
        dto.put("data", task.getData());
        if (username != null) {
            dto.put("username", username);
        }
        return dto;
    }

    private Map<Integer, String> loadUsernamesByIds(List<Task> tasks) {
        Set<Integer> userIds = new HashSet<>();
        for (Task t : tasks) {
            if (t.getUserId() != null && t.getUserId() > 0) userIds.add(t.getUserId());
        }
        Map<Integer, String> map = new HashMap<>();
        for (Integer uid : userIds) {
            User u = userService.getById(uid, false);
            if (u != null) map.put(uid, u.getUsername());
        }
        return map;
    }

    // ======================== 通用辅助 ========================

    private static Long parseLong(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
