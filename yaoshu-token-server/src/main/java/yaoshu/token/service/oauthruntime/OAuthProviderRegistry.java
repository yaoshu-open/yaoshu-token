package yaoshu.token.service.oauthruntime;

import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.CustomOAuthProviderMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserOAuthBindingMapper;
import yaoshu.token.pojo.entity.CustomOAuthProvider;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserOAuthBinding;
import yaoshu.token.service.OptionService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * OAuth Provider registry  */
@Service
@RequiredArgsConstructor
public class OAuthProviderRegistry {

    private final CustomOAuthProviderMapper providerMapper;
    private final UserOAuthBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final OptionService optionService;
    private final ObjectMapper objectMapper;

    public OAuthProvider getProvider(String slug) {
        return getProvidersSnapshot().get(StrUtil.trimToEmpty(slug).toLowerCase(Locale.ROOT));
    }

    public Map<String, OAuthProvider> getProvidersSnapshot() {
        Map<String, OAuthProvider> providers = new LinkedHashMap<>();
        providers.put("github", new GitHubOAuthProvider(optionService, userMapper, objectMapper));
        providers.put("discord", new DiscordOAuthProvider(optionService, userMapper, objectMapper));
        providers.put("linuxdo", new LinuxDoOAuthProvider(optionService, userMapper, objectMapper));
        providers.put("oidc", new OidcOAuthProvider(optionService, userMapper, objectMapper));

        List<CustomOAuthProvider> customProviders = providerMapper.selectList(new LambdaQueryWrapper<CustomOAuthProvider>()
                .eq(CustomOAuthProvider::getEnabled, true)
                .orderByAsc(CustomOAuthProvider::getId));
        for (CustomOAuthProvider customProvider : customProviders) {
            providers.put(customProvider.getSlug(), new GenericOAuthProvider(
                    customProvider, bindingMapper, userMapper, objectMapper, optionService));
        }
        return providers;
    }
}

abstract class AbstractOAuthProvider implements OAuthProvider {

    protected static final int USERNAME_MAX_LENGTH = 20;
    protected static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    protected final OptionService optionService;
    protected final UserMapper userMapper;
    protected final ObjectMapper objectMapper;

    protected AbstractOAuthProvider(OptionService optionService, UserMapper userMapper, ObjectMapper objectMapper) {
        this.optionService = optionService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    protected String getOption(String key) {
        return StrUtil.trimToEmpty(optionService.getValue(key));
    }

    protected boolean getBoolOption(String key) {
        return "true".equalsIgnoreCase(getOption(key));
    }

    protected int getIntOption(String key, int defaultValue) {
        return StrUtil.isBlank(getOption(key)) ? defaultValue : Integer.parseInt(getOption(key));
    }

    protected HttpResponse<String> send(HttpRequest request, String failMessage) {
        try {
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(failMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(failMessage, e);
        }
    }

    protected boolean isUserFieldTaken(SFunction<User, String> field, String value) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(field, value)) > 0;
    }

    protected User fillActiveUserByField(SFunction<User, String> field, String value) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(field, value)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
    }

    protected OAuthRuntimeModels.OAuthToken readToken(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            OAuthRuntimeModels.OAuthToken token = new OAuthRuntimeModels.OAuthToken();
            token.setAccessToken(readText(node, "access_token"));
            token.setTokenType(readText(node, "token_type"));
            token.setRefreshToken(readText(node, "refresh_token"));
            if (node.hasNonNull("expires_in")) {
                token.setExpiresIn(node.get("expires_in").asInt());
            }
            token.setScope(readText(node, "scope"));
            token.setIdToken(readText(node, "id_token"));
            return token;
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("oauth.token_parse_failed"), e);
        }
    }

    protected String readText(JsonNode node, String field) {
        JsonNode valueNode = node.get(field);
        return valueNode == null || valueNode.isNull() ? "" : valueNode.asText("");
    }

    protected String bearerTokenType(String tokenType) {
        return StrUtil.isBlank(tokenType) ? "Bearer" : tokenType;
    }
}

final class GitHubOAuthProvider extends AbstractOAuthProvider {

    GitHubOAuthProvider(OptionService optionService, UserMapper userMapper, ObjectMapper objectMapper) {
        super(optionService, userMapper, objectMapper);
    }

    @Override
    public String getName() {
        return "GitHub";
    }

