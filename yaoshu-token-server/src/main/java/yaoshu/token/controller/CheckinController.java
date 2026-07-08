package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.config.operation.CheckinSettingConfig;
import yaoshu.token.service.CheckinService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 签到控制器  * <p>
 * 认证：UserAuth（由 AuthFilter 设置 request attribute "id"）
 * POST 额外：TurnstileCheck（由 TurnstileCheckFilter 处理）
 */
@RestController
@SaCheckLogin
@RequestMapping("/api/user/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 获取用户签到状态和历史记录      */
    @GetMapping
    public Result<?> getStatus(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new RuntimeException(I18nUtils.get("common.not_logged_in"));
        }

        // 从配置（options 表 checkin_setting）读取签到设置
        CheckinSettingConfig setting = checkinService.getSetting();

        if (!setting.isEnabled()) {
            throw new ResultException(R.errorPrompt("签到功能未启用"));
        }

        // 获取月份参数，默认为当前月份
        String month = request.getParameter("month");
        if (month == null || month.isEmpty()) {
            month = LocalDate.now().format(MONTH_FMT);
        }

        Map<String, Object> stats = checkinService.getUserCheckinStats(userId, month);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", setting.isEnabled());
        data.put("min_quota", setting.getMinQuota());
        data.put("max_quota", setting.getMaxQuota());
        data.put("stats", stats);

        return R.success(data);
    }

    /**
     * 执行签到      */
    @PostMapping
    public Result<?> doCheckin(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new RuntimeException(I18nUtils.get("common.not_logged_in"));
        }

        try {
            Map<String, Object> checkinData = checkinService.doCheckin(userId);
            return R.success(checkinData);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }
}
