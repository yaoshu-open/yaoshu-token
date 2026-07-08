package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.mapper.TwoFaBackupCodeMapper;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.pojo.entity.TwoFa;
import yaoshu.token.pojo.entity.TwoFaBackupCode;
import yaoshu.token.pojo.ipo.TwoFaIPO;
import yaoshu.token.service.TotpService;
import yaoshu.token.service.TwoFaBackupCodeHelper;
import yaoshu.token.service.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;

/**
 * 双因素认证控制器  * <p>
 * 认证：混合（Setup/Enable/Disable/Status/BackupCodes 用 UserAuth，VerifyLogin 无认证）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TwoFaController {

    private final TwoFaMapper twoFaMapper;
    private final TwoFaBackupCodeMapper twoFaBackupCodeMapper;
    private final UserService userService;
    private final TwoFaBackupCodeHelper twoFaBackupCodeHelper;

    // ======================== 登录 2FA（无认证） ========================

    /**
     * 验证 2FA 登录      * <p>
     * 从 Session 获取待验证用户，用 TOTP 验证码 / 备用码 校验。
     * 中间件：CriticalRateLimit + AnonymousRequestBodyLimit
     */
    @PostMapping("/user/login/2fa")
    public Result<?> verifyLogin(@Valid @RequestBody TwoFaIPO.CodeVerify ipo) {
        // 2FA 验证端点为公开端点（无 SaInterceptor 强制校验），但要求当前会话处于 2fa_pending 半登录态
        if (!StpUtil.isLogin()) {
            throw new ResultException(R.errorPrompt("登录会话已过期，请重新登录"));
        }
        SaSession session = StpUtil.getSession();
        if (!Boolean.TRUE.equals(session.get("2fa_pending"))) {
            throw new ResultException(R.errorPrompt("没有待验证的 2FA 会话"));
        }
        Integer userId = StpUtil.getLoginIdAsInt();

        String code = ipo.getCode();
        if (code == null || code.isBlank()) {
            throw new ResultException(R.errorPrompt("请输入 2FA 验证码"));
        }

        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null || !Boolean.TRUE.equals(twoFa.getIsEnabled())) {
            throw new ResultException(R.errorPrompt("2FA 未启用"));
        }

        // 账号锁定检查
        if (twoFa.getLockedUntil() != null && twoFa.getLockedUntil() > System.currentTimeMillis() / 1000) {
            throw new ResultException(R.errorPrompt("2FA 验证次数过多，账号已锁定，请稍后再试"));
        }

        boolean passed;
        // 备用码格式检测：含短横线或非纯数字 → 备用码校验
        if (code.contains("-") || !code.matches("\\d+")) {
            passed = twoFaBackupCodeHelper.verifyBackupCode(userId, code);
        } else {
            // TOTP 验证码校验
            String cleanedCode = code.replace(" ", "");
            passed = TotpService.validateTOTPCode(twoFa.getSecret(), cleanedCode);
        }

        if (!passed) {
            // 记录失败尝试
            int failedAttempts = (twoFa.getFailedAttempts() != null ? twoFa.getFailedAttempts() : 0) + 1;
            twoFa.setFailedAttempts(failedAttempts);
            if (failedAttempts >= TotpService.MAX_FAIL_ATTEMPTS) {
                twoFa.setLockedUntil(System.currentTimeMillis() / 1000 + TotpService.LOCKOUT_DURATION);
            }
            twoFaMapper.updateById(twoFa);
            throw new ResultException(R.errorPrompt("验证码错误，剩余尝试次数：" + (TotpService.MAX_FAIL_ATTEMPTS - failedAttempts)));
        }

        // 验证通过：重置失败计数 + 清除 pending 标记 + 更新最后登录时间
        twoFa.setFailedAttempts(0);
        twoFa.setLockedUntil(null);
        twoFa.setLastUsedAt(System.currentTimeMillis() / 1000);
        twoFaMapper.updateById(twoFa);

        session.delete("2fa_pending");
        userService.updateLastLoginAt(userId);

        return R.success();
    }

    // ======================== Self 操作（UserAuth） ========================

    @SaCheckLogin
    @GetMapping("/user/2fa/status")
    public Result<?> getStatus(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        boolean enabled = twoFa != null && Boolean.TRUE.equals(twoFa.getIsEnabled());
        boolean locked = twoFa != null
                && twoFa.getLockedUntil() != null
                && twoFa.getLockedUntil() > System.currentTimeMillis() / 1000;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("locked", locked);
        if (enabled) {
            long remaining = twoFaBackupCodeMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TwoFaBackupCode>()
                            .eq(TwoFaBackupCode::getUserId, userId)
                            .eq(TwoFaBackupCode::getIsUsed, false));
            data.put("backup_codes_remaining", remaining);
        }
        return R.success(data);
    }

    @SaCheckLogin
    @PostMapping("/user/2fa/setup")
    public Result<?> setup(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        String secret = TotpService.generateSecret();
        String username = userService.getById(userId, false).getUsername();
        String qrCodeData = TotpService.generateQRCodeData(secret, username);

        // 创建或更新 2FA 记录（未启用状态）
        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null) {
            twoFa = new TwoFa();
            twoFa.setUserId(userId);
            twoFa.setIsEnabled(false);
            twoFa.setFailedAttempts(0);
            twoFa.setCreatedAt(System.currentTimeMillis() / 1000);
            twoFa.setSecret(secret);
            twoFa.setUpdatedAt(System.currentTimeMillis() / 1000);
            twoFaMapper.insert(twoFa);
        } else {
            twoFa.setSecret(secret);
            twoFa.setUpdatedAt(System.currentTimeMillis() / 1000);
            twoFaMapper.updateById(twoFa);
        }

        List<String> backupCodes = regenerateAndSaveBackupCodes(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("secret", secret);
        data.put("qr_code_data", qrCodeData);
        data.put("backup_codes", backupCodes);
        return R.success(data);
    }

    @SaCheckLogin
    @PostMapping("/user/2fa/enable")
    public Result<?> enable(HttpServletRequest request,
                                       @Valid @RequestBody TwoFaIPO.CodeVerify ipo) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        String code = ipo.getCode();
        if (code == null || code.isBlank()) {
            throw new ResultException(R.errorPrompt("请输入验证码"));
        }

        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null || twoFa.getSecret() == null) {
            throw new ResultException(R.errorPrompt("请先设置 2FA"));
        }
        if (Boolean.TRUE.equals(twoFa.getIsEnabled())) {
            throw new ResultException(R.errorPrompt("2FA 已经启用"));
        }

        String cleanedCode = code.replace(" ", "");
        if (!TotpService.validateTOTPCode(twoFa.getSecret(), cleanedCode)) {
            throw new ResultException(R.errorPrompt("验证码错误"));
        }

        // 启用 2FA
        twoFa.setIsEnabled(true);
        twoFa.setUpdatedAt(System.currentTimeMillis() / 1000);
        twoFaMapper.updateById(twoFa);

        // 生成备用码
        regenerateAndSaveBackupCodes(userId);

        return R.success();
    }

    @SaCheckLogin
    @PostMapping("/user/2fa/disable")
    public Result<?> disable(HttpServletRequest request,
                                        @Valid @RequestBody TwoFaIPO.CodeVerify ipo) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        String code = ipo.getCode();
        if (code == null || code.isBlank()) {
            throw new ResultException(R.errorPrompt("请输入验证码"));
        }

        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null || !Boolean.TRUE.equals(twoFa.getIsEnabled())) {
            throw new ResultException(R.errorPrompt("2FA 未启用"));
        }

        String cleanedCode = code.replace(" ", "");
        boolean passed = TotpService.validateTOTPCode(twoFa.getSecret(), cleanedCode);
        if (!passed) {
            // 尝试备用码
            passed = twoFaBackupCodeHelper.verifyBackupCode(userId, code);
        }
        if (!passed) {
            throw new ResultException(R.errorPrompt("验证码错误"));
        }

        twoFaMapper.disableByUserId(userId);
        twoFaBackupCodeMapper.deleteByUserId(userId);

        return R.success();
    }

    @SaCheckLogin
    @PostMapping("/user/2fa/backup_codes")
    public Result<?> regenerateBackupCodes(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null || !Boolean.TRUE.equals(twoFa.getIsEnabled())) {
            throw new ResultException(R.errorPrompt("2FA 未启用"));
        }

        List<String> codes = regenerateAndSaveBackupCodes(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("backup_codes", codes);
        return R.success(data);
    }

    // ======================== 辅助方法 ========================

    /**
     * 重新生成备用码并存入数据库，返回明文列表（仅此一次可见）
     */
    private List<String> regenerateAndSaveBackupCodes(Integer userId) {
        // 删除旧备用码
        twoFaBackupCodeMapper.deleteByUserId(userId);

        List<String> codes = TotpService.generateBackupCodes();
        long now = System.currentTimeMillis() / 1000;
        for (String code : codes) {
            TwoFaBackupCode backupCode = new TwoFaBackupCode();
            backupCode.setUserId(userId);
            backupCode.setCodeHash(TotpService.hashBackupCode(code));
            backupCode.setIsUsed(false);
            backupCode.setCreatedAt(now);
            twoFaBackupCodeMapper.insert(backupCode);
        }
        return codes;
    }

}