    @Override
    public boolean isEnabled() {
        return getBoolOption("GitHubOAuthEnabled");
    }

    @Override
    public OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException(I18nUtils.get("oauth.invalid_code"));
        }
        String payload = "{\"client_id\":\"" + OAuthHttpSupport.escapeJson(getOption("GitHubClientId"))
                + "\",\"client_secret\":\"" + OAuthHttpSupport.escapeJson(getOption("GitHubClientSecret"))
                + "\",\"code\":\"" + OAuthHttpSupport.escapeJson(code) + "\"}";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 GitHub 服务器，请稍后重试！");
        OAuthRuntimeModels.OAuthToken token = readToken(response.body());
        if (StrUtil.isBlank(token.getAccessToken())) {
            throw new RuntimeException(I18nUtils.get("oauth.github_token_failed"));
        }
        return token;
    }

    @Override
    public OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .GET()
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 GitHub 服务器，请稍后重试！");
        try {
            JsonNode node = objectMapper.readTree(response.body());
            long id = node.path("id").asLong(0L);
            String login = node.path("login").asText("");
            if (id <= 0 || StrUtil.isBlank(login)) {
                throw new RuntimeException(I18nUtils.get("oauth.return_value_invalid"));
            }
            OAuthRuntimeModels.OAuthUser user = new OAuthRuntimeModels.OAuthUser();
            user.setProviderUserId(String.valueOf(id));
            user.setUsername(login);
            user.setDisplayName(node.path("name").asText(""));
            user.setEmail(node.path("email").asText(""));
            user.getExtra().put("legacy_id", login);
            return user;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.github_user_info_parse_failed"), e);
        }
    }

    @Override
    public boolean isUserIdTaken(String providerUserId) {
        return isUserFieldTaken(User::getGithubId, providerUserId);
    }

    @Override
    public User fillUserByProviderId(String providerUserId) {
        return fillActiveUserByField(User::getGithubId, providerUserId);
    }

    @Override
    public void setProviderUserId(User user, String providerUserId) {
        user.setGithubId(providerUserId);
    }

    @Override
    public String getProviderPrefix() {
        return "github_";
    }
}

final class DiscordOAuthProvider extends AbstractOAuthProvider {

    DiscordOAuthProvider(OptionService optionService, UserMapper userMapper, ObjectMapper objectMapper) {
        super(optionService, userMapper, objectMapper);
    }

    @Override
    public String getName() {
        return "Discord";
    }

    @Override
    public boolean isEnabled() {
        return getBoolOption("discord.enabled");
    }

    @Override
    public OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException(I18nUtils.get("oauth.invalid_code"));
        }
        String redirectUri = StrUtil.removeSuffix(getOption("ServerAddress"), "/") + "/oauth/discord";
        String form = OAuthHttpSupport.formBody(Map.of(
                "client_id", getOption("discord.client_id"),
                "client_secret", getOption("discord.client_secret"),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", redirectUri
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/oauth2/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 Discord 服务器，请稍后重试！");
        OAuthRuntimeModels.OAuthToken token = readToken(response.body());
        if (StrUtil.isBlank(token.getAccessToken())) {
            throw new RuntimeException(I18nUtils.get("oauth.discord_token_failed"));
        }
        return token;
    }

    @Override
    public OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .GET()
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 Discord 服务器，请稍后重试！");
        if (response.statusCode() != 200) {
            throw new RuntimeException(I18nUtils.get("oauth.discord_user_info_failed"));
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String uid = node.path("id").asText("");
            String username = node.path("username").asText("");
            if (StrUtil.isBlank(uid) || StrUtil.isBlank(username)) {
                throw new RuntimeException(I18nUtils.get("oauth.discord_user_info_empty"));
            }
            OAuthRuntimeModels.OAuthUser user = new OAuthRuntimeModels.OAuthUser();
            user.setProviderUserId(uid);
            user.setUsername(username);
            user.setDisplayName(node.path("global_name").asText(""));
            return user;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.discord_user_info_parse_failed"), e);
        }
    }

    @Override
    public boolean isUserIdTaken(String providerUserId) {
        return isUserFieldTaken(User::getDiscordId, providerUserId);
    }

    @Override
    public User fillUserByProviderId(String providerUserId) {
        return fillActiveUserByField(User::getDiscordId, providerUserId);
    }

    @Override
    public void setProviderUserId(User user, String providerUserId) {
        user.setDiscordId(providerUserId);
    }

    @Override
    public String getProviderPrefix() {
        return "discord_";
    }
}

