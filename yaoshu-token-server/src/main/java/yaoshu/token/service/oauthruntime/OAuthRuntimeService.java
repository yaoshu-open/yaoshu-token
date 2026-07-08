package yaoshu.token.service.oauthruntime;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import ai.yue.library.base.util.I18nUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserOAuthBindingMapper;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserOAuthBinding;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.VerificationService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;
/**
 * OAuth 统一编排服务  */
@Service
@RequiredArgsConstructor
public class OAuthRuntimeService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final OAuthProviderRegistry providerRegistry;
    private final UserMapper userMapper;
    private final UserOAuthBindingMapper bindingMapper;
    private final UserService userService;
    private final OptionService optionService;

    @Transactional(rollbackFor = Exception.class)
    public Result<?> handleOAuth(String providerSlug, HttpServletRequest request) {
        try {
            OAuthProvider provider = providerRegistry.getProvider(providerSlug);
            if (provider == null) {
                throw new ResultException(R.errorPrompt("未知的 OAuth 提供商"));
            }

            HttpSession session = request.getSession(false);
            if (!isStateValid(session, request.getParameter("state"))) {
                throw new ResultException(R.errorPrompt("state is empty or not same"));
            }

            String providerError = StrUtil.trimToEmpty(request.getParameter("error"));
            if (StrUtil.isNotBlank(providerError)) {
                throw new ResultException(R.errorPrompt(StrUtilCompat.blankToDefault(request.getParameter("error_description"), providerError)));
            }

            // 登录态判断改用 Sa-Token（原 HttpSession.username 判断与 AuthFilter 断链）
            if (StpUtil.isLogin()) {
                return handleOAuthBind(provider, request);
            }
            return handleOAuthLogin(provider, request, session);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> bindEmail(String email, String code, HttpServletRequest request) {
        try {
            if (StrUtil.isBlank(email) || StrUtil.isBlank(code)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
            }
            if (!VerificationService.verifyCode(email, code, VerificationService.EMAIL_VERIFICATION_PURPOSE)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("user.verification_code_error")));
            }
            User user = requireSessionUser();
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getEmail, email));
            return R.success();
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> unbindEmail(String code, HttpServletRequest request) {
        try {
            if (StrUtil.isBlank(code)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
            }
            User user = requireSessionUser();
            String email = user.getEmail();
            if (StrUtil.isBlank(email)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("oauth.email_not_bound")));
            }
            if (!VerificationService.verifyCode(email, code, VerificationService.EMAIL_VERIFICATION_PURPOSE)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("user.verification_code_error")));
            }
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getEmail, ""));
            return R.success();
        } catch (ResultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> handleWeChatAuth(String code, HttpServletRequest request) {
        try {
            ensureEnabled("WeChatAuthEnabled", "管理员未开启通过微信登录以及注册");
            String wechatId = getWechatIdByCode(code);
            User user = findActiveUserByField(User::getWechatId, wechatId);
            if (user == null && isUserFieldTaken(User::getWechatId, wechatId)) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("oauth.user_deleted")));
            }
            if (user == null) {
                if (!isRegisterEnabled()) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("user.register_disabled")));
                }
                user = buildNewOAuthUser(null, "wechat_", "WeChat User", null);
                user.setWechatId(wechatId);
                insertUser(user);
            }
            if (!isEnabledUser(user)) {
                throw new ResultException(R.errorPrompt("用户已被封禁"));
            }
            setupLogin(user);
            return R.success(buildUserData(user));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<?> bindWeChat(String code, HttpServletRequest request) {
        try {
            ensureEnabled("WeChatAuthEnabled", "管理员未开启通过微信登录以及注册");
            String wechatId = getWechatIdByCode(code);
            if (isUserFieldTaken(User::getWechatId, wechatId)) {
                throw new ResultException(R.errorPrompt("该微信账号已被绑定"));
            }
            User user = requireSessionUser();
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getWechatId, wechatId));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", "bind");
            return R.success(data);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    public Result<?> handleTelegramLogin(HttpServletRequest request) {
        try {
            ensureEnabled("TelegramOAuthEnabled", "管理员未开启通过 Telegram 登录以及注册");
            ensureTelegramSignatureValid(request);
            String telegramId = StrUtil.trimToEmpty(request.getParameter("id"));
            User user = findActiveUserByField(User::getTelegramId, telegramId);
            if (user == null) {
                throw new ResultException(R.errorPrompt("用户不存在"));
            }
            setupLogin(user);
            return R.success(buildUserData(user));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String bindTelegram(HttpServletRequest request) {
        ensureEnabled("TelegramOAuthEnabled", "管理员未开启通过 Telegram 登录以及注册");
        ensureTelegramSignatureValid(request);
        String telegramId = StrUtil.trimToEmpty(request.getParameter("id"));
        if (isUserFieldTaken(User::getTelegramId, telegramId)) {
            throw new RuntimeException(I18nUtils.get("oauth.telegram_already_bound"));
        }
        User user = requireSessionUser();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getTelegramId, telegramId));
        return CommonConstants.themeAwarePath("/console/personal");
    }

    private Result<?> handleOAuthLogin(OAuthProvider provider, HttpServletRequest request, HttpSession session) {
        if (!provider.isEnabled()) {
            throw new ResultException(R.errorPrompt("管理员未开启通过 " + provider.getName() + " 登录以及注册"));
        }
        OAuthRuntimeModels.OAuthToken token = provider.exchangeToken(request.getParameter("code"), request);
        OAuthRuntimeModels.OAuthUser oauthUser = provider.getUserInfo(token, request);
        User user = findOrCreateOAuthUser(provider, oauthUser, session);
        if (!isEnabledUser(user)) {
            throw new ResultException(R.errorPrompt("用户已被封禁"));
        }
        setupLogin(user);
        return R.success(buildUserData(user));
    }

    private Result<?> handleOAuthBind(OAuthProvider provider, HttpServletRequest request) {
        if (!provider.isEnabled()) {
            throw new ResultException(R.errorPrompt("管理员未开启通过 " + provider.getName() + " 登录以及注册"));
        }
        OAuthRuntimeModels.OAuthToken token = provider.exchangeToken(request.getParameter("code"), request);
        OAuthRuntimeModels.OAuthUser oauthUser = provider.getUserInfo(token, request);
        if (provider.isUserIdTaken(oauthUser.getProviderUserId())) {
            throw new ResultException(R.errorPrompt("该 " + provider.getName() + " 账户已被绑定"));
        }
        String legacyId = extraString(oauthUser, "legacy_id");
        if (StrUtil.isNotBlank(legacyId) && provider.isUserIdTaken(legacyId)) {
            throw new ResultException(R.errorPrompt("该 " + provider.getName() + " 账户已被绑定"));
        }

        User sessionUser = requireSessionUser();
        if (provider.isGenericProvider()) {
            upsertOAuthBinding(sessionUser.getId(), provider.getProviderId(), oauthUser.getProviderUserId());
        } else {
            User update = new User();
            update.setId(sessionUser.getId());
            provider.setProviderUserId(update, oauthUser.getProviderUserId());
            userMapper.updateById(update);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", "bind");
        return R.success(data);
    }

    private User findOrCreateOAuthUser(OAuthProvider provider,
                                       OAuthRuntimeModels.OAuthUser oauthUser,
                                       HttpSession session) {
        if (provider.isUserIdTaken(oauthUser.getProviderUserId())) {
            User existingUser = provider.fillUserByProviderId(oauthUser.getProviderUserId());
            if (existingUser == null || existingUser.getId() == null || existingUser.getId() == 0) {
                throw new RuntimeException(I18nUtils.get("oauth.user_deleted"));
            }
            return existingUser;
        }

        String legacyId = extraString(oauthUser, "legacy_id");
        if (StrUtil.isNotBlank(legacyId) && provider.isUserIdTaken(legacyId)) {
            User existingUser = provider.fillUserByProviderId(legacyId);
            if (existingUser == null || existingUser.getId() == null || existingUser.getId() == 0) {
                throw new RuntimeException(I18nUtils.get("oauth.user_deleted"));
            }
            if (!provider.isGenericProvider()) {
                User update = new User();
                update.setId(existingUser.getId());
                provider.setProviderUserId(update, oauthUser.getProviderUserId());
                userMapper.updateById(update);
            }
            return existingUser;
        }

        if (!isRegisterEnabled()) {
            throw new RuntimeException(I18nUtils.get("user.register_disabled"));
        }

        User user = buildNewOAuthUser(
                oauthUser.getUsername(),
                provider.getProviderPrefix(),
                provider.getName() + " User",
                oauthUser.getEmail()
        );
        if (StrUtil.isNotBlank(oauthUser.getDisplayName())) {
            user.setDisplayName(oauthUser.getDisplayName());
        } else if (StrUtil.isNotBlank(oauthUser.getUsername())) {
            user.setDisplayName(oauthUser.getUsername());
        }
        user.setInviterId(resolveInviterId(session));
        if (!provider.isGenericProvider()) {
            provider.setProviderUserId(user, oauthUser.getProviderUserId());
        }
        insertUser(user);
        if (provider.isGenericProvider()) {
            upsertOAuthBinding(user.getId(), provider.getProviderId(), oauthUser.getProviderUserId());
        }
        return user;
    }

    private User buildNewOAuthUser(String preferredUsername, String fallbackPrefix, String fallbackDisplayName, String email) {
        User user = new User();
        user.setUsername(generateUsername(preferredUsername, fallbackPrefix));
        user.setDisplayName(fallbackDisplayName);
        user.setEmail(StrUtilCompat.blankToDefault(email, ""));
        user.setRole(CommonConstants.ROLE_COMMON_USER);
        user.setStatus(CommonConstants.USER_STATUS_ENABLED);
        return user;
    }

    private void insertUser(User user) {
        long now = System.currentTimeMillis() / 1000;
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        if (user.getLastLoginAt() == null) {
            user.setLastLoginAt(0L);
        }
        userMapper.insert(user);
    }

    private void upsertOAuthBinding(Integer userId, Integer providerId, String providerUserId) {
        UserOAuthBinding existing = bindingMapper.selectOne(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getUserId, userId)
                .eq(UserOAuthBinding::getProviderId, providerId)
                .last("LIMIT 1"));
        if (existing == null) {
            UserOAuthBinding binding = new UserOAuthBinding();
            binding.setUserId(userId);
            binding.setProviderId(providerId);
            binding.setProviderUserId(providerUserId);
            bindingMapper.insert(binding);
            return;
        }
        existing.setProviderUserId(providerUserId);
        bindingMapper.updateById(existing);
    }

    private void setupLogin(User user) {
        userService.updateLastLoginAt(user.getId());
        StpUtil.login(user.getId());
        SaSession session = StpUtil.getSession();
        session.set("username", user.getUsername());
        session.set("role", user.getRole());
        session.set("status", user.getStatus());
        session.set("group", user.getGroup());
    }

    private Map<String, Object> buildUserData(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("display_name", user.getDisplayName());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("group", user.getGroup());
        return data;
    }

    private User requireSessionUser() {
        if (!StpUtil.isLogin()) {
            throw new RuntimeException(I18nUtils.get("oauth.not_logged_in"));
        }
        Integer userId = StpUtil.getLoginIdAsInt();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
        if (user == null) {
            throw new RuntimeException(I18nUtils.get("oauth.user_deleted"));
        }
        return user;
    }

    private boolean isStateValid(HttpSession session, String state) {
        return session != null
                && session.getAttribute("oauth_state") instanceof String sessionState
                && StrUtil.isNotBlank(state)
                && state.equals(sessionState);
    }

    private void ensureEnabled(String key, String message) {
        if (!"true".equalsIgnoreCase(StrUtil.trimToEmpty(optionService.getValue(key)))) {
            throw new RuntimeException(message);
        }
    }

    private boolean isRegisterEnabled() {
        return "true".equalsIgnoreCase(StrUtil.trimToEmpty(optionService.getValue("RegisterEnabled")));
    }

    private Integer resolveInviterId(HttpSession session) {
        if (session == null || session.getAttribute("aff") == null) {
            return null;
        }
        User inviter = userService.findByAffCode(String.valueOf(session.getAttribute("aff")));
        return inviter == null ? null : inviter.getId();
    }

    private boolean isEnabledUser(User user) {
        return user.getStatus() != null && user.getStatus() == CommonConstants.USER_STATUS_ENABLED;
    }

    private String generateUsername(String preferredUsername, String fallbackPrefix) {
        String candidate = StrUtil.trimToEmpty(preferredUsername);
        if (StrUtil.isNotBlank(candidate)
                && candidate.length() <= 20
                && !userService.checkExistOrDeleted(candidate, null)) {
            return candidate;
        }
        User maxUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .orderByDesc(User::getId)
                .last("LIMIT 1"));
        int maxId = maxUser == null || maxUser.getId() == null ? 0 : maxUser.getId();
        return fallbackPrefix + (maxId + 1);
    }

    private String getWechatIdByCode(String code) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        String serverAddress = StrUtil.trimToEmpty(optionService.getValue("WeChatServerAddress"));
        String serverToken = StrUtil.trimToEmpty(optionService.getValue("WeChatServerToken"));
        String target = StrUtil.removeSuffix(serverAddress, "/") + "/api/wechat/user?code="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", serverToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = Convert.toJSONObject(response.body());
            if (!Boolean.TRUE.equals(body.get("success"))) {
                throw new RuntimeException(String.valueOf(body.getOrDefault("message", I18nUtils.get("user.verification_code_error"))));
            }
            String data = String.valueOf(body.get("data"));
            if (StrUtil.isBlank(data) || "null".equalsIgnoreCase(data)) {
                throw new RuntimeException(I18nUtils.get("user.verification_code_error"));
            }
            return data;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("oauth.wechat_service_call_failed"), e);
        }
    }

    private void ensureTelegramSignatureValid(HttpServletRequest request) {
        String token = StrUtil.trimToEmpty(optionService.getValue("TelegramBotToken"));
        Map<String, String[]> params = request.getParameterMap();
        String hash = firstParam(params, "hash");
        if (StrUtil.isBlank(hash)) {
            throw new RuntimeException(I18nUtils.get("oauth.invalid_request"));
        }
        List<String> parts = new ArrayList<>();
        params.forEach((key, values) -> {
            if (!"hash".equals(key) && values != null && values.length > 0) {
                parts.add(key + "=" + values[0]);
            }
        });
        parts.sort(Comparator.naturalOrder());
        String dataCheckString = String.join("\n", parts);
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String calculated = HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
            if (!hash.equals(calculated)) {
                throw new RuntimeException(I18nUtils.get("oauth.invalid_request"));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("oauth.telegram_signature_failed"), e);
        }
    }

    private String firstParam(Map<String, String[]> params, String key) {
        String[] values = params.get(key);
        return values == null || values.length == 0 ? "" : values[0];
    }

    private boolean isUserFieldTaken(com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, String> field, String value) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(field, value)) > 0;
    }

    private User findActiveUserByField(com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, String> field, String value) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(field, value)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
    }

    private String extraString(OAuthRuntimeModels.OAuthUser oauthUser, String key) {
        Object value = oauthUser.getExtra().get(key);
        return value == null ? "" : String.valueOf(value);
    }

}
