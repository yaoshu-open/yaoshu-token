package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.entity.Midjourney;
import yaoshu.token.service.MidjourneyService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SystemService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * Midjourney 日志控制器  * <p>
 * 路由：
 * <ul>
 *   <li>GET /api/mj/      → AdminAuth：全表分页查询</li>
 *   <li>GET /api/mj/self  → UserAuth：当前用户分页查询</li>
 * </ul>
 * 当 setting.MjForwardUrlEnabled=true 时，将 image_url 重写为本站 /mj/image/:mjId 代理路径。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MjLogController {

    private final MidjourneyService midjourneyService;
    private final SystemService systemService;
    private final OptionService optionService;

    /** 管理员查询所有 MJ 任务 */
    @SaCheckRole("admin")
    @GetMapping("/mj/")
    public Result<?> getAll(HttpServletRequest request) {
        Integer channelId = parseInt(request.getParameter("channel_id"));
        String mjId = request.getParameter("mj_id");
        Long startTs = parseLong(request.getParameter("start_timestamp"));
        Long endTs = parseLong(request.getParameter("end_timestamp"));

        List<Midjourney> items = midjourneyService.getAllTasks(channelId, mjId, startTs, endTs);
        // PageHelper 自动 count，PageInfo 携带 total；图片 URL 重写原地修改 list
        PageInfo<Midjourney> pageInfo = PageInfo.of(items);
        rewriteImageUrlIfEnabled(pageInfo.getList());
        return R.success(pageInfo);
    }

    /** 用户查询自己的 MJ 任务 */
    @SaCheckLogin
    @GetMapping("/mj/self")
    public Result<?> getSelf(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("id");
        Integer userId = userIdAttr instanceof Integer i ? i : null;
        if (userId == null || userId <= 0) {
            throw new ResultException(R.errorPrompt("invalid user"));
        }

        String mjId = request.getParameter("mj_id");
        Long startTs = parseLong(request.getParameter("start_timestamp"));
        Long endTs = parseLong(request.getParameter("end_timestamp"));

        List<Midjourney> items = midjourneyService.getAllUserTask(userId, mjId, startTs, endTs);
        PageInfo<Midjourney> pageInfo = PageInfo.of(items);
        rewriteImageUrlIfEnabled(pageInfo.getList());
        return R.success(pageInfo);
    }

    // ======================== ImageUrl 重写 ========================

    private void rewriteImageUrlIfEnabled(List<Midjourney> items) {
        if (items == null || items.isEmpty()) return;
        if (!systemService.isMjForwardEnabled()) return;
        String serverAddress = optionService.getValue("ServerAddress");
        if (serverAddress == null) serverAddress = "";
        // 移除末尾斜杠避免拼接出双 //
        if (serverAddress.endsWith("/")) {
            serverAddress = serverAddress.substring(0, serverAddress.length() - 1);
        }
        for (Midjourney m : items) {
            if (m.getMjId() != null && !m.getMjId().isEmpty()) {
                m.setImageUrl(serverAddress + "/mj/image/" + m.getMjId());
            }
        }
    }

    // ======================== 通用辅助 ========================

    private static Long parseLong(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
