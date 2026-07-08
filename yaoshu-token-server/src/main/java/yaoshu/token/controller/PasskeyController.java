package yaoshu.token.controller;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.data.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.config.SystemSettingConfig;
import yaoshu.token.middleware.SecureVerificationFilter;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.PasskeyCredential;
import yaoshu.token.pojo.entity.TwoFa;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.passkey.PasskeyService;
import yaoshu.token.service.passkey.PasskeySessionService;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Passkey 控制器  * <p>
 * 8 端点：loginBegin/loginFinish、registerBegin/registerFinish、verifyBegin/verifyFinish、status、delete
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final PasskeyMapper passkeyMapper;
    private final TwoFaMapper twoFaMapper;
    private final UserMapper userMapper;

    // ======================== 登录（无认证） ========================

    @PostMapping("/user/passkey/login/begin")
    public Result<?> loginBegin(HttpServletRequest request) {
        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) {
            throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));
        }

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            AssertionRequest assertionRequest = passkeyService.startDiscoverableLogin(rp);
            String assertionJson = assertionRequest.toJson();
            PasskeySessionService.saveSessionData(request, PasskeySessionService.LOGIN_SESSION_KEY, assertionJson);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("options", Convert.toJSONObject(assertionJson));
            return R.success(data);
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey login begin failed", e);
            throw new ResultException(R.errorPrompt("Passkey 登录初始化失败: " + e.getMessage()));
        }
    }

    @PostMapping("/user/passkey/login/finish")
    public Result<?> loginFinish(HttpServletRequest request) {
        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            String sessionJson = PasskeySessionService.popSessionData(request, PasskeySessionService.LOGIN_SESSION_KEY);
            if (sessionJson == null) throw new ResultException(R.errorPrompt("Passkey 登录会话已过期"));

            AssertionRequest assertionRequest = AssertionRequest.fromJson(sessionJson);

            // 用户发现回调：通过 credentialId 查找用户
            assert assertionRequest.getPublicKeyCredentialRequestOptions() != null;
            AssertionResult result = passkeyService.finishPasskeyLogin(rp, assertionRequest,
                    getRequestBody(request),
                    (credentialId, userHandle) -> {
                        try {
                            String credIdB64 = credentialId.getBase64();
                            PasskeyCredential credential = passkeyMapper.selectByCredentialId(credIdB64);
                            if (credential == null) return null;

                            User user = userMapper.selectById(credential.getUserId());
                            if (user == null || user.getStatus() != 1) return null;
                            return new PasskeyService.PasskeyUser(
                                    user.getId(), user.getUsername(),
                                    user.getDisplayName(), user.getStatus());
                        } catch (Exception e) {
                            log.error("Passkey login user lookup failed", e);
                            return null;
                        }
                    });

            // 更新凭证信息 - AssertionResult 更新 signCount 和 lastUsedAt
            int resultUserId = Integer.parseInt(new String(result.getUserHandle().getBytes(), java.nio.charset.StandardCharsets.UTF_8));
            PasskeyCredential existingCred = passkeyMapper.selectByUserId(resultUserId);
            if (existingCred != null) {
                existingCred.updateFromAssertion(result);
                passkeyMapper.updateById(existingCred);
            }

            // 设置用户登录会话
            User user = userMapper.selectById(resultUserId);
            if (user == null || user.getStatus() != 1) throw new ResultException(R.errorPrompt("该用户已被禁用"));

            // 写入 Sa-Token 登录会话（Go: setupLogin，原 HttpSession 与 AuthFilter 断链）
            StpUtil.login(user.getId());
            SaSession session = StpUtil.getSession();
            session.set("username", user.getUsername());
            session.set("role", user.getRole());
            session.set("status", user.getStatus());

            return R.success("Passkey 登录成功");
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey login finish failed", e);
            throw new ResultException(R.errorPrompt("Passkey 登录失败: " + e.getMessage()));
        }
    }

    // ======================== Self 操作（需登录） ========================

    @GetMapping("/user/passkey")
    public Result<?> status(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (credential != null) {
            data.put("enabled", true);
            data.put("last_used_at", credential.getLastUsedAt());
        } else {
            data.put("enabled", false);
        }
        return R.success(data);
    }

    @PostMapping("/user/passkey/register/begin")
    public Result<?> registerBegin(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));

        // 如需 2FA 前置验证
        if (!checkRegistrationVerification(request, userId)) throw new ResultException(R.errorPrompt("请先完成安全验证"));

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            User user = userMapper.selectById(userId);
            if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

            PublicKeyCredentialCreationOptions creationOptions = passkeyService.startRegistration(
                    rp, userId, user.getUsername(), user.getDisplayName());
            String creationJson = creationOptions.toJson();
            PasskeySessionService.saveSessionData(request, PasskeySessionService.REGISTRATION_SESSION_KEY, creationJson);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("options", Convert.toJSONObject(creationJson));
            return R.success(data);
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey register begin failed", e);
            throw new ResultException(R.errorPrompt("Passkey 注册初始化失败: " + e.getMessage()));
        }
    }

    @PostMapping("/user/passkey/register/finish")
    public Result<?> registerFinish(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));

        if (!checkRegistrationVerification(request, userId)) throw new ResultException(R.errorPrompt("请先完成安全验证"));

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            String sessionJson = PasskeySessionService.popSessionData(request, PasskeySessionService.REGISTRATION_SESSION_KEY);
            if (sessionJson == null) throw new ResultException(R.errorPrompt("Passkey 注册会话已过期"));

            PublicKeyCredentialCreationOptions creationOptions = PublicKeyCredentialCreationOptions.fromJson(sessionJson);
            RegistrationResult regResult = passkeyService.finishRegistration(rp, creationOptions, getRequestBody(request));

            // 持久化凭证
            PasskeyCredential credential = PasskeyCredential.fromRegistrationResult(userId, regResult);
            passkeyMapper.deleteByUserId(userId); // Upsert：先删除旧凭证
            passkeyMapper.insert(credential);

            return R.success("Passkey 注册成功");
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey register finish failed", e);
            throw new ResultException(R.errorPrompt("Passkey 注册失败: " + e.getMessage()));
        }
    }

    @PostMapping("/user/passkey/verify/begin")
    public Result<?> verifyBegin(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));

        PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
        if (credential == null) throw new ResultException(R.errorPrompt("该用户尚未绑定 Passkey"));

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            AssertionRequest assertionRequest = passkeyService.startLogin(rp, userId);
            String assertionJson = assertionRequest.toJson();
            PasskeySessionService.saveSessionData(request, PasskeySessionService.VERIFY_SESSION_KEY, assertionJson);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("options", Convert.toJSONObject(assertionJson));
            return R.success(data);
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey verify begin failed", e);
            throw new ResultException(R.errorPrompt("Passkey 验证初始化失败: " + e.getMessage()));
        }
    }

    @PostMapping("/user/passkey/verify/finish")
    public Result<?> verifyFinish(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (!settings.isEnabled()) throw new ResultException(R.errorPrompt("管理员未启用 Passkey 登录"));

        PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
        if (credential == null) throw new ResultException(R.errorPrompt("该用户尚未绑定 Passkey"));

        try {
            RelyingParty rp = passkeyService.buildRelyingParty(request);
            if (rp == null) throw new ResultException(R.errorPrompt("Passkey 服务未初始化"));

            String sessionJson = PasskeySessionService.popSessionData(request, PasskeySessionService.VERIFY_SESSION_KEY);
            if (sessionJson == null) throw new ResultException(R.errorPrompt("Passkey 验证会话已过期"));

            AssertionRequest assertionRequest = AssertionRequest.fromJson(sessionJson);
            passkeyService.finishLogin(rp, assertionRequest, getRequestBody(request));

            // 更新凭证最后使用时间
            credential.setLastUsedAt(System.currentTimeMillis() / 1000);
            passkeyMapper.updateById(credential);

            // 写入 Passkey 验证完成标记，供 /api/verify 转换为最终安全验证
            // 同时清除已有的安全验证状态，强制走 /api/verify 完成 step-up
            jakarta.servlet.http.HttpSession session = request.getSession();
            session.setAttribute(PasskeySessionService.PASSKEY_READY_KEY, System.currentTimeMillis() / 1000);
            session.removeAttribute(SecureVerificationFilter.SECURE_VERIFIED_AT);
            session.removeAttribute(SecureVerificationFilter.SECURE_VERIFIED_METHOD);

            return R.success("Passkey 验证成功");
        } catch (ResultException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey verify finish failed", e);
            throw new ResultException(R.errorPrompt("Passkey 验证失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/user/passkey")
    public Result<?> delete(HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) throw new ResultException(R.errorPrompt("未登录"));

        // 安全检查：如果有 2FA，需先完成 2FA 安全验证；否则需完成 Passkey 安全验证
        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa != null && Boolean.TRUE.equals(twoFa.getIsEnabled())) {
            if (!hasSecureVerification(request, "2fa")) {
                throw new ResultException(R.errorPrompt("请先完成安全验证"));
            }
        } else {
            // 无 2FA 时，删除前要求已通过 Passkey 安全验证
            PasskeyCredential existing = passkeyMapper.selectByUserId(userId);
            if (existing == null) throw new ResultException(R.errorPrompt("该用户尚未绑定 Passkey"));
            if (!hasSecureVerification(request, "passkey")) {
                throw new ResultException(R.errorPrompt("请先完成对应的安全验证"));
            }
        }

        PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
        if (credential == null) throw new ResultException(R.errorPrompt("该用户尚未绑定 Passkey"));

        int deleted = passkeyMapper.deleteByUserId(userId);
        if (deleted > 0) return R.success("Passkey 已解绑");
        throw new ResultException(R.errorPrompt("Passkey 解绑失败"));
    }

    // ======================== Admin 操作 ========================

    // 注：管理员重置 Passkey 由 AdminController.resetPasskey (DELETE /api/user/{id}/reset_passkey) 承载，

    // ======================== 内部分法 ========================

    /** 获取当前 Sa-Token 登录用户 ID，未登录返回 null */
    private Integer currentUserId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsInt() : null;
    }

    private boolean checkRegistrationVerification(HttpServletRequest request, int userId) {
        TwoFa twoFa = twoFaMapper.getByUserId(userId);
        if (twoFa == null || !Boolean.TRUE.equals(twoFa.getIsEnabled())) return true;
        // 启用 2FA 时，注册 Passkey 前要求已通过 2FA 安全验证
        return hasSecureVerification(request, "2fa");
    }

    /**
     * 检查 session 中是否存在有效的安全验证。      * <p>
     * 要求 session 中 secure_verified_at 时间戳未超过 5 分钟，且 secure_verified_method 与期望方式一致。
     * 超时或方式不匹配时清除验证状态并返回 false。
     *
     * @param method 期望的验证方式（"2fa" / "passkey"）
     */
    private boolean hasSecureVerification(HttpServletRequest request, String method) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return false;

        Object verifiedAtRaw = session.getAttribute(SecureVerificationFilter.SECURE_VERIFIED_AT);
        if (verifiedAtRaw == null) return false;

        long verifiedAt;
        if (verifiedAtRaw instanceof Long l) {
            verifiedAt = l;
        } else if (verifiedAtRaw instanceof Integer i) {
            verifiedAt = i.longValue();
        } else {
            clearSecureVerification(session);
            return false;
        }

        // 验证有效期 300 秒
        if (System.currentTimeMillis() / 1000 - verifiedAt >= 300) {
            clearSecureVerification(session);
            return false;
        }

        // 校验验证方式一致
        Object verifiedMethod = session.getAttribute(SecureVerificationFilter.SECURE_VERIFIED_METHOD);
        return method.equals(verifiedMethod);
    }

    private void clearSecureVerification(jakarta.servlet.http.HttpSession session) {
        session.removeAttribute(SecureVerificationFilter.SECURE_VERIFIED_AT);
        session.removeAttribute(SecureVerificationFilter.SECURE_VERIFIED_METHOD);
    }

    private boolean canManageTargetRole(int myRole, int targetRole) {
        // Admin 角色层级：role 值越小权限越高
        return myRole < targetRole;
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }
}
