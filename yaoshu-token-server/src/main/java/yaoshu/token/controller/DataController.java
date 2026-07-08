package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.mapper.QuotaMapper;
import yaoshu.token.pojo.entity.QuotaData;

import java.util.List;

/**
 * 数据查询控制器  * <p>
 * 认证：/ 和 /users 用 AdminAuth，/self 用 UserAuth
 * <p>
 * Go 路由：
 * - GET /api/data/      → AdminAuth → GetAllQuotaDates
 * - GET /api/data/users → AdminAuth → GetQuotaDatesByUser
 * - GET /api/data/self  → UserAuth  → GetUserQuotaDates
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final QuotaMapper quotaMapper;

    /**
     * 获取所有配额数据（管理员）      * <p>
     * 若指定 username 则按用户过滤，否则按 model_name + created_at 聚合
     */
    @SaCheckRole("admin")
    @GetMapping({"/", ""})
    public Result<?> getAllQuotaDates(
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp,
            @RequestParam(value = "username", required = false) String username) {
        try {
            List<QuotaData> dates;
            if (username != null && !username.isEmpty()) {
                // Go: GetQuotaDataByUsername
                LambdaQueryWrapper<QuotaData> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(QuotaData::getUsername, username);
                if (startTimestamp != null && startTimestamp > 0) {
                    wrapper.ge(QuotaData::getCreatedAt, startTimestamp);
                }
                if (endTimestamp != null && endTimestamp > 0) {
                    wrapper.le(QuotaData::getCreatedAt, endTimestamp);
                }
                dates = quotaMapper.selectList(wrapper);
            } else {
                // Go: SELECT model_name, sum(count) as count, sum(quota) as quota, sum(token_used) as token_used, created_at
                //     FROM quota_data WHERE ... GROUP BY model_name, created_at
                dates = quotaMapper.getAllQuotaDatesAgg(startTimestamp, endTimestamp);
            }
            return R.success(dates);
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    /**
     * 按用户聚合配额数据（管理员）      * <p>
     * Go: SELECT username, created_at, sum(count) as count, sum(quota) as quota, sum(token_used) as token_used
     *     FROM quota_data WHERE ... GROUP BY username, created_at
     */
    @SaCheckRole("admin")
    @GetMapping({"/users", "/users/"})
    public Result<?> getQuotaDatesByUser(
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp) {
        try {
            List<QuotaData> dates = quotaMapper.getQuotaDataGroupByUser(startTimestamp, endTimestamp);
            return R.success(dates);
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    /**
     * 获取当前用户配额数据      * <p>
     * Go 限制时间跨度不超过 1 个月（2592000 秒）
     */
    @SaCheckLogin
    @GetMapping({"/self", "/self/"})
    public Result<?> getSelfQuotaDates(
            HttpServletRequest request,
            @RequestParam(value = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(value = "end_timestamp", required = false) Long endTimestamp) {
        // Go: 判断时间跨度是否超过 1 个月
        if (startTimestamp != null && endTimestamp != null
                && endTimestamp - startTimestamp > 2592000L) {
            throw new ResultException(R.errorPrompt("时间跨度不能超过 1 个月"));
        }

        try {
            Object userIdAttr = request.getAttribute("id");
            if (userIdAttr == null) {
                throw new ResultException(R.errorPrompt("未登录"));
            }
            int userId = Integer.parseInt(userIdAttr.toString());

            LambdaQueryWrapper<QuotaData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuotaData::getUserId, userId);
            if (startTimestamp != null && startTimestamp > 0) {
                wrapper.ge(QuotaData::getCreatedAt, startTimestamp);
            }
            if (endTimestamp != null && endTimestamp > 0) {
                wrapper.le(QuotaData::getCreatedAt, endTimestamp);
            }
            List<QuotaData> dates = quotaMapper.selectList(wrapper);
            return R.success(dates);
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }
}
