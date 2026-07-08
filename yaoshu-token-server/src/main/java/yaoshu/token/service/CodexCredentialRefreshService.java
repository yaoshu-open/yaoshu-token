package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.pojo.dto.ChannelSettingsDTO;
import yaoshu.token.pojo.entity.Channel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codex 凭证刷新服务  * <p>
 * 从渠道 key 中解析 OAuth 凭证，使用 refresh_token 刷新 access_token，再回写渠道 key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodexCredentialRefreshService {

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ChannelMapper channelMapper;
    private final CodexOAuthService codexOAuthService;
    private final ProxyClientCacheService proxyClientCacheService;
    /**
     * Codex OAuth 凭证结构      */
    public static class CodexOAuthKey {
        private String idToken;
        private String accessToken;
        private String refreshToken;
        private String accountId;
        private String lastRefresh;
        private String email;
        private String type;
        private String expired;

        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getLastRefresh() { return lastRefresh; }
        public void setLastRefresh(String lastRefresh) { this.lastRefresh = lastRefresh; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getExpired() { return expired; }
        public void setExpired(String expired) { this.expired = expired; }
    }

    /**
     * 刷新凭证      *
     * @param refreshToken 刷新令牌
     * @return 新的 access_token，失败返回 null
     */
    public String refreshCredential(String refreshToken) {
        try {
            CodexOAuthService.CodexOAuthTokenResult result = codexOAuthService.refreshToken(refreshToken);
            return result.getAccessToken();
        } catch (Exception e) {
            log.error("failed to refresh codex credential: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新指定 Codex 渠道的凭证      * <p>
     * 步骤：查渠道 → 校验类型 → 解析 OAuth key → 解析 proxy → 调用 refresh → 回写渠道 key
     *
     * @param channelId 渠道 ID
     * @return 新的 OAuthKey，失败抛异常
     */
    public CodexOAuthKey refreshChannelCredential(int channelId) {
        return refreshChannelCredential(channelId, false);
    }

    /**
     * 刷新指定 Codex 渠道的凭证（含缓存重置选项）      */
    public CodexOAuthKey refreshChannelCredential(int channelId, boolean resetCaches) {
        Channel ch = channelMapper.selectById(channelId);
        if (ch == null) {
            throw new RuntimeException("channel not found");
        }
        if (ch.getType() == null || ch.getType() != ChannelConstants.CHANNEL_TYPE_CODEX) {
            throw new RuntimeException("channel type is not Codex");
        }

        CodexOAuthKey oauthKey = parseOAuthKey(ch.getKey());
        if (oauthKey.getRefreshToken() == null || oauthKey.getRefreshToken().trim().isEmpty()) {
            throw new RuntimeException("codex channel: refresh_token is required to refresh credential");
        }

        String proxyURL = parseProxyFromSetting(ch.getSetting());

        // 调用 OpenAI OAuth refresh API（带代理）
        CodexOAuthService.CodexOAuthTokenResult result = codexOAuthService.refreshTokenWithProxy(
                oauthKey.getRefreshToken(),
                proxyURL
        );

        oauthKey.setAccessToken(result.getAccessToken());
        oauthKey.setRefreshToken(result.getRefreshToken());
        oauthKey.setLastRefresh(LocalDateTime.now().format(RFC3339));
        oauthKey.setExpired(result.getExpiresAt().format(RFC3339));
        if (oauthKey.getType() == null || oauthKey.getType().trim().isEmpty()) {
            oauthKey.setType("codex");
        }

        // 从 JWT 中补充缺失的 account_id / email
        if (oauthKey.getAccountId() == null || oauthKey.getAccountId().trim().isEmpty()) {
            String accountId = CodexOAuthService.extractAccountIDFromJWT(oauthKey.getAccessToken());
            if (accountId != null) {
                oauthKey.setAccountId(accountId);
            }
        }
        if (oauthKey.getEmail() == null || oauthKey.getEmail().trim().isEmpty()) {
            String email = CodexOAuthService.extractEmailFromJWT(oauthKey.getAccessToken());
            if (email != null) {
                oauthKey.setEmail(email);
            }
        }

        // 回写渠道 key
        String encoded = Convert.toJSONString(oauthKey);
        LambdaUpdateWrapper<Channel> uw = new LambdaUpdateWrapper<>();
        uw.eq(Channel::getId, channelId).set(Channel::getKey, encoded);
        channelMapper.update(null, uw);

        // Codex 凭证刷新成功后清空 proxyClients 缓存
        proxyClientCacheService.reset();

        if (resetCaches) {
            // ChannelCacheService 初始化由全局管理，此处仅打日志
            log.info("codex credential refreshed for channel {}, caches reset requested", channelId);
        }

        return oauthKey;
    }

    /**
     * 解析 Codex OAuth key JSON      */
    public static CodexOAuthKey parseOAuthKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new RuntimeException("codex channel: empty oauth key");
        }
        try {
            Map<String, Object> map = Convert.toJSONObject(raw);
            if (map == null) {
                throw new RuntimeException("codex channel: invalid oauth key json");
            }
            CodexOAuthKey key = new CodexOAuthKey();
            key.setIdToken(getStr(map, "id_token"));
            key.setAccessToken(getStr(map, "access_token"));
            key.setRefreshToken(getStr(map, "refresh_token"));
            key.setAccountId(getStr(map, "account_id"));
            key.setLastRefresh(getStr(map, "last_refresh"));
            key.setEmail(getStr(map, "email"));
            key.setType(getStr(map, "type"));
            key.setExpired(getStr(map, "expired"));
            return key;
        } catch (Exception e) {
            throw new RuntimeException("codex channel: invalid oauth key json", e);
        }
    }

    /**
     * 将 OAuthKey 序列化为 JSON，用于回写渠道 key
     */
    public static String toEncodedJson(CodexOAuthKey key) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (key.getIdToken() != null) map.put("id_token", key.getIdToken());
        if (key.getAccessToken() != null) map.put("access_token", key.getAccessToken());
        if (key.getRefreshToken() != null) map.put("refresh_token", key.getRefreshToken());
        if (key.getAccountId() != null) map.put("account_id", key.getAccountId());
        if (key.getLastRefresh() != null) map.put("last_refresh", key.getLastRefresh());
        if (key.getEmail() != null) map.put("email", key.getEmail());
        if (key.getType() != null) map.put("type", key.getType());
        if (key.getExpired() != null) map.put("expired", key.getExpired());
        return Convert.toJSONString(map);
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 从渠道 setting JSON 中解析 proxy URL      */
    private String parseProxyFromSetting(String channelSetting) {
        if (channelSetting == null || channelSetting.isBlank()) {
            return null;
        }
        try {
            ChannelSettingsDTO setting = Convert.toJavaBean(channelSetting, ChannelSettingsDTO.class);
            String proxy = setting.getProxy();
            return proxy == null || proxy.isBlank() ? null : proxy.trim();
        } catch (Exception e) {
            log.warn("Failed to parse channel setting proxy for Codex refresh: {}", e.getMessage());
            return null;
        }
    }
}
