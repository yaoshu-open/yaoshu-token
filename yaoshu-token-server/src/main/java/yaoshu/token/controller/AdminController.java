package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import ai.yue.library.web.util.ServletUtils;
import com.github.pagehelper.PageInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.ipo.AdminIPO;
import yaoshu.token.service.OAuthManagementService;
import yaoshu.token.service.TopupService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.passkey.PasskeyUserService;
import yaoshu.token.mapper.TwoFaMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.pojo.entity.TopUp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;

/**
 * 用户管理控制器（Admin）  * <p>
 * 认证：AdminAuth（全部）
 */
@Slf4j
@RestController
@SaCheckRole("admin")
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final OAuthManagementService oauthManagementService;
    private final PasskeyUserService passkeyUserService;
    private final TwoFaMapper twoFaMapper;
    private final TopUpMapper topUpMapper;
    private final TopupService topupService;

    /** RoleRootUser = 3, RoleAdminUser = 2, RoleCommonUser = 1 */
    private static final int ROLE_ROOT = 3;
    private static final int ROLE_ADMIN = 2;
    private static final int USER_STATUS_ENABLED = 1;
    private static final int USER_STATUS_DISABLED = 2;

    // ======================== 查询 ========================

    /**
     * 分页获取所有用户      */
    @GetMapping("/user/")
    public Result<?> getAllUsers(HttpServletRequest request) {
        List<User> users = userService.getAllUsers();
        return R.success(PageInfo.of(users));
    }

    /**
     * 搜索用户      */
    @GetMapping("/user/search")
    public Result<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        List<User> users = userService.searchUsers(keyword, group, role, status);
        return R.success(PageInfo.of(users));
    }

    /**
     * 获取单个用户      */
    @GetMapping("/user/{id}")
    public Result<?> getUser(@PathVariable int id, HttpServletRequest request) {
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }

        // 角色层级检查：当前管理员只能管理角色低于自己的用户
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }

        return R.success(user);
    }

    // ======================== 写操作 ========================

    /**
     * 创建用户      */
    @PostMapping("/user/")
    public Result<?> createUser(@Valid @RequestBody AdminIPO.CreateUser ipo, HttpServletRequest request) {
        String username = trimToNull(ipo.getUsername());
        String password = ipo.getPassword();
        String displayName = ipo.getDisplayName();
        Integer role = ipo.getRole();

        // 检查用户名/邮箱是否已存在
        String email = ipo.getEmail();
        if (userService.checkExistOrDeleted(username, email)) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.username_email_exists")));
        }

        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && role != null && role >= myRole) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.cannot_create_same_higher_level")));
        }

        // 哈希密码
        User user = new User();
        user.setUsername(username);
        user.setPassword(userService.hashPassword(password));
        user.setDisplayName(displayName != null ? displayName : username);
        user.setRole(role != null ? role : 1);
        user.setStatus(USER_STATUS_ENABLED);
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }
        // group
        String group = trimToNull(ipo.getGroup());
        if (group != null) {
            user.setGroup(group);
        }
        // quota
        Integer quota = ipo.getQuota();
        if (quota != null) {
            user.setQuota(quota.longValue());
        }

        userService.createUser(user);
        return R.success();
    }

    /**
     * 管理用户（禁用/启用/删除/升降级/配额）      */
    @PostMapping("/user/manage")
    public Result<?> manageUser(@Valid @RequestBody AdminIPO.ManageUser ipo, HttpServletRequest request) {
        Integer id = ipo.getId();
        String action = ipo.getAction();
        Integer value = ipo.getValue();
        String mode = ipo.getMode();

        if (id == null || action == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
        }

        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }

        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }

        // Go: username 来自上下文
        String adminUsername = (String) request.getAttribute("username");

        switch (action) {
            case "disable":
                if (user.getRole() != null && user.getRole() == ROLE_ROOT) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.cannot_disable_root")));
                }
                user.setStatus(USER_STATUS_DISABLED);
                userService.updateUser(user, false);
                break;
            case "enable":
                user.setStatus(USER_STATUS_ENABLED);
                userService.updateUser(user, false);
                break;
            case "delete":
                if (user.getRole() != null && user.getRole() == ROLE_ROOT) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.cannot_delete_root")));
                }
                userService.deleteUser(id);
                break;
            case "promote":
                if (myRole != null && myRole != ROLE_ROOT) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.only_root_can_promote")));
                }
                if (user.getRole() != null && user.getRole() >= ROLE_ADMIN) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_already_admin")));
                }
                user.setRole(ROLE_ADMIN);
                userService.updateUser(user, false);
                break;
            case "demote":
                if (user.getRole() != null && user.getRole() == ROLE_ROOT) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.cannot_demote_root")));
                }
                if (user.getRole() != null && user.getRole() <= 1) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_already_common")));
                }
                user.setRole(1); // RoleCommonUser
                userService.updateUser(user, false);
                break;
            case "add_quota":
                if (mode == null) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
                }
                if (value == null || value <= 0) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("admin.quota_change_cannot_be_zero")));
                }
                switch (mode) {
                    case "add":
                        userService.increaseUserQuota(id, value);
                        break;
                    case "subtract":
                        userService.decreaseUserQuota(id, value);
                        break;
                    case "override":
                        user.setQuota(value.longValue());
                        userService.updateUser(user, false);
                        break;
                    default:
                        throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
                }
                log.info("管理员 {} {} 用户 {} 配额 {} {}",
                        adminUsername, mode, user.getUsername(), "override".equals(mode) ? "覆盖为" : "调整", value);
                break;
            default:
                throw new ResultException(R.errorPrompt(I18nUtils.get("admin.invalid_operation_type")));
        }

        return R.success();
    }

    /**
     * 更新用户      */
    @PutMapping("/user/")
    public Result<?> updateUser(@Valid @RequestBody AdminIPO.UpdateUser ipo, HttpServletRequest request) {
        Integer id = ipo.getId();
        if (id == null || id == 0) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
        }

        User originUser = userService.getById(id, false);
        if (originUser == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }

        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, originUser.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }

        // 检查目标角色是否可设置
        Integer newRole = ipo.getRole();
        if (newRole != null && myRole != null && !canManageTargetRole(myRole, newRole)) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.cannot_set_same_higher_role")));
        }

        boolean updatePassword = false;
        User updatedUser = new User();
        updatedUser.setId(id);

        // 选择性更新：仅更新 body 中提供了的字段
        String username = trimToNull(ipo.getUsername());
        if (username != null) updatedUser.setUsername(username);

        String password = trimToNull(ipo.getPassword());
        if (password != null && !password.isEmpty() && !"$I_LOVE_U".equals(password)) {
            updatedUser.setPassword(userService.hashPassword(password));
            updatePassword = true;
        }

        String displayName = trimToNull(ipo.getDisplayName());
        if (displayName != null) updatedUser.setDisplayName(displayName);

        if (newRole != null) updatedUser.setRole(newRole);

        Integer status = ipo.getStatus();
        if (status != null) updatedUser.setStatus(status);

        String email = trimToNull(ipo.getEmail());
        if (email != null) updatedUser.setEmail(email);

        String group = trimToNull(ipo.getGroup());
        if (group != null) updatedUser.setGroup(group);

        Integer quota = ipo.getQuota();
        if (quota != null) updatedUser.setQuota(quota.longValue());

        userService.updateUser(updatedUser, updatePassword);
        return R.success();
    }

    /**
     * 删除用户      */
    @DeleteMapping("/user/{id}")
    public Result<?> deleteUser(@PathVariable int id, HttpServletRequest request) {
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }

        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && myRole <= (user.getRole() != null ? user.getRole() : 0)) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }

        userService.deleteUser(id);
        return R.success();
    }

    @DeleteMapping("/user/{id}/reset_passkey")
    public Result<?> resetPasskey(@PathVariable int id, HttpServletRequest request) {
        // 查询目标用户
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }
        // 删除用户所有 Passkey（Go: DeletePasskeyByUserID）
        if (!passkeyUserService.deletePasskeyByUserId(id)) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.passkey_not_bound")));
        }
        return R.success(I18nUtils.get("admin.passkey_reset"));
    }

    @GetMapping("/user/topup")
    public Result<?> getAllTopups(HttpServletRequest request) {
        String keyword = request.getParameter("keyword");

        // 构建查询条件（Go: GetAllTopUps / SearchAllTopUps）
        LambdaQueryWrapper<TopUp> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.like(TopUp::getTradeNo, keyword);
        }
        queryWrapper.orderByDesc(TopUp::getId);

        // PageHelper 分页（紧贴查询前，自动 count）
        PageHelper.startPage(ServletUtils.getRequest());
        List<TopUp> topups = topUpMapper.selectList(queryWrapper);
        return R.success(PageInfo.of(topups));
    }

    @PostMapping("/user/topup/complete")
    public Result<?> completeTopup(@Valid @RequestBody AdminIPO.CompleteTopup ipo, HttpServletRequest request) {
        String tradeNo = ipo.getTradeNo();
        if (tradeNo == null || tradeNo.isEmpty()) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.trade_no_missing")));
        }
        // 调用服务层事务方法（Go: ManualCompleteTopUp → FOR UPDATE + 状态幂等 + 配额落库）
        boolean success = topupService.manualCompleteTopUp(tradeNo, ServletUtils.getClientIP(request));
        if (!success) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.manual_complete_failed")));
        }
        return R.success();
    }

    @GetMapping("/user/{id}/oauth/bindings")
    public Result<?> getUserOAuthBindings(@PathVariable int id, HttpServletRequest request) {
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }
        return R.success(oauthManagementService.listUserBindings(id));
    }

    @DeleteMapping("/user/{id}/oauth/bindings/{provider_id:\\d+}")
    public Result<?> unbindUserOAuth(@PathVariable int id,
                                               @PathVariable("provider_id") String providerId,
                                               HttpServletRequest request) {
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }
        try {
            oauthManagementService.unbindUserOAuth(id, Integer.parseInt(providerId));
            return R.success();
        } catch (NumberFormatException e) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("oauth.invalid_provider_id")));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @DeleteMapping("/user/{id}/bindings/{binding_type}")
    public Result<?> clearUserBinding(@PathVariable int id,
                                                @PathVariable("binding_type") String bindingType,
                                                HttpServletRequest request) {
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_same_higher_level")));
        }
        try {
            oauthManagementService.clearUserBinding(id, bindingType);
            return R.success();
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @GetMapping("/user/2fa/stats")
    public Result<?> twoFaStats() {
        // COUNT 用户总数（Go: DB.Model(&User{}).Count）
        long totalUsers = userService.countAll();
        // COUNT 启用 2FA 的用户数（Go: DB.Model(&TwoFA{}).Where("is_enabled = true").Count）
        long enabledUsers = twoFaMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<yaoshu.token.pojo.entity.TwoFa>()
                .eq(yaoshu.token.pojo.entity.TwoFa::getIsEnabled, true));

        // 计算启用率（Go: enabledRate = enabled / total * 100 → "%.1f%%"）
        double enabledRate = 0;
        if (totalUsers > 0) {
            enabledRate = (double) enabledUsers / totalUsers * 100;
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_users", totalUsers);
        stats.put("enabled_users", enabledUsers);
        stats.put("enabled_rate", String.format("%.1f%%", enabledRate));

        return R.success(stats);
    }

    @DeleteMapping("/user/{id}/2fa")
    public Result<?> disableTwoFa(@PathVariable int id, HttpServletRequest request) {
        // 查询目标用户
        User user = userService.getById(id, false);
        if (user == null) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_not_exists")));
        }
        Integer myRole = (Integer) request.getAttribute("role");
        if (myRole != null && !canManageTargetRole(myRole, user.getRole())) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.no_permission_2fa")));
        }
        // 禁用 2FA（Go: DisableTwoFA → is_enabled=0 + deleted_at=NOW）
        int affected = twoFaMapper.disableByUserId(id);
        if (affected <= 0) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("admin.user_2fa_not_enabled")));
        }
        // 操作日志：管理员禁用了用户的2FA
        log.info("管理员 userId={} 禁用了用户 userId={} 的2FA", request.getAttribute("id"), id);
        return R.success();
    }

    // ======================== 辅助方法 ========================

    /**
     * 角色层级检查：Go canManageTargetRole
     * myRole == Root 或 myRole > targetRole 时可管理
     */
    private boolean canManageTargetRole(int myRole, Integer targetRole) {
        if (targetRole == null) return true;
        return myRole == ROLE_ROOT || myRole > targetRole;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}

