package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import ai.yue.library.web.util.ServletUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;

import java.util.List;

/**
 * 用户充值控制器  * <p>
 * 认证：混合（getInfo/getSelf/topup 用 UserAuth，admin getAll/complete 用 AdminAuth）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class TopupController {

    private final TopupService topupService;
    private final UserService userService;

    /** 获取充值信息 */
    @GetMapping("/topup/info")
    public Result<?> getInfo(HttpServletRequest request) {
        Integer userId = getUserId(request);
        String group = userService.getUserGroup(userId);
        return R.success(topupService.getTopupInfo(group));
    }

    /** Self 查看充值记录 */
    @GetMapping("/topup/self")
    public Result<?> getSelf(HttpServletRequest request) {
        Integer userId = getUserId(request);
        String keyword = request.getParameter("keyword");
        PageHelper.startPage(ServletUtils.getRequest());
        List<TopUp> list = topupService.listUserTopups(userId, keyword);
        return R.success(PageInfo.of(list));
    }

    /** 提交充值 */
    @PostMapping("/topup")
    public Result<?> topup(HttpServletRequest request, @Valid @RequestBody TopupRequest body) {
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先确认合规声明"));
        }
        Integer userId = getUserId(request);
        int quota = topupService.redeem(body.getKey(), userId);
        return R.success(quota);
    }

    private Integer getUserId(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null || userId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.not_logged_in"));
        }
        return userId;
    }

    @Data
    public static class TopupRequest {
        @NotBlank(message = "key 不能为空")
        private String key;
    }
}
