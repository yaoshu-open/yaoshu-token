package yaoshu.token.service;

import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import jakarta.validation.constraints.NotBlank;
import ai.yue.library.base.util.I18nUtils;
import lombok.Data;
import ai.yue.library.base.util.I18nUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.mapper.CustomOAuthProviderMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserOAuthBindingMapper;
import yaoshu.token.pojo.entity.CustomOAuthProvider;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserOAuthBinding;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * OAuth 管理服务：承接自定义 Provider CRUD、用户 OAuth 绑定查询/解绑，以及管理侧内置绑定清理。
 * <p>
 * 仅翻译当前 Java 已具备数据模型支撑的 Go 能力，不在此处扩展通用 OAuth 登录编排。
 */
@Service
@RequiredArgsConstructor
public class OAuthManagementService {

    private static final Set<String> BUILTIN_PROVIDER_SLUGS = Set.of(
            "github", "discord", "linuxdo", "oidc", "wechat", "telegram");

    private static final Set<String> SUPPORTED_ACCESS_POLICY_OPS = Set.of(
            "eq", "ne", "gt", "gte", "lt", "lte",
            "in", "not_in", "contains", "not_contains",
            "exists", "not_exists");

    private final CustomOAuthProviderMapper providerMapper;
    private final UserOAuthBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public List<CustomOAuthProviderView> listProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<CustomOAuthProvider>()
                        .orderByAsc(CustomOAuthProvider::getId))
                .stream()
                .map(this::toProviderView)
                .toList();
    }

    public CustomOAuthProviderView getProvider(int id) {
        return toProviderView(requireProvider(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomOAuthProviderView createProvider(CreateProviderCommand command) {
        CustomOAuthProvider provider = new CustomOAuthProvider();
        provider.setName(command.getName());
        provider.setSlug(command.getSlug());
        provider.setIcon(command.getIcon());
        provider.setEnabled(Boolean.TRUE.equals(command.getEnabled()));
        provider.setClientId(command.getClientId());
        provider.setClientSecret(command.getClientSecret());
        provider.setAuthorizationEndpoint(command.getAuthorizationEndpoint());
        provider.setTokenEndpoint(command.getTokenEndpoint());
        provider.setUserInfoEndpoint(command.getUserInfoEndpoint());
        provider.setScopes(command.getScopes());
        provider.setUserIdField(command.getUserIdField());
        provider.setUsernameField(command.getUsernameField());
        provider.setDisplayNameField(command.getDisplayNameField());
        provider.setEmailField(command.getEmailField());
        provider.setWellKnown(command.getWellKnown());
        provider.setAuthStyle(command.getAuthStyle());
        provider.setAccessPolicy(command.getAccessPolicy());
        provider.setAccessDeniedMessage(command.getAccessDeniedMessage());
        normalizeAndValidateProvider(provider, 0);
        providerMapper.insert(provider);
        return toProviderView(provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomOAuthProviderView updateProvider(int id, UpdateProviderCommand command) {
        CustomOAuthProvider provider = requireProvider(id);
        if (command.getName() != null) {
            provider.setName(command.getName());
        }
        if (command.getSlug() != null) {
            provider.setSlug(command.getSlug());
        }
        if (command.getIcon() != null) {
            provider.setIcon(command.getIcon());
        }
        if (command.getEnabled() != null) {
            provider.setEnabled(command.getEnabled());
        }
        if (command.getClientId() != null) {
            provider.setClientId(command.getClientId());
        }
        if (command.getClientSecret() != null) {
            provider.setClientSecret(command.getClientSecret());
        }
        if (command.getAuthorizationEndpoint() != null) {
            provider.setAuthorizationEndpoint(command.getAuthorizationEndpoint());
        }
        if (command.getTokenEndpoint() != null) {
            provider.setTokenEndpoint(command.getTokenEndpoint());
        }
        if (command.getUserInfoEndpoint() != null) {
            provider.setUserInfoEndpoint(command.getUserInfoEndpoint());
        }
        if (command.getScopes() != null) {
            provider.setScopes(command.getScopes());
        }
        if (command.getUserIdField() != null) {
            provider.setUserIdField(command.getUserIdField());
        }
        if (command.getUsernameField() != null) {
            provider.setUsernameField(command.getUsernameField());
        }
        if (command.getDisplayNameField() != null) {
            provider.setDisplayNameField(command.getDisplayNameField());
        }
        if (command.getEmailField() != null) {
            provider.setEmailField(command.getEmailField());
        }
        if (command.getWellKnown() != null) {
            provider.setWellKnown(command.getWellKnown());
        }
        if (command.getAuthStyle() != null) {
            provider.setAuthStyle(command.getAuthStyle());
        }
        if (command.getAccessPolicy() != null) {
            provider.setAccessPolicy(command.getAccessPolicy());
        }
        if (command.getAccessDeniedMessage() != null) {
            provider.setAccessDeniedMessage(command.getAccessDeniedMessage());
        }
        normalizeAndValidateProvider(provider, id);
        providerMapper.updateById(provider);
        return toProviderView(provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProvider(int id) {
        requireProvider(id);
        long bindingCount = bindingMapper.selectCount(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getProviderId, id));
        if (bindingCount > 0) {
            throw new RuntimeException(I18nUtils.get("oauth.provider_has_bindings"));
        }
        providerMapper.deleteById(id);
    }

    public Map<String, Object> fetchDiscovery(DiscoveryCommand command) {
        String wellKnownUrl = StrUtil.trimToEmpty(command.getWellKnownUrl());
        String issuerUrl = StrUtil.trimToEmpty(command.getIssuerUrl());
        if (wellKnownUrl.isEmpty() && issuerUrl.isEmpty()) {
            throw new RuntimeException(I18nUtils.get("oauth.discovery_url_empty"));
        }

        String targetUrl = wellKnownUrl;
        if (targetUrl.isEmpty()) {
            targetUrl = StrUtil.removeSuffix(issuerUrl, "/") + "/.well-known/openid-configuration";
        }
        validateHttpUrl(targetUrl);

        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String message = StrUtilCompat.blankToDefault(StrUtil.trim(response.body()), "HTTP " + response.statusCode());
                throw new RuntimeException(I18nUtils.get("oauth.discovery_fetch_failed", message));
            }
            Map<String, Object> discovery = Convert.toJSONObject(response.body());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("well_known_url", targetUrl);
            result.put("discovery", discovery);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.discovery_parse_failed", e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(I18nUtils.get("oauth.discovery_request_interrupted"), e);
        }
    }

    public List<UserOAuthBindingView> listUserBindings(int userId) {
        List<UserOAuthBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getUserId, userId)
                .orderByAsc(UserOAuthBinding::getProviderId));
        if (bindings.isEmpty()) {
            return List.of();
        }

        Set<Integer> providerIds = bindings.stream()
                .map(UserOAuthBinding::getProviderId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, CustomOAuthProvider> providerMap = providerMapper.selectList(
                        new LambdaQueryWrapper<CustomOAuthProvider>().in(CustomOAuthProvider::getId, providerIds))
                .stream()
                .collect(Collectors.toMap(CustomOAuthProvider::getId, p -> p));

        List<UserOAuthBindingView> result = new ArrayList<>();
        for (UserOAuthBinding binding : bindings) {
            CustomOAuthProvider provider = providerMap.get(binding.getProviderId());
            if (provider == null) {
                continue;
            }
            UserOAuthBindingView view = new UserOAuthBindingView();
            view.setProviderId(binding.getProviderId());
            view.setProviderName(provider.getName());
            view.setProviderSlug(provider.getSlug());
            view.setProviderIcon(provider.getIcon());
            view.setProviderUserId(binding.getProviderUserId());
            result.add(view);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbindUserOAuth(int userId, int providerId) {
        bindingMapper.delete(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getUserId, userId)
                .eq(UserOAuthBinding::getProviderId, providerId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearUserBinding(int userId, String bindingType) {
        requireUser(userId);
        String normalizedType = StrUtil.trimToEmpty(bindingType).toLowerCase(Locale.ROOT);
        boolean updated = switch (normalizedType) {
            case "email" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getEmail, "")) > 0;
            case "github" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getGithubId, "")) > 0;
            case "discord" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getDiscordId, "")) > 0;
            case "oidc" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getOidcId, "")) > 0;
            case "wechat" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getWechatId, "")) > 0;
            case "telegram" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getTelegramId, "")) > 0;
            case "linuxdo" -> userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getLinuxDoId, "")) > 0;
            default -> throw new RuntimeException(I18nUtils.get("oauth.invalid_binding_type"));
        };
        if (!updated) {
            throw new RuntimeException(I18nUtils.get("admin.user_not_exists"));
        }
    }

    private CustomOAuthProvider requireProvider(int id) {
        if (id <= 0) {
            throw new RuntimeException(I18nUtils.get("oauth.invalid_id"));
        }
        CustomOAuthProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new RuntimeException(I18nUtils.get("oauth.provider_not_found"));
        }
        return provider;
    }

    private void requireUser(int userId) {
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("admin.user_not_exists"));
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException(I18nUtils.get("admin.user_not_exists"));
        }
    }

    private void normalizeAndValidateProvider(CustomOAuthProvider provider, int excludeId) {
        String name = StrUtil.trimToEmpty(provider.getName());
        if (name.isEmpty()) {
            throw new RuntimeException("provider name is required");
        }
        String slug = StrUtil.trimToEmpty(provider.getSlug()).toLowerCase(Locale.ROOT);
        if (slug.isEmpty()) {
            throw new RuntimeException("provider slug is required");
        }
        for (int i = 0; i < slug.length(); i++) {
            char current = slug.charAt(i);
            if (!(current >= 'a' && current <= 'z')
                    && !(current >= '0' && current <= '9')
                    && current != '-') {
                throw new RuntimeException("provider slug must contain only lowercase letters, numbers, and hyphens");
            }
        }
        if (BUILTIN_PROVIDER_SLUGS.contains(slug)) {
            throw new RuntimeException(I18nUtils.get("oauth.slug_conflict_builtin"));
        }

        long slugCount = providerMapper.selectCount(new LambdaQueryWrapper<CustomOAuthProvider>()
                .eq(CustomOAuthProvider::getSlug, slug)
                .ne(excludeId > 0, CustomOAuthProvider::getId, excludeId));
        if (slugCount > 0) {
            throw new RuntimeException(I18nUtils.get("oauth.slug_already_used"));
        }

        if (StrUtil.trimToEmpty(provider.getClientId()).isEmpty()) {
            throw new RuntimeException("client ID is required");
        }
        if (StrUtil.trimToEmpty(provider.getClientSecret()).isEmpty()) {
            throw new RuntimeException("client secret is required");
        }
        if (StrUtil.trimToEmpty(provider.getAuthorizationEndpoint()).isEmpty()) {
            throw new RuntimeException("authorization endpoint is required");
        }
        if (StrUtil.trimToEmpty(provider.getTokenEndpoint()).isEmpty()) {
            throw new RuntimeException("token endpoint is required");
        }
        if (StrUtil.trimToEmpty(provider.getUserInfoEndpoint()).isEmpty()) {
            throw new RuntimeException("user info endpoint is required");
        }

        validateHttpUrl(provider.getAuthorizationEndpoint());
        validateHttpUrl(provider.getTokenEndpoint());
        validateHttpUrl(provider.getUserInfoEndpoint());
        if (StrUtil.isNotBlank(provider.getWellKnown())) {
            validateHttpUrl(provider.getWellKnown());
        }
        validateAccessPolicy(provider.getAccessPolicy());

        provider.setName(name);
        provider.setSlug(slug);
        provider.setIcon(StrUtilCompat.nullToDefault(provider.getIcon(), ""));
        provider.setClientId(provider.getClientId().trim());
        provider.setClientSecret(provider.getClientSecret().trim());
        provider.setAuthorizationEndpoint(provider.getAuthorizationEndpoint().trim());
        provider.setTokenEndpoint(provider.getTokenEndpoint().trim());
        provider.setUserInfoEndpoint(provider.getUserInfoEndpoint().trim());
        provider.setScopes(StrUtilCompat.blankToDefault(StrUtil.trim(provider.getScopes()), "openid profile email"));
        provider.setUserIdField(StrUtilCompat.blankToDefault(StrUtil.trim(provider.getUserIdField()), "sub"));
        provider.setUsernameField(StrUtilCompat.blankToDefault(StrUtil.trim(provider.getUsernameField()), "preferred_username"));
        provider.setDisplayNameField(StrUtilCompat.blankToDefault(StrUtil.trim(provider.getDisplayNameField()), "name"));
        provider.setEmailField(StrUtilCompat.blankToDefault(StrUtil.trim(provider.getEmailField()), "email"));
        provider.setWellKnown(StrUtilCompat.emptyToNull(StrUtil.trim(provider.getWellKnown())));
        if (provider.getAuthStyle() == null) {
            provider.setAuthStyle(0);
        }
        provider.setAccessPolicy(StrUtilCompat.emptyToNull(StrUtil.trim(provider.getAccessPolicy())));
        provider.setAccessDeniedMessage(StrUtilCompat.emptyToNull(StrUtil.trim(provider.getAccessDeniedMessage())));
        if (provider.getEnabled() == null) {
            provider.setEnabled(false);
        }
    }

    private void validateAccessPolicy(String accessPolicy) {
        String trimmed = StrUtil.trimToEmpty(accessPolicy);
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            validateAccessPolicyNode(root, "policy");
        } catch (IOException e) {
            throw new RuntimeException("access_policy must be valid JSON", e);
        }
    }

    private void validateAccessPolicyNode(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new RuntimeException(path + " must be an object");
        }
        String logic = node.path("logic").asText("and").trim().toLowerCase(Locale.ROOT);
        if (!"and".equals(logic) && !"or".equals(logic)) {
            throw new RuntimeException(path + ".logic is unsupported: " + logic);
        }

        JsonNode conditions = node.path("conditions");
        JsonNode groups = node.path("groups");
        boolean hasConditions = conditions.isArray() && !conditions.isEmpty();
        boolean hasGroups = groups.isArray() && !groups.isEmpty();
        if (!hasConditions && !hasGroups) {
            throw new RuntimeException(path + " requires at least one condition or group");
        }

        if (hasConditions) {
            for (int index = 0; index < conditions.size(); index++) {
                JsonNode condition = conditions.get(index);
                String field = condition.path("field").asText("").trim();
                if (field.isEmpty()) {
                    throw new RuntimeException(path + ".conditions[" + index + "].field is required");
                }
                String op = condition.path("op").asText("").trim().toLowerCase(Locale.ROOT);
                if (!SUPPORTED_ACCESS_POLICY_OPS.contains(op)) {
                    throw new RuntimeException(path + ".conditions[" + index + "].op is unsupported: " + op);
                }
                if (("in".equals(op) || "not_in".equals(op)) && !condition.path("value").isArray()) {
                    throw new RuntimeException(path + ".conditions[" + index + "].value must be an array for op " + op);
                }
            }
        }

        if (hasGroups) {
            for (int index = 0; index < groups.size(); index++) {
                validateAccessPolicyNode(groups.get(index), path + ".groups[" + index + "]");
            }
        }
    }

    private void validateHttpUrl(String value) {
        String url = StrUtil.trimToEmpty(value);
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new RuntimeException(I18nUtils.get("oauth.only_http_https_url"));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(I18nUtils.get("oauth.url_invalid", url), e);
        }
    }

    private CustomOAuthProviderView toProviderView(CustomOAuthProvider provider) {
        CustomOAuthProviderView view = new CustomOAuthProviderView();
        view.setId(provider.getId());
        view.setName(provider.getName());
        view.setSlug(provider.getSlug());
        view.setIcon(provider.getIcon());
        view.setEnabled(Boolean.TRUE.equals(provider.getEnabled()));
        view.setClientId(provider.getClientId());
        view.setAuthorizationEndpoint(provider.getAuthorizationEndpoint());
        view.setTokenEndpoint(provider.getTokenEndpoint());
        view.setUserInfoEndpoint(provider.getUserInfoEndpoint());
        view.setScopes(provider.getScopes());
        view.setUserIdField(provider.getUserIdField());
        view.setUsernameField(provider.getUsernameField());
        view.setDisplayNameField(provider.getDisplayNameField());
        view.setEmailField(provider.getEmailField());
        view.setWellKnown(provider.getWellKnown());
        view.setAuthStyle(provider.getAuthStyle());
        view.setAccessPolicy(provider.getAccessPolicy());
        view.setAccessDeniedMessage(provider.getAccessDeniedMessage());
        return view;
    }

    @Data
    public static class DiscoveryCommand {
        private String wellKnownUrl;
        private String issuerUrl;
    }

    @Data
    public static class CreateProviderCommand {
        @NotBlank(message = "name 不能为空")
        private String name;

        @NotBlank(message = "slug 不能为空")
        private String slug;

        private String icon;
        private Boolean enabled;

        @NotBlank(message = "client_id 不能为空")
        private String clientId;

        @NotBlank(message = "client_secret 不能为空")
        private String clientSecret;

        @NotBlank(message = "authorization_endpoint 不能为空")
        private String authorizationEndpoint;

        @NotBlank(message = "token_endpoint 不能为空")
        private String tokenEndpoint;

        @NotBlank(message = "user_info_endpoint 不能为空")
        private String userInfoEndpoint;

        private String scopes;
        private String userIdField;
        private String usernameField;
        private String displayNameField;
        private String emailField;
        private String wellKnown;
        private Integer authStyle;
        private String accessPolicy;
        private String accessDeniedMessage;
    }

    @Data
    public static class UpdateProviderCommand {
        private String name;
        private String slug;
        private String icon;
        private Boolean enabled;
        private String clientId;
        private String clientSecret;
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String userInfoEndpoint;
        private String scopes;
        private String userIdField;
        private String usernameField;
        private String displayNameField;
        private String emailField;
        private String wellKnown;
        private Integer authStyle;
        private String accessPolicy;
        private String accessDeniedMessage;
    }

    @Data
    public static class CustomOAuthProviderView {
        private Integer id;
        private String name;
        private String slug;
        private String icon;
        private Boolean enabled;
        private String clientId;
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String userInfoEndpoint;
        private String scopes;
        private String userIdField;
        private String usernameField;
        private String displayNameField;
        private String emailField;
        private String wellKnown;
        private Integer authStyle;
        private String accessPolicy;
        private String accessDeniedMessage;
    }

    @Data
    public static class UserOAuthBindingView {
        private Integer providerId;
        private String providerName;
        private String providerSlug;
        private String providerIcon;
        private String providerUserId;
    }
}