final class LinuxDoOAuthProvider extends AbstractOAuthProvider {

    LinuxDoOAuthProvider(OptionService optionService, UserMapper userMapper, ObjectMapper objectMapper) {
        super(optionService, userMapper, objectMapper);
    }

    @Override
    public String getName() {
        return "Linux DO";
    }

    @Override
    public boolean isEnabled() {
        return getBoolOption("LinuxDOOAuthEnabled");
    }

    @Override
    public OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException("invalid code");
        }
        String tokenEndpoint = StrUtilCompat.blankToDefault(System.getenv("LINUX_DO_TOKEN_ENDPOINT"),
                "https://connect.linux.do/oauth2/token");
        String redirectUri = request.getScheme() + "://" + request.getHeader("Host") + "/api/oauth/linuxdo";
        String form = OAuthHttpSupport.formBody(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri
        ));
        String credentials = getOption("LinuxDOClientId") + ":" + getOption("LinuxDOClientSecret");
        String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorization)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = send(httpRequest, "failed to connect to Linux DO server");
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String accessToken = node.path("access_token").asText("");
            if (StrUtil.isBlank(accessToken)) {
                throw new RuntimeException("failed to get access token: " + node.path("message").asText(""));
            }
            OAuthRuntimeModels.OAuthToken token = new OAuthRuntimeModels.OAuthToken();
            token.setAccessToken(accessToken);
            return token;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.linuxdo_token_parse_failed"), e);
        }
    }

    @Override
    public OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request) {
        String userEndpoint = StrUtilCompat.blankToDefault(System.getenv("LINUX_DO_USER_ENDPOINT"),
                "https://connect.linux.do/api/user");
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(userEndpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(httpRequest, "failed to get user info from Linux DO");
        try {
            JsonNode node = objectMapper.readTree(response.body());
            int id = node.path("id").asInt(0);
            if (id <= 0) {
                throw new RuntimeException("invalid user info returned");
            }
            int trustLevel = node.path("trust_level").asInt(0);
            int minimumTrustLevel = getIntOption("LinuxDOMinimumTrustLevel", 0);
            if (trustLevel < minimumTrustLevel) {
                throw new RuntimeException(I18nUtils.get("oauth.linuxdo_trust_level_low"));
            }
            OAuthRuntimeModels.OAuthUser user = new OAuthRuntimeModels.OAuthUser();
            user.setProviderUserId(String.valueOf(id));
            user.setUsername(node.path("username").asText(""));
            user.setDisplayName(node.path("name").asText(""));
            user.getExtra().put("trust_level", trustLevel);
            user.getExtra().put("active", node.path("active").asBoolean(false));
            user.getExtra().put("silenced", node.path("silenced").asBoolean(false));
            return user;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.linuxdo_user_info_parse_failed"), e);
        }
    }

    @Override
    public boolean isUserIdTaken(String providerUserId) {
        return isUserFieldTaken(User::getLinuxDoId, providerUserId);
    }

    @Override
    public User fillUserByProviderId(String providerUserId) {
        return fillActiveUserByField(User::getLinuxDoId, providerUserId);
    }

    @Override
    public void setProviderUserId(User user, String providerUserId) {
        user.setLinuxDoId(providerUserId);
    }

    @Override
    public String getProviderPrefix() {
        return "linuxdo_";
    }
}

final class OidcOAuthProvider extends AbstractOAuthProvider {

    OidcOAuthProvider(OptionService optionService, UserMapper userMapper, ObjectMapper objectMapper) {
        super(optionService, userMapper, objectMapper);
    }

    @Override
    public String getName() {
        return "OIDC";
    }

    @Override
    public boolean isEnabled() {
        return getBoolOption("oidc.enabled");
    }

    @Override
    public OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        String redirectUri = StrUtil.removeSuffix(getOption("ServerAddress"), "/") + "/oauth/oidc";
        String form = OAuthHttpSupport.formBody(Map.of(
                "client_id", getOption("oidc.client_id"),
                "client_secret", getOption("oidc.client_secret"),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", redirectUri
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(getOption("oidc.token_endpoint")))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 OIDC 服务器，请稍后重试！");
        OAuthRuntimeModels.OAuthToken token = readToken(response.body());
        if (StrUtil.isBlank(token.getAccessToken())) {
            throw new RuntimeException(I18nUtils.get("oauth.oidc_token_failed"));
        }
        return token;
    }

