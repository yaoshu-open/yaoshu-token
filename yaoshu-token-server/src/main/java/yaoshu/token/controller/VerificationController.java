package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.middleware.SecureVerificationFilter;
import yaoshu.token.pojo.entity.PasskeyCredential;
import yaoshu.token.pojo.entity.TwoFa;
import yaoshu.token.pojo.ipo.VerificationIPO;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.service.EmailService;
import yaoshu.token.service.TotpService;
import yaoshu.token.service.TwoFaBackupCodeHelper;
import yaoshu.token.service.VerificationService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.passkey.PasskeySessionService;
import yaoshu.token.pojo.entity.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 验证控制器  * <p>
 * 认证：混合（SendEmail 无认证，UniversalVerify UserAuth）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VerificationController {

    private final UserService userService;
    private final TwoFaMapper twoFaMapper;
    private final TwoFaBackupCodeHelper twoFaBackupCodeHelper;
    private final PasskeyMapper passkeyMapper;

    // ======================== 邮箱验证码 ========================

    /**
     * 发送邮箱验证码      * <p>
     * 中间件：EmailVerificationRateLimit + TurnstileCheck
     */
    @GetMapping("/verification")
    public Result<?> sendEmail(@RequestParam String email,
                                          @RequestParam(defaultValue = "") String turnstile) {
        if (email == null || !email.contains("@")) {
            throw new ResultException(R.errorPrompt("无效的邮箱地址"));
        }

        // 生成 6 位验证码
        String code = VerificationService.generateVerificationCode(6);
        VerificationService.registerCode(email, code, VerificationService.EMAIL_VERIFICATION_PURPOSE);

        // 发送邮件（品牌化模板，标题不含收件人邮箱避免隐私泄露）
        String subject = "【" + CommonConstants.systemName + "】验证码";
        String content = EmailService.buildVerificationCodeEmail(code, VerificationService.verificationValidMinutes);
        boolean sent = EmailService.sendEmail(subject, email, content);

        if (!sent) {
            throw new ResultException(R.errorPrompt("验证码发送失败，请联系管理员"));
        }
        return R.success();
    }

    // ======================== 密码重置邮件 ========================

    /**
     * 发送密码重置邮件      * <p>
     * 中间件：CriticalRateLimit + TurnstileCheck
     */
    @GetMapping("/reset_password")
    public Result<?> sendResetEmail(@RequestParam String email,
                                               @RequestParam(defaultValue = "") String turnstile) {
        if (email == null || !email.contains("@")) {
            throw new ResultException(R.errorPrompt("无效的邮箱地址"));
        }

        // 查找用户（findByUsernameOrEmail 同时匹配 username 和 email）
        User user = userService.findByUsernameOrEmail(email);
        if (user == null || !email.equalsIgnoreCase(user.getEmail())) {
            // 安全考虑：不暴露用户是否存在，统一返回"已发送"
            return R.success();
        }

        // 生成重置 token（6 位验证码）
        String token = VerificationService.generateVerificationCode(6);
        VerificationService.registerCode(email, token, VerificationService.PASSWORD_RESET_PURPOSE);

        // 发送重置邮件（品牌化模板）
        String subject = "【" + CommonConstants.systemName + "】密码重置验证码";
        String content = EmailService.buildPasswordResetEmail(user.getUsername(), token,
                VerificationService.verificationValidMinutes);
        EmailService.sendEmail(subject, email, content);

        return R.success();
    }

    // ======================== 通用验证（2FA/Passkey） ========================

    /**
     * 通用验证接口      * <p>
     * 验证成功后在 session 写入安全验证时间戳 secure_verified_at + 方式，供敏感操作放行。
     * 中间件：CriticalRateLimit
     */
    @PostMapping("/verify")
    public Result<?> universalVerify(HttpServletRequest request,
                                                @Valid @RequestBody VerificationIPO.UniversalVerify ipo) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) {
            throw new ResultException(R.errorPrompt("未登录"));
        }

        String method = ipo.getMethod();
        String code = ipo.getCode();

        if (method == null) {
            throw new ResultException(R.errorPrompt("参数缺失：method 为必填"));
        }

        // 检查用户启用的验证方式
        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        boolean has2FA = twoFa != null && Boolean.TRUE.equals(twoFa.getIsEnabled());
        PasskeyCredential passkey = passkeyMapper.selectByUserId(userId);
        boolean hasPasskey = passkey != null;

        if (!has2FA && !hasPasskey) {
            throw new ResultException(R.errorPrompt("用户未启用2FA或Passkey"));
        }

        boolean verified;
        if ("2fa".equals(method)) {
            if (!has2FA) {
                throw new ResultException(R.errorPrompt("用户未启用2FA"));
            }
            if (code == null || code.isBlank()) {
                throw new ResultException(R.errorPrompt("验证码不能为空"));
            }
            verified = TotpService.validateTOTPCode(twoFa.getSecret(), code.replace(" ", ""));
            if (!verified) {
                verified = twoFaBackupCodeHelper.verifyBackupCode(userId, code);
            }
            if (!verified) {
                throw new ResultException(R.errorPrompt("验证失败，请检查验证码或备用码"));
            }
        } else if ("passkey".equals(method)) {
            if (!hasPasskey) {
                throw new ResultException(R.errorPrompt("用户未启用Passkey"));
            }
            // Passkey 分支只信任 verifyFinish 写入的短期 ready 标记
            verified = consumePasskeyReady(request);
            if (!verified) {
                throw new ResultException(R.errorPrompt("请先完成 Passkey 验证"));
            }
        } else {
            throw new ResultException(R.errorPrompt("不支持的验证方式：" + method));
        }

        // 验证成功：写入安全验证 session
        long now = setSecureVerificationSession(request, method);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verified", true);
        data.put("expires_at", now + 300);
        return R.success(data);
    }

    // ======================== 辅助方法 ========================

    /**
     * 写入安全验证 session 时间戳。      * 清除 Passkey ready 标记，写入 secure_verified_at + secure_verified_method。
     */
    private long setSecureVerificationSession(HttpServletRequest request, String method) {
        HttpSession session = request.getSession(true);
        session.removeAttribute(PasskeySessionService.PASSKEY_READY_KEY);
        long now = System.currentTimeMillis() / 1000;
        session.setAttribute(SecureVerificationFilter.SECURE_VERIFIED_AT, now);
        session.setAttribute(SecureVerificationFilter.SECURE_VERIFIED_METHOD, method);
        return now;
    }

    /**
     * 消费 Passkey 验证完成标记。      * 一次性消费（读取后立即删除），标记超过 60 秒视为过期失效。
     */
    private boolean consumePasskeyReady(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object readyAtRaw = session.getAttribute(PasskeySessionService.PASSKEY_READY_KEY);
        // 无论成功与否都消费掉标记，防止重放
        session.removeAttribute(PasskeySessionService.PASSKEY_READY_KEY);
        if (readyAtRaw == null) {
            return false;
        }
        long readyAt;
        if (readyAtRaw instanceof Long l) {
            readyAt = l;
        } else if (readyAtRaw instanceof Integer i) {
            readyAt = i.longValue();
        } else {
            return false;
        }
        // 过期的 ready 标记不可复用
        return System.currentTimeMillis() / 1000 - readyAt < PasskeySessionService.PASSKEY_READY_TIMEOUT;
    }

}
