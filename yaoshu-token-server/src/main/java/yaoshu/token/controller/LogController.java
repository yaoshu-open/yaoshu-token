package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.service.ChannelAffinityService;
import yaoshu.token.service.LogManagementService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 日志控制器  * <p>
 * 认证：混合（getAll/delete/getStat/searchAll 用 AdminAuth，getSelf/searchUserLogs/getSelfStat 用 UserAuth，getByKey 用 TokenAuthReadOnly）
 */
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {

    private final LogManagementService logManagementService;

    @SaCheckRole("admin")
    @GetMapping("/")
    public Result<?> getAll(HttpServletRequest request) {
        List<Log> logs = logManagementService.getAllLogs(
                toInt(request.getParameter("type")),
                toLong(request.getParameter("start_timestamp")),
                toLong(request.getParameter("end_timestamp")),
                trimToNull(request.getParameter("model_name")),
                trimToNull(request.getParameter("username")),
                trimToNull(request.getParameter("token_name")),
                toInt(request.getParameter("channel")),
                trimToNull(request.getParameter("group")),
                trimToNull(request.getParameter("request_id")),
                trimToNull(request.getParameter("upstream_request_id")));
        return R.success(PageInfo.of(logs));
    }

    @SaCheckRole("admin")
    @DeleteMapping("/")
    public Result<?> deleteHistory(@RequestParam("target_timestamp") long targetTimestamp) {
        long count = logManagementService.deleteOldLogs(targetTimestamp);
        return R.success(count);
    }

    @SaCheckRole("admin")
    @GetMapping("/stat")
    public Result<?> getStat(HttpServletRequest request) {
        LogManagementService.StatResult stat = logManagementService.sumUsedQuota(
                toInt(request.getParameter("type")),
                toLong(request.getParameter("start_timestamp")),
                toLong(request.getParameter("end_timestamp")),
                trimToNull(request.getParameter("model_name")),
                trimToNull(request.getParameter("username")),
                trimToNull(request.getParameter("token_name")),
                toInt(request.getParameter("channel")),
                trimToNull(request.getParameter("group")));
        return R.success(Map.of("quota", stat.quota(), "rpm", stat.rpm(), "tpm", stat.tpm()));
    }

    @SaCheckLogin
    @GetMapping("/self/stat")
    public Result<?> getSelfStat(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        LogManagementService.StatResult stat = logManagementService.sumUsedQuota(
                toInt(request.getParameter("type")),
                toLong(request.getParameter("start_timestamp")),
                toLong(request.getParameter("end_timestamp")),
                trimToNull(request.getParameter("model_name")),
                username,
                trimToNull(request.getParameter("token_name")),
                toInt(request.getParameter("channel")),
                trimToNull(request.getParameter("group")));
        return R.success(Map.of("quota", stat.quota(), "rpm", stat.rpm(), "tpm", stat.tpm()));
    }

    @SaCheckRole("admin")
    @GetMapping("/channel_affinity_usage_cache")
    public Result<?> getChannelAffinityCache(
            @RequestParam("rule_name") String ruleName,
            @RequestParam(value = "using_group", required = false) String usingGroup,
            @RequestParam("key_fp") String keyFp) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new ResultException(R.errorPrompt("missing param: rule_name"));
        }
        if (keyFp == null || keyFp.isBlank()) {
            throw new ResultException(R.errorPrompt("missing param: key_fp"));
        }
        ChannelAffinityService.UsageCacheStats stats = ChannelAffinityService.getUsageCacheStats(
                ruleName.trim(),
                usingGroup != null ? usingGroup.trim() : "",
                keyFp.trim());
        return R.success(stats);
    }

    @SaCheckRole("admin")
    @GetMapping("/search")
    public Result<?> searchAll(@RequestParam(required = false) String keyword,
                                         HttpServletRequest request) {
        // Go 中 SearchAllLogs 已废弃
        throw new ResultException(R.errorPrompt(I18nUtils.get("admin.interface_deprecated")));
    }

    @SaCheckLogin
    @GetMapping({"/self", "/self/"})
    public Result<?> getUserLogs(HttpServletRequest request) {
        Integer userId = toInt(request.getAttribute("id"));
        List<Log> logs = logManagementService.getUserLogs(
                userId == null ? 0 : userId,
                toInt(request.getParameter("type")),
                toLong(request.getParameter("start_timestamp")),
                toLong(request.getParameter("end_timestamp")),
                trimToNull(request.getParameter("model_name")),
                trimToNull(request.getParameter("token_name")),
                trimToNull(request.getParameter("group")),
                trimToNull(request.getParameter("request_id")),
                trimToNull(request.getParameter("upstream_request_id")));
        return R.success(PageInfo.of(logs));
    }

    @SaCheckLogin
    @GetMapping("/self/search")
    public Result<?> searchUserLogs(HttpServletRequest request, @RequestParam(required = false) String keyword) {
        // Go 中 SearchUserLogs 已废弃
        throw new ResultException(R.errorPrompt(I18nUtils.get("admin.interface_deprecated")));
    }

    @GetMapping("/token")
    public Result<?> getByKey(HttpServletRequest request) {
        Integer tokenId = toInt(request.getAttribute("token_id"));
        if (tokenId == null || tokenId == 0) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.invalid_token")));
        }
        List<Log> logs = logManagementService.getByTokenId(tokenId);
        return R.success(logs);
    }

    // ======================== 辅助方法 ========================



    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