    @Override
    public OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(getOption("oidc.user_info_endpoint")))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .GET()
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 OIDC 服务器，请稍后重试！");
        if (response.statusCode() != 200) {
            throw new RuntimeException(I18nUtils.get("oauth.oidc_user_info_failed"));
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String openId = node.path("sub").asText("");
            String email = node.path("email").asText("");
            if (StrUtil.isBlank(openId) || StrUtil.isBlank(email)) {
                throw new RuntimeException(I18nUtils.get("oauth.oidc_user_info_empty"));
            }
            OAuthRuntimeModels.OAuthUser user = new OAuthRuntimeModels.OAuthUser();
            user.setProviderUserId(openId);
            user.setUsername(node.path("preferred_username").asText(""));
            user.setDisplayName(node.path("name").asText(""));
            user.setEmail(email);
            return user;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.oidc_user_info_parse_failed"), e);
        }
    }

    @Override
    public boolean isUserIdTaken(String providerUserId) {
        return isUserFieldTaken(User::getOidcId, providerUserId);
    }

    @Override
    public User fillUserByProviderId(String providerUserId) {
        return fillActiveUserByField(User::getOidcId, providerUserId);
    }

    @Override
    public void setProviderUserId(User user, String providerUserId) {
        user.setOidcId(providerUserId);
    }

    @Override
    public String getProviderPrefix() {
        return "oidc_";
    }
}

final class GenericOAuthProvider implements OAuthProvider {

    private final CustomOAuthProvider config;
    private final UserOAuthBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final OptionService optionService;

    GenericOAuthProvider(CustomOAuthProvider config,
                         UserOAuthBindingMapper bindingMapper,
                         UserMapper userMapper,
                         ObjectMapper objectMapper,
                         OptionService optionService) {
        this.config = config;
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.optionService = optionService;
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(config.getEnabled());
    }

    @Override
    public OAuthRuntimeModels.OAuthToken exchangeToken(String code, HttpServletRequest request) {
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException(I18nUtils.get("oauth.invalid_code"));
        }
        String redirectUri = StrUtil.removeSuffix(getServerAddress(request), "/") + "/oauth/" + config.getSlug();
        Map<String, String> formMap = new LinkedHashMap<>();
        formMap.put("grant_type", "authorization_code");
        formMap.put("code", code);
        formMap.put("redirect_uri", redirectUri);
        if (!Integer.valueOf(2).equals(config.getAuthStyle())) {
            formMap.put("client_id", config.getClientId());
            formMap.put("client_secret", config.getClientSecret());
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(config.getTokenEndpoint()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        if (Integer.valueOf(2).equals(config.getAuthStyle())) {
            String credentials = config.getClientId() + ":" + config.getClientSecret();
            requestBuilder.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        HttpRequest httpRequest = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                OAuthHttpSupport.formBody(formMap))).build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 " + config.getName() + " 服务器，请稍后重试！");
        OAuthRuntimeModels.OAuthToken token = readToken(response.body());
        if (StrUtil.isBlank(token.getAccessToken())) {
            throw new RuntimeException(I18nUtils.get("oauth.custom_oauth_token_failed", config.getName()));
        }
        return token;
    }

