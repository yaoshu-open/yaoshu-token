package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import ai.yue.library.base.convert.Convert;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.pojo.entity.TwoFa;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.ipo.UserIPO;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.TokenService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.VerificationService;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;

/**
 * 用户控制器  * <p>
 * 认证：公开（register/login/logout）无认证 + RateLimit 中间件 | Self（/api/user/self/*）UserAuth | Admin（/api/user/ etc）AdminAuth
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {    private final UserService userService;
    private final TokenService tokenService;
    private final OptionService optionService;
    private final TwoFaMapper twoFaMapper;

    private static final String KEY_PREFIX = "sk-";
    private static final int KEY_LENGTH = 48;
    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ======================== 公开操作（无认证） ========================

    /**
     * 用户注册      */
    @PostMapping("/user/register")
    public Result<?> register(@Valid @RequestBody UserIPO.Register ipo) {
        String username = trimToNull(ipo.getUsername());
        String password = ipo.getPassword();
        String email = trimToNull(ipo.getEmail());

        // 检查是否启用注册
        String registerEnabled = optionService.getValue("RegisterEnabled");
        if (!"true".equals(registerEnabled)) {
            throw new ResultException(R.errorPrompt("管理员已关闭新用户注册"));
        }

        // 检查邮箱验证（如果启用）
        String emailVerificationEnabled = optionService.getValue("EmailVerificationEnabled");
        if ("true".equals(emailVerificationEnabled)) {
            String verificationCode = ipo.getVerificationCode();
            if (email == null || email.isEmpty() || verificationCode == null || verificationCode.isEmpty()) {
                throw new ResultException(R.errorPrompt("需要邮箱验证码"));
            }
            if (!VerificationService.verifyCode(email, verificationCode, VerificationService.EMAIL_VERIFICATION_PURPOSE)) {
                throw new ResultException(R.errorPrompt("验证码错误或已过期"));
            }
        }

        // 检查用户名/邮箱是否已存在
        if (userService.checkExistOrDeleted(username, email)) {
            throw new ResultException(R.errorPrompt("用户名或邮箱已存在"));
        }

        // 处理邀请码（Go: aff_code 是邀请人的 code，不是注册用户自己的）
        String affCode = trimToNull(ipo.getAffCode());
        Integer inviterId = null;
        if (affCode != null) {
            User inviter = userService.findByAffCode(affCode);
            if (inviter != null) {
                inviterId = inviter.getId();
            }
        }

        // 构建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(userService.hashPassword(password));
        user.setDisplayName(username);
        user.setRole(1); // RoleCommonUser
        user.setStatus(1); // UserStatusEnabled
        user.setInviterId(inviterId);
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }

        userService.createUser(user);
        log.info("用户注册成功: {}", username);

        // 生成默认 Token
        String generateDefaultToken = optionService.getValue("GenerateDefaultToken");
        if (!"false".equals(generateDefaultToken)) {
            String key = generateKey();
            long now = System.currentTimeMillis() / 1000;
            Token token = new Token();
            token.setUserId(user.getId());
            token.setName(username + "的初始令牌");
            token.setKey(key);
            token.setCreatedTime(now);
            token.setAccessedTime(now);
            token.setExpiredTime(-1L); // 永不过期
            token.setRemainQuota(500000L);
            token.setUnlimitedQuota(true);
            token.setModelLimitsEnabled(false);
            token.setStatus(1);

            // 如果启用了 DefaultUseAutoGroup
            String defaultUseAutoGroup = optionService.getValue("DefaultUseAutoGroup");
            if ("true".equals(defaultUseAutoGroup)) {
                token.setGroup("auto");
            }

            tokenService.create(token);
        }

        return R.success();
    }

    /**
     * 用户登录      */
    @PostMapping("/user/login")
    public Result<?> login(@Valid @RequestBody UserIPO.Login ipo) {
        // 检查是否启用密码登录
        String passwordLoginEnabled = optionService.getValue("PasswordLoginEnabled");
        if (!"true".equals(passwordLoginEnabled)) {
            throw new ResultException(R.errorPrompt("管理员已关闭密码登录"));
        }

        String username = ipo.getUsername();
        String password = ipo.getPassword();

        // 查找用户（含密码字段）
        User user = userService.findByUsernameOrEmail(username);
        if (user == null) {
            throw new ResultException(R.errorPrompt("用户名或密码错误"));
        }

        // 校验密码
        if (!userService.validatePassword(password, user.getPassword())) {
            throw new ResultException(R.errorPrompt("用户名或密码错误"));
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new ResultException(R.errorPrompt("用户已被禁用"));
        }

        // 检查 2FA
        String twoFAEnabled = optionService.getValue("TwoFAEnabled");
        boolean require2fa = false;
        if ("true".equals(twoFAEnabled)) {
            TwoFa twoFa = twoFaMapper.getByUserId(user.getId());
            if (twoFa != null && Boolean.TRUE.equals(twoFa.getIsEnabled())) {
                require2fa = true;
            }
        }

        // 建立 Sa-Token 登录会话（替代 HttpSession
        StpUtil.login(user.getId());
        String tokenValue = StpUtil.getTokenValue();
        SaSession session = StpUtil.getSession();
        session.set("username", user.getUsername());
        session.set("role", user.getRole());
        session.set("status", user.getStatus());
        session.set("group", user.getGroup());

        // 2FA 用户：打 pending 标记并提前返回，不更新 last_login_at
        // AuthFilter 检测到 2fa_pending 将拦截所有受保护端点，TwoFaController.verifyLogin 通过后清除标记
        if (require2fa) {
            session.set("2fa_pending", true);
            Map<String, Object> require2faData = new LinkedHashMap<>();
            require2faData.put("require_2fa", true);
            require2faData.put("token", tokenValue);
            return R.success(require2faData);
        }

        // 非 2FA：更新最后登录时间
        userService.updateLastLoginAt(user.getId());

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("token", tokenValue);
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("displayName", user.getDisplayName());
        userData.put("role", user.getRole());
        userData.put("status", user.getStatus());
        userData.put("group", user.getGroup());

        return R.success(userData);
    }

    @GetMapping("/user/logout")
    public Result<?> logout() {
        StpUtil.logout();
        return R.success();
    }

    // 注：/api/user/groups 端点已迁移到 GroupController.getUserGroups（与 /api/user/self/groups 共用 GetUserGroups 实现）

    // ======================== Self 操作（UserAuth） ========================

    /** 角色常量*/
    private static final int ROLE_ROOT = 3;
    private static final int ROLE_ADMIN = 2;

    /**
     * 获取当前用户信息      */
    @SaCheckLogin
    @GetMapping("/user/self")
    public Result<?> getSelf(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, false);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // 清除 remark（Go: 管理员备注不暴露给普通用户）
        // 构建响应数据（Go 返回所有字段）
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("id", user.getId());
        responseData.put("username", user.getUsername());
        responseData.put("displayName", user.getDisplayName());
        responseData.put("role", user.getRole());
        responseData.put("status", user.getStatus());
        responseData.put("email", user.getEmail());
        responseData.put("githubId", user.getGithubId());
        responseData.put("discordId", user.getDiscordId());
        responseData.put("oidcId", user.getOidcId());
        responseData.put("wechatId", user.getWechatId());
        responseData.put("telegramId", user.getTelegramId());
        responseData.put("group", user.getGroup());
        responseData.put("quota", user.getQuota());
        responseData.put("usedQuota", user.getUsedQuota());
        responseData.put("requestCount", user.getRequestCount());
        responseData.put("affCode", user.getAffCode());
        responseData.put("affCount", user.getAffCount());
        responseData.put("affQuota", user.getAffQuota());
        responseData.put("affHistoryQuota", user.getAffHistoryQuota());
        responseData.put("inviterId", user.getInviterId());
        responseData.put("linuxDoId", user.getLinuxDoId());
        responseData.put("setting", user.getSetting());
        responseData.put("stripeCustomer", user.getStripeCustomer());
        // 从 setting JSON 提取 sidebar_modules（Go: userSetting.SidebarModules）
        responseData.put("sidebarModules", extractSidebarModules(user.getSetting()));
        // 用户权限信息（Go: calculateUserPermissions）
        responseData.put("permissions", calculateUserPermissions(user.getRole()));

        return R.success(responseData);
    }

    /**
     * 从 user.setting JSON 中提取 sidebar_modules 字段      */
    private Object extractSidebarModules(String setting) {
        if (setting == null || setting.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> settingMap = Convert.toJSONObject(setting);
            return settingMap.get("sidebar_modules");
        } catch (Exception e) {
            log.warn("Failed to extract sidebar_modules from setting: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算用户权限      */
    private Map<String, Object> calculateUserPermissions(Integer userRole) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        int role = userRole != null ? userRole : 0;
        if (role == ROLE_ROOT) {
            // 超级管理员不需要边栏设置功能
            permissions.put("sidebar_settings", false);
            permissions.put("sidebar_modules", new LinkedHashMap<>());
        } else if (role == ROLE_ADMIN) {
            // 管理员可以设置边栏，但不包含系统设置功能
            permissions.put("sidebar_settings", true);
            Map<String, Object> modules = new LinkedHashMap<>();
            Map<String, Object> adminModule = new LinkedHashMap<>();
            adminModule.put("setting", false); // 管理员不能访问系统设置
            modules.put("admin", adminModule);
            permissions.put("sidebar_modules", modules);
        } else {
            // 普通用户只能设置个人功能，不包含管理员区域
            permissions.put("sidebar_settings", true);
            Map<String, Object> modules = new LinkedHashMap<>();
            modules.put("admin", false); // 普通用户不能访问管理员区域
            permissions.put("sidebar_modules", modules);
        }
        return permissions;
    }

    /**
     * 更新个人信息      */
    @SaCheckLogin
    @PutMapping("/user/self")
    public Result<?> updateSelf(HttpServletRequest request, @Valid @RequestBody UserIPO.UpdateSelf ipo) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, true);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // sidebar_modules 更新（存储在 user.setting 的 JSON 中）
        String sidebarModules = trimToNull(ipo.getSidebarModules());
        if (sidebarModules != null) {
            user.setSetting(updateUserSettingField(user.getSetting(), "sidebar_modules", sidebarModules));
            userService.updateUser(user, false);
            return R.success();
        }

        // language 更新
        String language = trimToNull(ipo.getLanguage());
        if (language != null) {
            user.setSetting(updateUserSettingField(user.getSetting(), "language", language));
            userService.updateUser(user, false);
            return R.success();
        }

        // 通用信息更新
        String displayName = trimToNull(ipo.getDisplayName());
        if (displayName != null) user.setDisplayName(displayName);

        String email = trimToNull(ipo.getEmail());
        if (email != null) user.setEmail(email);

        String newPassword = trimToNull(ipo.getPassword());
        boolean updatePassword = false;
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(userService.hashPassword(newPassword));
            updatePassword = true;
        }

        userService.updateUser(user, updatePassword);
        return R.success();
    }

    /**
     * 删除账户      */
    @SaCheckLogin
    @DeleteMapping("/user/self")
    public Result<?> deleteSelf(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, false);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // Root 用户不能删除自己
        if (user.getRole() != null && user.getRole() == 3) {
            throw new ResultException(R.errorPrompt("无法删除 Root 用户"));
        }

        userService.deleteUser(userId);
        // 清理 Sa-Token 登录会话
        StpUtil.logout();
        return R.success();
    }

    /**
     * 生成 AccessToken      */
    @SaCheckLogin
    @GetMapping("/user/token")
    public Result<?> generateToken(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, true);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // 生成随机 key（28-32 位）
        int keyLen = 29 + SECURE_RANDOM.nextInt(4);
        String key = generateKey(keyLen);

        user.setAccessToken(key);
        userService.updateUser(user, false);

        return R.success(key);
    }

    /**
     * 获取推广码      */
    @SaCheckLogin
    @GetMapping("/user/aff")
    public Result<?> getAffCode(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, true);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // 如果没有 aff_code，生成一个
        String affCode = user.getAffCode();
        if (affCode == null || affCode.isEmpty()) {
            affCode = generateAffCode();
            user.setAffCode(affCode);
            userService.updateUser(user, false);
        }

        return R.success(affCode);
    }

    /**
     * 转移推广额度      */
    @SaCheckLogin
    @PostMapping("/user/aff_transfer")
    public Result<?> transferAffQuota(HttpServletRequest request, @Valid @RequestBody UserIPO.AffTransfer ipo) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        Integer quota = ipo.getQuota();

        User user = userService.getById(userId, true);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        Integer affQuota = user.getAffQuota();
        if (affQuota == null || affQuota < quota) {
            throw new ResultException(R.errorPrompt("可转移的推广额度不足"));
        }

        // 从 aff_quota 扣减，增加到 quota
        user.setAffQuota(affQuota - quota);
        user.setQuota((user.getQuota() != null ? user.getQuota() : 0) + quota);
        userService.updateUser(user, false);

        log.info("用户 {} 推广额度转移 {} 到主额度", user.getUsername(), quota);
        return R.success();
    }

    /**
     * 更新用户设置      */
    @SaCheckLogin
    @PutMapping("/user/setting")
    public Result<?> updateSetting(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        User user = userService.getById(userId, true);
        if (user == null) throw new ResultException(R.errorPrompt("用户不存在"));

        // 更新 setting JSON 中的字段值
        String mergedSetting = mergeUserSetting(user.getSetting(), body);
        user.setSetting(mergedSetting);
        userService.updateUser(user, false);
        return R.success();
    }

    // ======================== 辅助方法 ========================

    /**
     * 更新 user.setting（JSON字符串）中的单个字段
     */
    @SuppressWarnings("unchecked")
    private String updateUserSettingField(String currentSetting, String key, Object value) {
        try {
            Map<String, Object> setting = (currentSetting != null && !currentSetting.isEmpty())
                    ? Convert.toJSONObject(currentSetting)
                    : new LinkedHashMap<>();
            setting.put(key, value);
            return Convert.toJSONString(setting);
        } catch (Exception e) {
            log.warn("Failed to update user setting field {}: {}", key, e.getMessage());
            return "{\"" + key + "\":\"" + value + "\"}";
        }
    }

    /**
     * 合并多个 setting 字段
     */
    @SuppressWarnings("unchecked")
    private String mergeUserSetting(String currentSetting, Map<String, Object> newFields) {
        try {
            Map<String, Object> setting = (currentSetting != null && !currentSetting.isEmpty())
                    ? Convert.toJSONObject(currentSetting)
                    : new LinkedHashMap<>();
            setting.putAll(newFields);
            return Convert.toJSONString(setting);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ======================== 辅助方法 ========================

    /** 生成 Token Key */
    private String generateKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        sb.append(KEY_PREFIX);
        for (int i = KEY_PREFIX.length(); i < KEY_LENGTH; i++) {
            sb.append(KEY_CHARS.charAt(SECURE_RANDOM.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    /** 生成指定长度的 Key */
    private String generateKey(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(KEY_CHARS.charAt(SECURE_RANDOM.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    /** 生成 4 位推广码 */
    private String generateAffCode() {
        String affChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(affChars.charAt(SECURE_RANDOM.nextInt(affChars.length())));
        }
        return sb.toString();
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
