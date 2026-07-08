package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.handler.TaskRelayService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务中转控制器（Suno/Task）  * <p>
 * 认证：TokenAuth（全部）+ Distribute
 */
@RestController
@RequiredArgsConstructor
public class TaskRelayController {

    private final TaskRelayService taskRelayService;

    /** Suno 提交 */
    @PostMapping("/suno/submit/{action}")
    public Map<String, Object> sunoSubmit(@PathVariable String action,
                                           @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        RelayInfo info = buildTaskRelayInfo(request, body);
        info.setTaskAction(action);
        info.setOriginModelName(resolveModel(body, "suno_music"));
        request.setAttribute("platform", "suno");
        return submit(request, info);
    }

    /** Suno 批量查询 */
    @PostMapping("/suno/fetch")
    @SuppressWarnings("unchecked")
    public Map<String, Object> sunoFetch(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        RelayInfo info = buildTaskRelayInfo(request, body);
        Object idsObj = body.get("ids");
        List<String> ids = idsObj instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        return taskRelayService.sunoFetch(request, info, ids);
    }

    /** Suno 单任务查询 */
    @GetMapping("/suno/fetch/{id}")
    public Map<String, Object> sunoFetchById(@PathVariable String id, HttpServletRequest request) {
        RelayInfo info = buildTaskRelayInfo(request, Map.of());
        return taskRelayService.sunoFetchById(request, info, id);
    }

    private Map<String, Object> submit(HttpServletRequest request, RelayInfo info) {
        try {
            TaskRelayService.TaskSubmitResult result = taskRelayService.relayTask(request, info);
            return Map.of("code", "success", "data", result.getPublicTaskID());
        } catch (TaskRelayService.TaskRelayException e) {
            return e.toResponseBody();
        }
    }

    private RelayInfo buildTaskRelayInfo(HttpServletRequest request, Map<String, Object> body) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());
        info.setRequestURLPath(request.getRequestURI());
        info.setRequest(body);
        info.setUserId(toInt(request.getAttribute(ContextKeyConstants.USER_ID)));
        info.setTokenId(toInt(request.getAttribute(ContextKeyConstants.TOKEN_ID)));
        info.setTokenGroup(stringAttr(request, ContextKeyConstants.TOKEN_GROUP));
        info.setUserGroup(stringAttr(request, ContextKeyConstants.USER_GROUP));
        info.setUsingGroup(stringAttr(request, ContextKeyConstants.USING_GROUP));
        info.setOriginModelName(resolveModel(body, stringAttr(request, ContextKeyConstants.ORIGINAL_MODEL)));
        info.setUpstreamModelName(info.getOriginModelName());
        return info;
    }

    private String resolveModel(Map<String, Object> body, String fallback) {
        Object model = body != null ? body.get("model") : null;
        return model != null && !String.valueOf(model).isEmpty() ? String.valueOf(model) : fallback;
    }

    private String stringAttr(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value != null ? String.valueOf(value) : null;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }
}