    @Override
    public OAuthRuntimeModels.OAuthUser getUserInfo(OAuthRuntimeModels.OAuthToken token, HttpServletRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getUserInfoEndpoint()))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", bearerTokenType(token.getTokenType()) + " " + token.getAccessToken())
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(httpRequest, "无法连接至 " + config.getName() + " 服务器，请稍后重试！");
        if (response.statusCode() != 200) {
            throw new RuntimeException(I18nUtils.get("oauth.custom_oauth_user_info_failed", config.getName()));
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String providerUserId = stringValue(resolvePath(node, config.getUserIdField()));
            String username = stringValue(resolvePath(node, config.getUsernameField()));
            String displayName = stringValue(resolvePath(node, config.getDisplayNameField()));
            String email = stringValue(resolvePath(node, config.getEmailField()));
            if (StrUtil.isBlank(providerUserId)) {
                throw new RuntimeException(I18nUtils.get("oauth.custom_oauth_user_id_empty", config.getName()));
            }
            enforceAccessPolicy(node);
            OAuthRuntimeModels.OAuthUser user = new OAuthRuntimeModels.OAuthUser();
            user.setProviderUserId(providerUserId);
            user.setUsername(username);
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.getExtra().put("provider", config.getSlug());
            return user;
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.custom_oauth_user_info_parse_failed", config.getName()), e);
        }
    }

    @Override
    public boolean isUserIdTaken(String providerUserId) {
        return bindingMapper.selectCount(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getProviderId, config.getId())
                .eq(UserOAuthBinding::getProviderUserId, providerUserId)) > 0;
    }

    @Override
    public User fillUserByProviderId(String providerUserId) {
        UserOAuthBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<UserOAuthBinding>()
                .eq(UserOAuthBinding::getProviderId, config.getId())
                .eq(UserOAuthBinding::getProviderUserId, providerUserId)
                .last("LIMIT 1"));
        if (binding == null) {
            return null;
        }
        User user = userMapper.selectById(binding.getUserId());
        return user != null && user.getDeletedAt() == null ? user : null;
    }

    @Override
    public void setProviderUserId(User user, String providerUserId) {
        // 自定义 Provider 绑定写入 user_oauth_bindings，由编排 service 统一处理。
    }

    @Override
    public String getProviderPrefix() {
        return config.getSlug() + "_";
    }

    @Override
    public boolean isGenericProvider() {
        return true;
    }

    @Override
    public Integer getProviderId() {
        return config.getId();
    }

    private HttpResponse<String> send(HttpRequest request, String failMessage) {
        try {
            return AbstractOAuthProvider.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(failMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(failMessage, e);
        }
    }

    private OAuthRuntimeModels.OAuthToken readToken(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            OAuthRuntimeModels.OAuthToken token = new OAuthRuntimeModels.OAuthToken();
            token.setAccessToken(node.path("access_token").asText(""));
            token.setTokenType(node.path("token_type").asText(""));
            token.setRefreshToken(node.path("refresh_token").asText(""));
            if (node.hasNonNull("expires_in")) {
                token.setExpiresIn(node.get("expires_in").asInt());
            }
            token.setScope(node.path("scope").asText(""));
            token.setIdToken(node.path("id_token").asText(""));
            return token;
        } catch (IOException jsonError) {
            Map<String, String> queryMap = parseUrlEncodedBody(body);
            OAuthRuntimeModels.OAuthToken token = new OAuthRuntimeModels.OAuthToken();
            token.setAccessToken(StrUtil.trimToEmpty(queryMap.get("access_token")));
            token.setTokenType(StrUtil.trimToEmpty(queryMap.get("token_type")));
            token.setScope(StrUtil.trimToEmpty(queryMap.get("scope")));
            return token;
        }
    }

    private String bearerTokenType(String tokenType) {
        return StrUtil.isBlank(tokenType) ? "Bearer" : tokenType;
    }

    private String getServerAddress(HttpServletRequest request) {
        String serverAddress = StrUtil.trimToEmpty(optionService.getValue("ServerAddress"));
        if (StrUtil.isBlank(serverAddress)) {
            serverAddress = request.getScheme() + "://" + request.getHeader("Host");
        }
        return serverAddress;
    }

    private void enforceAccessPolicy(JsonNode root) {
        String policy = StrUtil.trimToEmpty(config.getAccessPolicy());
        if (policy.isEmpty()) {
            return;
        }
        try {
            JsonNode policyNode = objectMapper.readTree(policy);
            if (!matchesPolicy(root, policyNode)) {
                throw new RuntimeException(renderDeniedMessage(root, policyNode));
            }
        } catch (IOException e) {
            throw new RuntimeException(I18nUtils.get("oauth.custom_oauth_access_policy_invalid"), e);
        }
    }

    private boolean matchesPolicy(JsonNode root, JsonNode policyNode) {
        String logic = policyNode.path("logic").asText("and").trim().toLowerCase(Locale.ROOT);
        List<Boolean> results = new ArrayList<>();
        JsonNode conditions = policyNode.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode condition : conditions) {
                results.add(matchesCondition(root, condition));
            }
        }
        JsonNode groups = policyNode.path("groups");
        if (groups.isArray()) {
            for (JsonNode group : groups) {
                results.add(matchesPolicy(root, group));
            }
        }
        if (results.isEmpty()) {
            return true;
        }
        if ("or".equals(logic)) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

    private boolean matchesCondition(JsonNode root, JsonNode condition) {
        JsonNode current = resolvePath(root, condition.path("field").asText(""));
        String op = condition.path("op").asText("").trim().toLowerCase(Locale.ROOT);
        JsonNode expected = condition.path("value");
        return switch (op) {
            case "exists" -> !current.isMissingNode() && !current.isNull();
            case "not_exists" -> current.isMissingNode() || current.isNull();
            case "eq" -> compare(current, expected) == 0;
            case "ne" -> compare(current, expected) != 0;
            case "gt" -> compare(current, expected) > 0;
            case "gte" -> compare(current, expected) >= 0;
            case "lt" -> compare(current, expected) < 0;
            case "lte" -> compare(current, expected) <= 0;
            case "in" -> contains(expected, current);
            case "not_in" -> !contains(expected, current);
            case "contains" -> contains(current, expected);
            case "not_contains" -> !contains(current, expected);
            default -> false;
        };
    }

    private int compare(JsonNode current, JsonNode expected) {
        Double currentNumber = asNumber(current);
        Double expectedNumber = asNumber(expected);
        if (currentNumber != null && expectedNumber != null) {
            return Double.compare(currentNumber, expectedNumber);
        }
        return stringValue(current).compareTo(stringValue(expected));
    }

    private boolean contains(JsonNode container, JsonNode target) {
        if (container == null || container.isMissingNode() || container.isNull()) {
            return false;
        }
        if (container.isArray()) {
            for (JsonNode item : container) {
                if (compare(item, target) == 0) {
                    return true;
                }
            }
            return false;
        }
        return stringValue(container).contains(stringValue(target));
    }

    private Double asNumber(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual() && StrUtil.isNotBlank(node.asText())) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String renderDeniedMessage(JsonNode root, JsonNode policyNode) {
        String message = StrUtil.trimToEmpty(config.getAccessDeniedMessage());
        if (message.isEmpty()) {
            return "Access denied: your account does not meet this provider's access requirements.";
        }
        String field = firstField(policyNode);
        return message
                .replace("{{provider}}", config.getName())
                .replace("{{field}}", field)
                .replace("{{current}}", stringValue(resolvePath(root, field)));
    }

    private String firstField(JsonNode policyNode) {
        JsonNode conditions = policyNode.path("conditions");
        if (conditions.isArray() && !conditions.isEmpty()) {
            return conditions.get(0).path("field").asText("");
        }
        JsonNode groups = policyNode.path("groups");
        if (groups.isArray() && !groups.isEmpty()) {
            return firstField(groups.get(0));
        }
        return "";
    }

    private JsonNode resolvePath(JsonNode root, String path) {
        if (root == null || StrUtil.isBlank(path)) {
            return root == null ? objectMapper.getNodeFactory().missingNode() : root;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return objectMapper.getNodeFactory().missingNode();
            }
            String remaining = segment;
            while (remaining.contains("[") && remaining.endsWith("]")) {
                int bracketIndex = remaining.indexOf('[');
                String field = bracketIndex > 0 ? remaining.substring(0, bracketIndex) : "";
                if (StrUtil.isNotBlank(field)) {
                    current = current.path(field);
                }
                int endIndex = remaining.indexOf(']');
                int arrayIndex = Integer.parseInt(remaining.substring(bracketIndex + 1, endIndex));
                current = current.path(arrayIndex);
                remaining = endIndex + 1 < remaining.length() ? remaining.substring(endIndex + 1) : "";
            }
            if (StrUtil.isNotBlank(remaining)) {
                current = current.path(remaining);
            }
        }
        return current == null ? objectMapper.getNodeFactory().missingNode() : current;
    }

    private String stringValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return node.toString().replace("\"", "");
    }

    private Map<String, String> parseUrlEncodedBody(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (StrUtil.isBlank(pair)) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            result.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }
}

final class OAuthHttpSupport {

    private OAuthHttpSupport() {
    }

    static String formBody(Map<String, String> values) {
        List<String> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(StrUtilCompat.nullToEmpty(value), StandardCharsets.UTF_8)));
        return String.join("&", pairs);
    }

    static String escapeJson(String value) {
        return StrUtilCompat.nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
