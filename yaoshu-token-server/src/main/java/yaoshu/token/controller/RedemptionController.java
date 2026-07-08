package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Redemption;
import yaoshu.token.pojo.ipo.RedemptionIPO;
import yaoshu.token.service.RedemptionService;
import yaoshu.token.service.TopupService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 兑换码管理控制器  * <p>
 * 认证：AdminAuth（全部）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/redemption")
@RequiredArgsConstructor
public class RedemptionController {

    private final RedemptionService redemptionService;
    private final TopupService topupService;

    @GetMapping("/")
    public Result<?> getAll(HttpServletRequest request) {
        List<Redemption> list = redemptionService.getAll();
        return R.success(PageInfo.of(list));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                                      HttpServletRequest request) {
        List<Redemption> list = redemptionService.search(keyword);
        return R.success(PageInfo.of(list));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id) {
        Redemption r = redemptionService.getById(id);
        if (r == null) throw new ResultException(R.errorPrompt("兑换码不存在"));
        return R.success(r);
    }

    @PostMapping("/")
    public Result<?> add(HttpServletRequest request, @Valid @RequestBody RedemptionIPO.Create ipo) {
        // ① 支付合规已确认
        if (!topupService.isPaymentComplianceConfirmed()) {
            throw new ResultException(R.errorPrompt("请先在系统设置中确认支付合规声明"));
        }
        Integer userId = (Integer) request.getAttribute("id");
        Redemption r = new Redemption();
        r.setName(trimToNull(ipo.getName()));
        r.setQuota(ipo.getQuota() != null ? ipo.getQuota() : 0);
        r.setCount(ipo.getCount() != null ? ipo.getCount() : 1);
        if (ipo.getExpiredTime() != null) r.setExpiredTime(ipo.getExpiredTime());

        // ② name 长度 1-20（按 Unicode 码点数计算，与 Go utf8.RuneCountInString 一致）
        if (r.getName() == null) {
            throw new ResultException(R.errorPrompt("兑换码名称不能为空"));
        }
        int nameLen = r.getName().codePointCount(0, r.getName().length());
        if (nameLen == 0 || nameLen > 20) {
            throw new ResultException(R.errorPrompt("兑换码名称长度必须为 1-20 个字符"));
        }
        // ③ count 1-100
        if (r.getCount() == null || r.getCount() <= 0) {
            throw new ResultException(R.errorPrompt("兑换码数量必须大于 0"));
        }
        if (r.getCount() > 100) {
            throw new ResultException(R.errorPrompt("兑换码数量不能超过 100"));
        }
        // ④ expiredTime 不能小于当前时间（0 表示永不过期，允许）
        if (r.getExpiredTime() != null && r.getExpiredTime() != 0
                && r.getExpiredTime() < System.currentTimeMillis() / 1000) {
            throw new ResultException(R.errorPrompt("过期时间不能早于当前时间"));
        }

        List<String> keys = redemptionService.add(userId == null ? 1 : userId, r);
        return R.success(keys);
    }

    @PutMapping("/")
    public Result<?> update(@Valid @RequestBody RedemptionIPO.Update ipo,
                                      HttpServletRequest request) {
        Integer id = ipo.getId();
        if (id == null || id == 0) throw new ResultException(R.errorPrompt("无效的参数"));
        boolean statusOnly = "true".equals(trimToEmpty(request.getParameter("status_only")));
        Redemption r = new Redemption();
        r.setId(id);
        r.setStatus(ipo.getStatus());
        if (!statusOnly) {
            r.setName(trimToNull(ipo.getName()));
            r.setQuota(ipo.getQuota());
            if (ipo.getExpiredTime() != null) r.setExpiredTime(ipo.getExpiredTime());
        }
        return R.success(redemptionService.update(r, statusOnly));
    }

    @DeleteMapping("/invalid")
    public Result<?> deleteInvalid() {
        long count = redemptionService.deleteInvalid();
        return R.success(count);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        redemptionService.delete(id);
        return R.success();
    }

    // ======================== 辅助方法 ========================




    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}


