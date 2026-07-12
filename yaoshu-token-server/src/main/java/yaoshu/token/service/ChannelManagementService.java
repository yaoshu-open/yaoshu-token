package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.config.EndpointTypeMapping;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.ChannelTestResultCode;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.EndpointTypeEnum;
import yaoshu.token.mapper.AbilityMapper;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.pojo.dto.ChannelInfoDTO;
import yaoshu.token.pojo.dto.ChannelOtherSettingsDTO;
import yaoshu.token.pojo.dto.ChannelSettingsDTO;
import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.pojo.dto.OpenAIImageDTO;
import yaoshu.token.pojo.dto.OpenAIResponsesCompactionRequest;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.pojo.dto.RelayFormat;
import yaoshu.token.pojo.dto.RerankRequest;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.pojo.entity.Ability;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.ipo.ChannelIPO;
import yaoshu.token.pojo.dto.EmbeddingDTO;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.OverrideUtils;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RequestConversion;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.BillingExprHelper;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.service.CodexOAuthService.CodexOAuthAuthorizationFlow;
import yaoshu.token.service.CodexOAuthService.CodexOAuthTokenResult;
import yaoshu.token.service.CodexUsageService.CodexUsageResult;
import ai.yue.library.base.util.I18nUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 渠道管理服务，承接 ChannelController 的管理型接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelManagementService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** proxy-aware 客户端缓存，key = proxy URL*/
    private static final Map<String, HttpClient> PROXY_CLIENTS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.locks.ReentrantLock PROXY_CLIENT_LOCK = new java.util.concurrent.locks.ReentrantLock();
    private static final DateTimeFormatter CODEX_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String COMPACT_MODEL_SUFFIX = "-compact";
    private static final int LOG_TYPE_CONSUME = 2;
    private static final List<Integer> UNSUPPORTED_TEST_CHANNEL_TYPES = List.of(
            ChannelConstants.CHANNEL_TYPE_MIDJOURNEY,
            ChannelConstants.CHANNEL_TYPE_MIDJOURNEY_PLUS,
            ChannelConstants.CHANNEL_TYPE_SUNO_API,
            ChannelConstants.CHANNEL_TYPE_KLING,
            ChannelConstants.CHANNEL_TYPE_JIMENG,
            ChannelConstants.CHANNEL_TYPE_DOUBAO_VIDEO,
            ChannelConstants.CHANNEL_TYPE_VIDU
    );
    private static final List<String> AUTOMATIC_DISABLE_KEYWORDS = List.of(
            "your credit balance is too low",
            "this organization has been disabled.",
            "you exceeded your current quota",
            "permission denied",
            "the security token included in the request is invalid",
            "operation not allowed",
            "your account is not authorized"
    );

    private static final ReentrantLock FIX_ABILITY_LOCK = new ReentrantLock();
    private static final Map<Integer, ReentrantLock> CHANNEL_POLLING_LOCKS = new ConcurrentHashMap<>();
    private final AtomicBoolean batchChannelTestRunning = new AtomicBoolean(false);

    /**
     * 启动时初始化渠道内存缓存
     * <p>
     * OptionService @PostConstruct 先于本方法执行（Spring 按依赖顺序初始化），
     * 此时 MemoryCacheEnabled 已从 options 表加载到 CommonConstants。
     */
    @PostConstruct
    public void initCache() {
        refreshChannelCache();
    }

    private final ChannelMapper channelMapper;
    private final AbilityMapper abilityMapper;
    private final LogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final CodexOAuthService codexOAuthService;
    private final CodexUsageService codexUsageService;
    private final CodexCredentialRefreshService codexCredentialRefreshService;
    private final OptionService optionService;
    private final UserNotifyService userNotifyService;
    private final UserService userService;
    private final ProxyClientCacheService proxyClientCacheService;
    private final PricingService pricingService;

    @Transactional(rollbackFor = Exception.class)
    public long deleteDisabledChannels() {
        List<Channel> disabledChannels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .in(Channel::getStatus, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED,
                        CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED));
        if (disabledChannels.isEmpty()) {
            return 0L;
        }

        List<Integer> ids = disabledChannels.stream().map(Channel::getId).filter(Objects::nonNull).toList();
        abilityMapper.delete(new LambdaQueryWrapper<Ability>().in(Ability::getChannelId, ids));
        long deleted = channelMapper.delete(new LambdaQueryWrapper<Channel>()
                .in(Channel::getId, ids));
        refreshChannelCache();
        return deleted;
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableChannelsByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException(I18nUtils.get("common.invalid_params"));
        }
        channelMapper.update(null, new LambdaUpdateWrapper<Channel>()
                .eq(Channel::getTag, tag)
                .set(Channel::getStatus, CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED));
        abilityMapper.update(null, new LambdaUpdateWrapper<Ability>()
                .eq(Ability::getTag, tag)
                .set(Ability::getEnabled, false));
        refreshChannelCache();
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableChannelsByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException(I18nUtils.get("common.invalid_params"));
        }
        channelMapper.update(null, new LambdaUpdateWrapper<Channel>()
                .eq(Channel::getTag, tag)
                .set(Channel::getStatus, CommonConstants.CHANNEL_STATUS_ENABLED));
        abilityMapper.update(null, new LambdaUpdateWrapper<Ability>()
                .eq(Ability::getTag, tag)
                .set(Ability::getEnabled, true));
        refreshChannelCache();
    }

    @Transactional(rollbackFor = Exception.class)
    public void editChannelsByTag(ChannelIPO.TagEdit ipo) {
        String tag = ipo.getTag();
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException(I18nUtils.get("common.invalid_params"));
        }
        boolean hasNewTag = ipo.getNewTag() != null;
        boolean hasModelMapping = ipo.getModelMapping() != null;
        boolean hasModels = ipo.getModels() != null;
        boolean hasGroups = ipo.getGroups() != null;
        boolean hasPriority = ipo.getPriority() != null;
        boolean hasWeight = ipo.getWeight() != null;
        boolean hasParamOverride = ipo.getParamOverride() != null;
        boolean hasHeaderOverride = ipo.getHeaderOverride() != null;

        String newTag = ipo.getNewTag();
        String modelMapping = ipo.getModelMapping();
        String models = trimToEmpty(ipo.getModels());
        String groups = trimToEmpty(ipo.getGroups());
        Long priority = ipo.getPriority();
        Integer weight = ipo.getWeight();
        String paramOverride = validateJsonOverride(ipo.getParamOverride(), I18nUtils.get("channel.invalid_param_override_json"));
        String headerOverride = validateJsonOverride(ipo.getHeaderOverride(), I18nUtils.get("channel.invalid_header_override_json"));

        LambdaUpdateWrapper<Channel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Channel::getTag, tag);
        boolean hasUpdate = false;
        if (hasNewTag && !Objects.equals(newTag, tag)) {
            updateWrapper.set(Channel::getTag, newTag);
            hasUpdate = true;
        }
        if (hasModelMapping) {
            updateWrapper.set(Channel::getModelMapping, modelMapping);
            hasUpdate = true;
        }
        if (hasModels && !models.isEmpty()) {
            updateWrapper.set(Channel::getModels, models);
            hasUpdate = true;
        }
        if (hasGroups && !groups.isEmpty()) {
            updateWrapper.set(Channel::getGroup, groups);
            hasUpdate = true;
        }
        if (hasPriority) {
            updateWrapper.set(Channel::getPriority, priority);
            hasUpdate = true;
        }
        if (hasWeight) {
            updateWrapper.set(Channel::getWeight, weight);
            hasUpdate = true;
        }
        if (hasParamOverride) {
            updateWrapper.set(Channel::getParamOverride, paramOverride);
            hasUpdate = true;
        }
        if (hasHeaderOverride) {
            updateWrapper.set(Channel::getHeaderOverride, headerOverride);
            hasUpdate = true;
        }
        if (hasUpdate) {
            channelMapper.update(null, updateWrapper);
        }

        boolean recreateAbilities = (hasModels && !models.isEmpty()) || (hasGroups && !groups.isEmpty());
        String updatedTag = hasNewTag && !Objects.equals(newTag, tag) ? newTag : tag;
        if (recreateAbilities) {
            List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                    .eq(Channel::getTag, updatedTag));
            for (Channel channel : channels) {
                recreateChannelAbilities(channel);
            }
        } else if (hasNewTag || hasPriority || hasWeight) {
            LambdaUpdateWrapper<Ability> abilityUpdate = new LambdaUpdateWrapper<>();
            abilityUpdate.eq(Ability::getTag, tag);
            if (hasNewTag) {
                abilityUpdate.set(Ability::getTag, newTag);
            }
            if (hasPriority) {
                abilityUpdate.set(Ability::getPriority, priority);
            }
            if (hasWeight) {
                abilityUpdate.set(Ability::getWeight, weight == null ? null : weight);
            }
            abilityMapper.update(null, abilityUpdate);
        }
        refreshChannelCache();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> fixAbilities() {
        if (!FIX_ABILITY_LOCK.tryLock()) {
            throw new IllegalStateException(I18nUtils.get("ability.repair_running"));
        }
        try {
            abilityMapper.delete(null);
            List<Channel> channels = channelMapper.selectList(null);
            int success = 0;
            int fails = 0;
            for (Channel channel : channels) {
                try {
                    recreateChannelAbilities(channel);
                    success++;
                } catch (RuntimeException e) {
                    fails++;
                    log.warn("Failed to rebuild abilities for channel#{}: {}", channel.getId(), e.getMessage());
                }
            }
            refreshChannelCache();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("fails", fails);
            return result;
        } finally {
            FIX_ABILITY_LOCK.unlock();
        }
    }

    public List<String> fetchModelsByChannelId(int id) {
        Channel channel = requireChannel(id);
        try {
            return fetchModelsFromChannel(channel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18nUtils.get("channel.get_model_list_failed", e.getMessage()));
        } catch (IOException e) {
            throw new IllegalStateException(I18nUtils.get("channel.get_model_list_failed", e.getMessage()));
        }
    }

    public List<String> fetchModels(Integer type, String baseUrl, String key) {
        if (type == null) {
            throw new IllegalArgumentException("Invalid request");
        }
        String normalizedBaseUrl = normalizeBaseUrl(type, baseUrl);
        String firstKey = firstKey(key);
        try {
            return fetchModelsFromRequest(type, normalizedBaseUrl, firstKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchSetTag(List<Integer> ids, String tag) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(I18nUtils.get("common.invalid_params"));
        }
        channelMapper.update(null, new LambdaUpdateWrapper<Channel>()
                .in(Channel::getId, ids)
                .set(Channel::getTag, tag));
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>().in(Channel::getId, ids));
        for (Channel channel : channels) {
            recreateChannelAbilities(channel);
        }
        refreshChannelCache();
        return ids.size();
    }

    public String getTagModels(String tag) {
        String normalizedTag = trimToNull(tag);
        if (normalizedTag == null) {
            throw new IllegalArgumentException(I18nUtils.get("channel.tag_cannot_be_empty"));
        }
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getTag, normalizedTag));
        String longestModels = "";
        int maxLength = 0;
        for (Channel channel : channels) {
            String models = channel.getModels();
            if (models == null || models.isBlank()) {
                continue;
            }
            int currentLength = splitCommaSeparated(models).size();
            if (currentLength > maxLength) {
                maxLength = currentLength;
                longestModels = models;
            }
        }
        return longestModels;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> copyChannel(int id, String suffix, boolean resetBalance) {
        Channel origin = requireChannel(id);
        Channel clone = new Channel();
        BeanUtils.copyProperties(origin, clone);
        clone.setId(null);
        clone.setCreatedTime(currentUnixSeconds());
        clone.setName((origin.getName() == null ? "" : origin.getName()) + suffix);
        clone.setTestTime(0L);
        clone.setResponseTime(0);
        if (resetBalance) {
            clone.setBalance(0D);
            clone.setUsedQuota(0L);
        }
        channelMapper.insert(clone);
        recreateChannelAbilities(clone);
        refreshChannelCache();
        return Map.of("id", clone.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> manageMultiKeys(ChannelIPO.MultiKeyManage ipo) {
        Integer channelId = ipo.getChannelId();
        String action = trimToNull(ipo.getAction());
        if (channelId == null || action == null) {
            throw new IllegalArgumentException(I18nUtils.get("common.invalid_params"));
        }

        Channel channel = requireChannel(channelId);
        ChannelInfoDTO info = parseChannelInfo(channel);
        if (!info.isMultiKey()) {
            throw new IllegalArgumentException(I18nUtils.get("channel.not_multi_key_mode"));
        }

        ReentrantLock lock = CHANNEL_POLLING_LOCKS.computeIfAbsent(channelId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return switch (action) {
                case "get_key_status" -> getMultiKeyStatus(ipo, channel, info);
                case "disable_key" -> disableSingleKey(ipo, channel, info);
                case "enable_key" -> enableSingleKey(ipo, channel, info);
                case "enable_all_keys" -> enableAllKeys(channel, info);
                case "disable_all_keys" -> disableAllKeys(channel, info);
                case "delete_key" -> deleteSingleKey(ipo, channel, info);
                case "delete_disabled_keys" -> deleteAutoDisabledKeys(channel, info);
                default -> throw new IllegalArgumentException(I18nUtils.get("channel.unsupported_operation"));
            };
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> startCodexOAuth(int channelId, HttpSession session) {
        requireSession(session);
        if (channelId > 0) {
            requireCodexChannel(channelId);
        }
        CodexOAuthAuthorizationFlow flow = codexOAuthService.createAuthorizationFlow();
        session.setAttribute(codexOAuthSessionKey(channelId, "state"), flow.getState());
        session.setAttribute(codexOAuthSessionKey(channelId, "verifier"), flow.getVerifier());
        session.setAttribute(codexOAuthSessionKey(channelId, "created_at"), currentUnixSeconds());
        return Map.of("authorizeUrl", flow.getAuthorizeURL());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completeCodexOAuth(int channelId, String input, HttpSession session) {
        requireSession(session);
        CodexAuthorizationInput authorizationInput = parseCodexAuthorizationInput(input);
        if (authorizationInput.code() == null) {
            throw new IllegalArgumentException("missing authorization code");
        }
        if (authorizationInput.state() == null) {
            throw new IllegalArgumentException("missing state in input");
        }

        Channel channel = channelId > 0 ? requireCodexChannel(channelId) : null;
        String expectedState = sessionString(session, codexOAuthSessionKey(channelId, "state"));
        String verifier = sessionString(session, codexOAuthSessionKey(channelId, "verifier"));
        if (expectedState == null || verifier == null) {
            throw new IllegalArgumentException("oauth flow not started or session expired");
        }
        if (!authorizationInput.state().equals(expectedState)) {
            throw new IllegalArgumentException("state mismatch");
        }

        CodexOAuthTokenResult tokenResult;
        try {
            tokenResult = codexOAuthService.exchangeCodeForTokenWithProxy(
                    authorizationInput.code(),
                    verifier,
                    channel == null ? null : parseProxyFromSetting(channel.getSetting())
            );
        } catch (RuntimeException e) {
            log.warn("Failed to exchange codex authorization code, channelId={}: {}", channelId, e.getMessage());
            throw new IllegalStateException(I18nUtils.get("channel.code_exchange_failed"));
        }

        String accountId = trimToNull(CodexOAuthService.extractAccountIDFromJWT(tokenResult.getAccessToken()));
        if (accountId == null) {
            throw new IllegalStateException("failed to extract account_id from access_token");
        }
        String email = trimToNull(CodexOAuthService.extractEmailFromJWT(tokenResult.getAccessToken()));
        String lastRefresh = LocalDateTime.now().format(CODEX_TIME_FORMATTER);
        String expiresAt = tokenResult.getExpiresAt().format(CODEX_TIME_FORMATTER);
        String encodedKey = toEncodedCodexKey(tokenResult, accountId, email, lastRefresh, expiresAt);

        clearCodexOAuthSession(session, channelId);

        Map<String, Object> data = new LinkedHashMap<>();
        if (channel != null) {
            Channel update = new Channel();
            update.setId(channel.getId());
            update.setKey(encodedKey);
            channelMapper.updateById(update);
            channel.setKey(encodedKey);
            refreshChannelCache();
            // Codex OAuth 完成并写入新凭证后清空 proxyClients 缓存
            proxyClientCacheService.reset();
            data.put("channelId", channel.getId());
        } else {
            data.put("key", encodedKey);
        }
        data.put("accountId", accountId);
        data.put("email", email);
        data.put("expiresAt", expiresAt);
        data.put("lastRefresh", lastRefresh);
        return payload(channel == null ? "generated" : "saved", data);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refreshCodexCredential(int channelId) {
        Channel channel = requireCodexChannel(channelId);
        CodexCredentialRefreshService.CodexOAuthKey oauthKey =
                codexCredentialRefreshService.refreshChannelCredential(channelId, false);
        refreshChannelCache();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expiresAt", oauthKey.getExpired());
        data.put("lastRefresh", oauthKey.getLastRefresh());
        data.put("accountId", oauthKey.getAccountId());
        data.put("email", oauthKey.getEmail());
        data.put("channelId", channel.getId());
        data.put("channelType", channel.getType());
        data.put("channelName", channel.getName());
        return payload("refreshed", data);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getCodexUsage(int channelId) {
        Channel channel = requireCodexChannel(channelId);
        ChannelInfoDTO info = parseChannelInfo(channel);
        if (info.isMultiKey()) {
            throw new IllegalArgumentException("multi-key channel is not supported");
        }

        CodexCredentialRefreshService.CodexOAuthKey oauthKey;
        try {
            oauthKey = CodexCredentialRefreshService.parseOAuthKey(trimToNull(channel.getKey()));
        } catch (RuntimeException e) {
            log.warn("Failed to parse codex oauth key for channel#{}: {}", channelId, e.getMessage());
            throw new IllegalStateException(I18nUtils.get("channel.credential_parse_failed"));
        }

        String accessToken = trimToNull(oauthKey.getAccessToken());
        String accountId = trimToNull(oauthKey.getAccountId());
        if (accessToken == null) {
            throw new IllegalArgumentException("codex channel: access_token is required");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("codex channel: account_id is required");
        }

        String proxyURL = parseProxyFromSetting(channel.getSetting());
        String baseUrl = normalizeBaseUrl(ChannelConstants.CHANNEL_TYPE_CODEX, channel.getBaseUrl());

        CodexUsageResult usageResult = codexUsageService.fetchUsageWithProxy(
                baseUrl,
                accessToken,
                accountId,
                proxyURL
        );
        if ((usageResult.getStatusCode() == 401 || usageResult.getStatusCode() == 403)
                && trimToNull(oauthKey.getRefreshToken()) != null) {
            try {
                oauthKey = codexCredentialRefreshService.refreshChannelCredential(channelId, false);
                refreshChannelCache();
                usageResult = codexUsageService.fetchUsageWithProxy(
                        baseUrl,
                        oauthKey.getAccessToken(),
                        trimToNull(oauthKey.getAccountId()),
                        proxyURL
                );
            } catch (RuntimeException e) {
                log.warn("Failed to refresh codex credential before usage retry, channel#{}: {}",
                        channelId, e.getMessage());
            }
        }

        if (usageResult.getError() != null) {
            throw new IllegalStateException(I18nUtils.get("channel.get_usage_failed"));
        }

        boolean ok = usageResult.getStatusCode() >= 200 && usageResult.getStatusCode() < 300;
        if (!ok) {
            throw new IllegalStateException("upstream status: " + usageResult.getStatusCode());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upstreamStatus", usageResult.getStatusCode());
        result.put("data", parseJsonBodyOrRawString(usageResult.getBody()));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateBalance(int channelId) {
        Channel channel = requireChannel(channelId);
        ChannelInfoDTO info = parseChannelInfo(channel);
        if (info.isMultiKey()) {
            throw new IllegalArgumentException(I18nUtils.get("channel.multi_key_no_balance_query"));
        }
        double balance = updateChannelBalance(channel);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", balance);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAllBalances() {
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getStatus, CommonConstants.CHANNEL_STATUS_ENABLED));
        boolean cacheChanged = false;
        for (Channel channel : channels) {
            ChannelInfoDTO info = parseChannelInfo(channel);
            if (info.isMultiKey()) {
                continue;
            }
            try {
                double balance = updateChannelBalance(channel);
                if (balance <= 0D && isAutoBanEnabled(channel)) {
                    // 通过 updateChannelStatus 禁用，同步关闭 abilities.enabled
                    updateChannelStatus(channel, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED);
                    cacheChanged = true;
                }
            } catch (RuntimeException e) {
                log.warn("Failed to update balance for channel#{}: {}", channel.getId(), e.getMessage());
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(I18nUtils.get("channel.balance_batch_update_interrupted"));
            }
        }
        if (cacheChanged) {
            refreshChannelCache();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyUpstreamModelUpdates(ChannelIPO.UpstreamUpdates ipo) {
        Integer channelId = ipo.getId();
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("invalid channel id");
        }

        Channel channel = requireChannel(channelId);
        ChannelUpstreamApplyResult result = applyChannelUpstreamModelUpdates(
                channel,
                ipo.getAddModels() != null ? ipo.getAddModels() : List.of(),
                ipo.getIgnoreModels() != null ? ipo.getIgnoreModels() : List.of(),
                ipo.getRemoveModels() != null ? ipo.getRemoveModels() : List.of()
        );
        if (result.modelsChanged()) {
            refreshChannelCache();
            // 上游模型批量应用结束后清空 proxyClients 缓存
            proxyClientCacheService.reset();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", channel.getId());
        data.put("addedModels", result.addedModels());
        data.put("removedModels", result.removedModels());
        data.put("ignoredModels", result.ignoredModels());
        data.put("remainingModels", result.remainingModels());
        data.put("remainingRemoveModels", result.remainingRemoveModels());
        data.put("models", channel.getModels());
        data.put("settings", parseJsonBodyOrRawString(channel.getOtherSettings()));
        return data;
    }

    public Map<String, Object> applyAllUpstreamModelUpdates() {
        List<Channel> channels = listEnabledChannels();
        List<Map<String, Object>> results = new ArrayList<>();
        List<Integer> failedChannelIds = new ArrayList<>();
        boolean refreshNeeded = false;
        int addedModelCount = 0;
        int removedModelCount = 0;

        for (Channel channel : channels) {
            ChannelOtherSettingsDTO settings = parseChannelOtherSettings(channel);
            if (!Boolean.TRUE.equals(settings.getUpstreamModelUpdateCheckEnabled())) {
                continue;
            }
            List<String> pendingAddModels = normalizeModelNames(settings.getUpstreamModelUpdateLastDetectedModels());
            List<String> pendingRemoveModels = normalizeModelNames(settings.getUpstreamModelUpdateLastRemovedModels());
            if (pendingAddModels.isEmpty() && pendingRemoveModels.isEmpty()) {
                continue;
            }

            try {
                ChannelUpstreamApplyResult result = applyChannelUpstreamModelUpdates(
                        channel,
                        pendingAddModels,
                        List.of(),
                        pendingRemoveModels
                );
                if (result.modelsChanged()) {
                    refreshNeeded = true;
                    userNotifyService.notifyUpstreamModelUpdateWatchers(
                            channel.getName(), result.addedModels(), result.removedModels());
                }
                addedModelCount += result.addedModels().size();
                removedModelCount += result.removedModels().size();

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("channelId", channel.getId());
                item.put("channelName", channel.getName());
                item.put("addedModels", result.addedModels());
                item.put("removedModels", result.removedModels());
                item.put("remainingModels", result.remainingModels());
                item.put("remainingRemoveModels", result.remainingRemoveModels());
                results.add(item);
            } catch (RuntimeException e) {
                failedChannelIds.add(channel.getId());
            }
        }

        if (refreshNeeded) {
            refreshChannelCache();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processedChannels", results.size());
        data.put("addedModels", addedModelCount);
        data.put("removedModels", removedModelCount);
        data.put("failedChannelIds", failedChannelIds);
        data.put("results", results);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> detectUpstreamModelUpdates(Integer channelId) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("invalid channel id");
        }

        Channel channel = requireChannel(channelId);
        ChannelUpstreamDetectResult result = detectChannelUpstreamModelUpdates(channel, true, false);
        if (result.modelsChanged()) {
            refreshChannelCache();
            userNotifyService.notifyUpstreamModelUpdateWatchers(
                    result.channelName(), result.addModels(), result.removeModels());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channelId", result.channelId());
        data.put("channelName", result.channelName());
        data.put("addModels", result.addModels());
        data.put("removeModels", result.removeModels());
        data.put("lastCheckTime", result.lastCheckTime());
        data.put("autoAddedModels", result.autoAddedModels());
        return data;
    }

    public Map<String, Object> detectAllUpstreamModelUpdates() {
        List<Channel> channels = listEnabledChannels();
        List<Map<String, Object>> results = new ArrayList<>();
        List<Integer> failedChannelIds = new ArrayList<>();
        boolean refreshNeeded = false;
        int detectedAddCount = 0;
        int detectedRemoveCount = 0;

        for (Channel channel : channels) {
            ChannelOtherSettingsDTO settings = parseChannelOtherSettings(channel);
            if (!Boolean.TRUE.equals(settings.getUpstreamModelUpdateCheckEnabled())) {
                continue;
            }
            try {
                ChannelUpstreamDetectResult result = detectChannelUpstreamModelUpdates(channel, true, false);
                if (result.modelsChanged()) {
                    refreshNeeded = true;
                    userNotifyService.notifyUpstreamModelUpdateWatchers(
                            result.channelName(), result.addModels(), result.removeModels());
                }
                detectedAddCount += result.addModels().size();
                detectedRemoveCount += result.removeModels().size();

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("channelId", result.channelId());
                item.put("channelName", result.channelName());
                item.put("addModels", result.addModels());
                item.put("removeModels", result.removeModels());
                item.put("lastCheckTime", result.lastCheckTime());
                item.put("autoAddedModels", result.autoAddedModels());
                results.add(item);
            } catch (RuntimeException e) {
                failedChannelIds.add(channel.getId());
            }
        }

        if (refreshNeeded) {
            refreshChannelCache();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processedChannels", results.size());
        data.put("failedChannelIds", failedChannelIds);
        data.put("detectedAddModels", detectedAddCount);
        data.put("detectedRemoveModels", detectedRemoveCount);
        data.put("channelDetectedResults", results);
        return data;
    }

    public Map<String, Object> testChannel(int channelId, HttpServletRequest request) {
        Channel channel = requireChannel(channelId);
        int testUserId = resolveChannelTestUserId(request);
        String requestedModel = request == null ? null : trimToNull(request.getParameter("model"));
        String endpointType = request == null ? null : trimToNull(request.getParameter("endpoint_type"));
        boolean stream = parseBoolean(request == null ? null : request.getParameter("stream"), false);

        ChannelTestExecutionResult result = executeChannelTest(
                channel,
                testUserId,
                requestedModel,
                endpointType,
                stream
        );
        if (!result.success()) {
            String message = result.localError() == null
                    ? I18nUtils.get("channel.test_failed")
                    : result.localError().getMessage();
            // 将 relay 领域 errorCode 映射为 yue-library ResultCode（code > 600），前端按 code 精确分支
            RelayException apiError = result.newApiError();
            ChannelTestResultCode resultCode = apiError == null
                    ? null
                    : ChannelTestResultCode.fromErrorCode(apiError.getErrorCode());
            if (resultCode != null) {
                Result<?> errorResult = R.errorPromptCode(resultCode);
                errorResult.setMsg(message);
                throw new ResultException(errorResult);
            }
            throw new ResultException(R.errorPrompt(message));
        }

        updateChannelTestMetrics(channel, result.elapsedMilliseconds());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("time", toSeconds(result.elapsedMilliseconds()));
        return payload;
    }

    public Map<String, Object> testAllChannelsAsync() {
        if (!batchChannelTestRunning.compareAndSet(false, true)) {
            throw new IllegalStateException(I18nUtils.get("channel.test_already_running"));
        }
        List<Channel> channels;
        List<ChannelBatchTestItem> results;
        try {
            int testUserId = resolveChannelTestUserId(null);
            channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                    .orderByAsc(Channel::getId));
            // 并发执行所有渠道测试，等待全部完成后返回各渠道测试结果
            results = executeAllChannelTestsConcurrently(channels, testUserId);
        } catch (RuntimeException e) {
            batchChannelTestRunning.set(false);
            throw e;
        } finally {
            batchChannelTestRunning.set(false);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", channels.size());
        result.put("completed", channels.size());
        result.put("results", results);
        return result;
    }

    /**
     * 按 ID 列表批量测试指定渠道。
     * <p>
     * 仅测试传入 ids 对应的渠道，手动禁用渠道被过滤不参与测试。
     * 与全量测试共用 {@code batchChannelTestRunning} 互斥标志，避免并发测试相互干扰缓存与状态。
     * 不向 Root 用户发送"全量测试完成"通知（定向测试非全量巡检）。
     * 返回 {total=传入ids数, completed=完成数, results=各渠道测试明细}，
     * results 仅含实际被测渠道，故 results.length 可能小于 total。
     */
    public Map<String, Object> testChannelsByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("common.invalid_params")));
        }
        if (!batchChannelTestRunning.compareAndSet(false, true)) {
            throw new IllegalStateException(I18nUtils.get("channel.test_already_running"));
        }
        List<Channel> channels;
        List<ChannelBatchTestItem> results;
        try {
            int testUserId = resolveChannelTestUserId(null);
            channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                    .in(Channel::getId, ids)
                    .orderByAsc(Channel::getId));
            if (channels.isEmpty()) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("channel.not_exists")));
            }
            // 定向测试不发 Root 通知
            results = executeChannelTestsConcurrently(channels, testUserId, false);
        } catch (RuntimeException e) {
            batchChannelTestRunning.set(false);
            throw e;
        } finally {
            batchChannelTestRunning.set(false);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", ids.size());
        result.put("completed", channels.size());
        result.put("results", results);
        return result;
    }

    /**
     * 并发执行所有渠道测试，等待全部完成后返回各渠道测试结果。
     * <p>
     * 每个渠道测试作为独立任务提交到公共 ForkJoinPool，通过 CompletableFuture.allOf 等待全部完成。
     * 渠道状态变更（自动禁用/恢复）和缓存刷新在所有测试完成后统一处理。
     * 返回结果仅含实际被测渠道（手动禁用渠道被过滤），顺序按渠道 id 升序。
     */
    private List<ChannelBatchTestItem> executeAllChannelTestsConcurrently(List<Channel> channels, int testUserId) {
        return executeChannelTestsConcurrently(channels, testUserId, true);
    }

    /**
     * 并发执行渠道测试的通用内部方法，等待全部完成后返回各渠道测试结果。
     * <p>
     * 手动禁用渠道被过滤不参与测试；notifyRoot 控制是否向 Root 用户发送"全量测试完成"通知
     * （全量测试发通知，按 ID 列表定向测试不发）。所有测试完成后统一刷新缓存。
     * 返回结果仅含实际被测渠道，顺序保持入参顺序。
     */
    private List<ChannelBatchTestItem> executeChannelTestsConcurrently(List<Channel> channels, int testUserId, boolean notifyRoot) {
        long disableThresholdMillis = resolveChannelDisableThresholdMillis();
        // 并发收集测试结果
        List<CompletableFuture<ChannelBatchTestItem>> futures = channels.stream()
                .filter(channel -> !Objects.equals(channel.getStatus(), CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED))
                .map(channel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return executeSingleChannelTestInBatch(channel, testUserId, disableThresholdMillis);
                    } catch (Exception e) {
                        log.warn("渠道测试异常 channelId={} name={}: {}", channel.getId(), channel.getName(), e.getMessage());
                        // 异常时仍返回失败条目，保证结果完整性
                        return new ChannelBatchTestItem(
                                channel.getId(),
                                channel.getName(),
                                resolveChannelTestModel(channel, null),
                                false,
                                0L,
                                false,
                                e.getMessage()
                        );
                    }
                }))
                .toList();
        // 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 所有测试完成后统一刷新缓存
        refreshChannelCache();
        if (notifyRoot) {
            userNotifyService.notifyRootUser(
                    UserNotifyService.EVENT_CHANNEL_TEST,
                    I18nUtils.get("channel.test_completed"),
                    I18nUtils.get("channel.all_tests_completed")
            );
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /**
     * 批量测试中单个渠道的测试逻辑（并发安全）。
     * 每个渠道独立执行测试、更新指标、处理自动禁用/恢复。
     */
    private ChannelBatchTestItem executeSingleChannelTestInBatch(Channel channel, int testUserId, long disableThresholdMillis) {
        boolean channelEnabled = Objects.equals(channel.getStatus(), CommonConstants.CHANNEL_STATUS_ENABLED);
        ChannelTestExecutionResult result = executeChannelTest(
                channel,
                testUserId,
                null,
                null,
                shouldUseStreamForAutomaticChannelTest(channel)
        );
        updateChannelTestMetrics(channel, result.elapsedMilliseconds());

        RelayException effectiveError = result.newApiError();
        boolean shouldDisableChannel = shouldDisableChannel(effectiveError);
        if (effectiveError == null
                && CommonConstants.automaticDisableChannelEnabled
                && result.elapsedMilliseconds() > disableThresholdMillis) {
            String message = I18nUtils.get("distributor.response_time_exceeded",
                    String.format("%.2f", toSeconds(result.elapsedMilliseconds())),
                    String.format("%.2f", toSeconds(disableThresholdMillis)));
            effectiveError = newApiError(message, ErrorCode.CHANNEL_RESPONSE_TIME_EXCEEDED, 408);
            shouldDisableChannel = true;
        }

        // 追踪测试后渠道状态是否变更（自动禁用/自动恢复）
        boolean statusChanged = false;
        if (channelEnabled && shouldDisableChannel && isAutoBanEnabled(channel)) {
            if (updateChannelStatus(channel, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED)) {
                statusChanged = true;
                notifyChannelStatusChanged(channel, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED,
                        effectiveError == null ? I18nUtils.get("channel.test_failed") : effectiveError.getMessage());
            }
        }

        if (!channelEnabled && shouldEnableChannel(effectiveError, channel.getStatus())) {
            if (updateChannelStatus(channel, CommonConstants.CHANNEL_STATUS_ENABLED)) {
                statusChanged = true;
                notifyChannelStatusChanged(channel, CommonConstants.CHANNEL_STATUS_ENABLED, "");
            }
        }

        // 组装单渠道测试结果条目，testModel 与 executeChannelTest 内部解析逻辑一致
        String testModel = resolveChannelTestModel(channel, null);
        String error = null;
        if (!result.success()) {
            error = result.localError() == null
                    ? I18nUtils.get("channel.test_failed")
                    : result.localError().getMessage();
        }
        return new ChannelBatchTestItem(
                channel.getId(),
                channel.getName(),
                testModel,
                result.success(),
                result.elapsedMilliseconds(),
                statusChanged,
                error
        );
    }

    private ChannelTestExecutionResult executeChannelTest(Channel channel,
                                                          int testUserId,
                                                          String requestedModel,
                                                          String endpointTypeInput,
                                                          boolean stream) {
        long startedAt = System.currentTimeMillis();
        try {
            if (UNSUPPORTED_TEST_CHANNEL_TYPES.contains(channel.getType())) {
                String channelTypeName = ChannelConstants.getChannelTypeName(
                        channel.getType() == null ? ChannelConstants.CHANNEL_TYPE_UNKNOWN : channel.getType()
                );
                return ChannelTestExecutionResult.failure(
                        new IllegalStateException(channelTypeName + " channel test is not supported"),
                        null,
                        elapsedMilliseconds(startedAt)
                );
            }

            String resolvedModel = resolveChannelTestModel(channel, requestedModel);
            ChannelTestPlan testPlan = buildChannelTestPlan(channel, resolvedModel, endpointTypeInput);
            RelayInfo info = buildTestRelayInfo(channel, testPlan, testUserId, stream);
            Object request = buildTestRequest(testPlan.modelName(), testPlan.endpointType(), channel, stream);
            info.setRequest(request);
            info.setBillingRequestInput(BillingExprHelper.buildBillingExprRequestInputFromRequest(
                    request,
                    info.getRequestHeaders()
            ));

            ModelMappedHelper.apply(info, channel.getModelMapping(), request);
            updateRequestModelName(request, info.getUpstreamModelName());

            IAdaptor adaptor = RelayAdaptor.getAdaptor(testPlan.apiType());
            if (adaptor == null) {
                RelayException adaptorError = newApiError(
                        "invalid api type: " + testPlan.apiType() + ", adaptor is nil",
                        ErrorCode.INVALID_API_TYPE,
                        500
                );
                return ChannelTestExecutionResult.failure(adaptorError, adaptorError, elapsedMilliseconds(startedAt));
            }
            adaptor.init(info);

            Object convertedRequest = convertTestRequest(adaptor, info, request);
            byte[] requestBody = encodeTestRequestBody(convertedRequest);
            if (info.getParamOverride() != null && !info.getParamOverride().isEmpty()) {
                requestBody = OverrideUtils.applyParamOverrideWithRelayInfo(requestBody, info);
            }

            InMemoryHttpServletResponse response = new InMemoryHttpServletResponse();
            info.setResponse(response.asResponse());

            HttpResponse<?> upstreamResponse = (HttpResponse<?>) adaptor.doRequest(info, requestBody);
            if (upstreamResponse != null && upstreamResponse.statusCode() != 200) {
                RelayException badResponseError = buildBadResponseError(upstreamResponse);
                return ChannelTestExecutionResult.failure(badResponseError, badResponseError, elapsedMilliseconds(startedAt));
            }

            IAdaptor.DoResponseResult doResponseResult = adaptor.doResponse(info, upstreamResponse);
            if (doResponseResult != null && doResponseResult.getError() != null) {
                RelayException responseError = doResponseResult.getError();
                return ChannelTestExecutionResult.failure(responseError, responseError, elapsedMilliseconds(startedAt));
            }

            Usage usage = coerceTestUsage(doResponseResult == null ? null : doResponseResult.getUsage(),
                    stream, info.getEstimatePromptTokens());
            validateTestResponseBody(response.body(), stream);
            long elapsed = elapsedMilliseconds(startedAt);
            recordChannelTestLog(channel, testUserId, info, usage, elapsed);
            return ChannelTestExecutionResult.success(elapsed, info, usage);
        } catch (RelayException e) {
            return ChannelTestExecutionResult.failure(e, e, elapsedMilliseconds(startedAt));
        } catch (Exception e) {
            return ChannelTestExecutionResult.failure(e, null, elapsedMilliseconds(startedAt));
        }
    }

    private ChannelTestPlan buildChannelTestPlan(Channel channel, String modelName, String endpointTypeInput) {
        String endpointType = normalizeChannelTestEndpoint(channel, modelName, endpointTypeInput);
        String planModelName = modelName;
        String requestPath = "/v1/chat/completions";
        String relayFormat = RelayFormat.OPENAI;
        int relayMode = RelayModeEnum.CHAT_COMPLETIONS;

        if (endpointType != null) {
            switch (endpointType) {
                case "embeddings" -> {
                    requestPath = "/v1/embeddings";
                    relayFormat = RelayFormat.EMBEDDING;
                    relayMode = RelayModeEnum.EMBEDDINGS;
                }
                case "image-generation" -> {
                    requestPath = "/v1/images/generations";
                    relayFormat = RelayFormat.OPENAI_IMAGE;
                    relayMode = RelayModeEnum.IMAGES_GENERATIONS;
                }
                case "jina-rerank" -> {
                    requestPath = "/v1/rerank";
                    relayFormat = RelayFormat.RERANK;
                    relayMode = RelayModeEnum.RERANK;
                }
                case "openai-response" -> {
                    requestPath = "/v1/responses";
                    relayFormat = RelayFormat.OPENAI_RESPONSES;
                    relayMode = RelayModeEnum.RESPONSES;
                }
                case "openai-response-compact" -> {
                    requestPath = "/v1/responses/compact";
                    relayFormat = RelayFormat.OPENAI_RESPONSES_COMPACTION;
                    relayMode = RelayModeEnum.RESPONSES_COMPACT;
                    if (!planModelName.endsWith(COMPACT_MODEL_SUFFIX)) {
                        planModelName = planModelName + COMPACT_MODEL_SUFFIX;
                    }
                }
                case "anthropic" -> {
                    requestPath = "/v1/messages";
                    relayFormat = RelayFormat.CLAUDE;
                }
                case "gemini" -> {
                    requestPath = "/v1beta/models/" + stripCompactSuffix(planModelName) + ":streamGenerateContent";
                    relayFormat = RelayFormat.GEMINI;
                }
                default -> {
                }
            }
        } else {
            String lowerModel = planModelName.toLowerCase(Locale.ROOT);
            if (lowerModel.contains("rerank")) {
                requestPath = "/v1/rerank";
                relayFormat = RelayFormat.RERANK;
                relayMode = RelayModeEnum.RERANK;
            }
            if (lowerModel.contains("embedding")
                    || lowerModel.startsWith("m3e")
                    || lowerModel.contains("bge-")
                    || lowerModel.contains("embed")
                    || Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_MOKA_AI)) {
                requestPath = "/v1/embeddings";
                relayFormat = RelayFormat.EMBEDDING;
                relayMode = RelayModeEnum.EMBEDDINGS;
            }
            if (Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_VOLC_ENGINE)
                    && planModelName.contains("seedream")) {
                requestPath = "/v1/images/generations";
                relayFormat = RelayFormat.OPENAI_IMAGE;
                relayMode = RelayModeEnum.IMAGES_GENERATIONS;
            }
            if (lowerModel.contains("codex")) {
                requestPath = "/v1/responses";
                relayFormat = RelayFormat.OPENAI_RESPONSES;
                relayMode = RelayModeEnum.RESPONSES;
            }
            if (planModelName.endsWith(COMPACT_MODEL_SUFFIX)) {
                requestPath = "/v1/responses/compact";
                relayFormat = RelayFormat.OPENAI_RESPONSES_COMPACTION;
                relayMode = RelayModeEnum.RESPONSES_COMPACT;
            }
        }

        // apiType 直接使用渠道类型值（CHANNEL_TYPE_*），与中转链路 RelayInfo.setApiType(channelType) 语义一致，
        // 供 RelayAdaptor.getAdaptor(apiType) 按 CHANNEL_TYPE_* 匹配适配器（RelayAdaptor 注释明确）。
        int apiType = channel.getType() != null ? channel.getType() : ChannelConstants.CHANNEL_TYPE_UNKNOWN;
        if (relayMode == RelayModeEnum.RESPONSES_COMPACT
                && apiType != ChannelConstants.CHANNEL_TYPE_OPENAI
                && apiType != ChannelConstants.CHANNEL_TYPE_CODEX) {
            throw new IllegalArgumentException("responses compaction test only supports openai/codex channels");
        }
        return new ChannelTestPlan(planModelName, endpointType, requestPath, relayFormat, relayMode, apiType);
    }

    private RelayInfo buildTestRelayInfo(Channel channel, ChannelTestPlan testPlan, int testUserId, boolean stream) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());
        info.setStream(stream);
        info.setChannelTest(true);
        info.setRelayMode(testPlan.relayMode());
        info.setRelayFormat(testPlan.relayFormat());
        info.initRequestConversionChain();
        info.setRequestURLPath(testPlan.requestPath());
        info.setChannelId(channel.getId() == null ? 0 : channel.getId());
        info.setChannelType(channel.getType() == null ? ChannelConstants.CHANNEL_TYPE_UNKNOWN : channel.getType());
        info.setChannelCreateTime(channel.getCreatedTime() == null ? 0L : channel.getCreatedTime());
        info.setChannelBaseUrl(normalizeBaseUrl(info.getChannelType(), channel.getBaseUrl()));
        info.setApiType(testPlan.apiType());
        info.setApiKey(resolveFirstAvailableKey(channel));
        info.setOrganization(trimToNull(channel.getOpenaiOrganization()));
        info.setOriginModelName(testPlan.modelName());
        info.setUpstreamModelName(testPlan.modelName());
        info.setModelMapped(false);
        info.setParamOverride(parseJsonMap(channel.getParamOverride(), I18nUtils.get("channel.invalid_param_override_json")));
        info.setHeadersOverride(parseJsonMap(channel.getHeaderOverride(), I18nUtils.get("channel.invalid_header_override_json")));
        info.setChannelSetting(parseChannelSetting(channel));
        info.setChannelOtherSettings(parseChannelOtherSettings(channel));
        info.setUserId(testUserId);
        String userGroup = resolveUserGroup(testUserId);
        info.setUserGroup(userGroup);
        info.setUsingGroup(userGroup);
        Map<String, String> requestHeaders = buildTestRequestHeaders(stream);
        info.setRequestHeaders(requestHeaders);
        info.setClientHeaders(requestHeaders);
        applyChannelSpecificTestFields(info, channel);
        return info;
    }

    private Object buildTestRequest(String model, String endpointType, Channel channel, boolean stream) {
        if (endpointType != null) {
            return switch (endpointType) {
                case "embeddings" -> new EmbeddingDTO(model, List.of("hello world"), null, null,
                        null, null, null, null, null, null);
                case "image-generation" -> buildTestImageRequest(model, stream);
                case "jina-rerank" -> buildRerankRequest(model);
                case "openai-response" -> buildResponsesRequest(model, stream);
                case "openai-response-compact" -> new OpenAIResponsesCompactionRequest(
                        model,
                        List.of(Map.of("role", "user", "content", "hi")),
                        null,
                        null
                );
                case "anthropic", "gemini", "openai" -> buildChatRequest(model, stream);
                default -> buildAutoDetectedTestRequest(model, channel, stream);
            };
        }
        return buildAutoDetectedTestRequest(model, channel, stream);
    }

    private Object buildAutoDetectedTestRequest(String model, Channel channel, boolean stream) {
        String lowerModel = model.toLowerCase(Locale.ROOT);
        if (lowerModel.contains("rerank")) {
            return buildRerankRequest(model);
        }
        if (lowerModel.contains("embedding") || lowerModel.startsWith("m3e") || lowerModel.contains("bge-")) {
            return new EmbeddingDTO(model, List.of("hello world"), null, null,
                    null, null, null, null, null, null);
        }
        if (model.endsWith(COMPACT_MODEL_SUFFIX)) {
            return new OpenAIResponsesCompactionRequest(
                    model,
                    List.of(Map.of("role", "user", "content", "hi")),
                    null,
                    null
            );
        }
        if (lowerModel.contains("codex")) {
            return buildResponsesRequest(model, stream);
        }
        if (Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_VOLC_ENGINE) && model.contains("seedream")) {
            return buildTestImageRequest(model, stream);
        }
        return buildChatRequest(model, stream);
    }

    private OpenAIImageDTO buildTestImageRequest(String model, boolean stream) {
        OpenAIImageDTO dto = new OpenAIImageDTO();
        dto.setModel(model);
        dto.setPrompt("a cute cat");
        dto.setN(1);
        dto.setSize("1024x1024");
        dto.setStream(stream);
        return dto;
    }

    private GeneralOpenAIRequest buildChatRequest(String model, boolean stream) {
        GeneralOpenAIRequest request = GeneralOpenAIRequest.builder()
                .model(model)
                .stream(stream)
                .messages(List.of(GeneralOpenAIRequest.Message.builder()
                        .role("user")
                        .content("hi")
                        .build()))
                .build();
        if (stream) {
            request.setStreamOptions(Map.of("include_usage", true));
        }
        if (model.contains("gemini")) {
            request.setMaxTokens(3000);
        } else if (model.toLowerCase(Locale.ROOT).contains("thinking")) {
            if (!model.toLowerCase(Locale.ROOT).contains("claude")) {
                request.setMaxTokens(50);
            }
        } else {
            request.setMaxTokens(16);
        }
        return request;
    }

    private OpenAIResponsesRequest buildResponsesRequest(String model, boolean stream) {
        OpenAIResponsesRequest request = new OpenAIResponsesRequest();
        request.setModel(model);
        request.setInput(List.of(Map.of("role", "user", "content", "hi")));
        request.setStream(stream);
        if (stream) {
            request.setStreamOptions(null);
        }
        return request;
    }

    private RerankRequest buildRerankRequest(String model) {
        RerankRequest request = new RerankRequest();
        request.setModel(model);
        request.setQuery("What is Deep Learning?");
        request.setDocuments(List.of(
                "Deep Learning is a subset of machine learning.",
                "Machine learning is a field of artificial intelligence."
        ));
        request.setTopN(2);
        return request;
    }

    private Object convertTestRequest(IAdaptor adaptor, RelayInfo info, Object request) throws Exception {
        return switch (info.getRelayMode()) {
            case RelayModeEnum.EMBEDDINGS -> adaptor.convertEmbeddingRequest(info, (EmbeddingDTO) request);
            case RelayModeEnum.IMAGES_GENERATIONS -> adaptor.convertImageRequest(info, (OpenAIImageDTO) request);
            case RelayModeEnum.RERANK -> adaptor.convertRerankRequest(info, info.getRelayMode(), (RerankRequest) request);
            case RelayModeEnum.RESPONSES, RelayModeEnum.RESPONSES_COMPACT -> adaptor.convertOpenAIResponsesRequest(
                    info,
                    normalizeResponsesRequest(request)
            );
            default -> adaptor.convertOpenAIRequest(info, request);
        };
    }

    private OpenAIResponsesRequest normalizeResponsesRequest(Object request) {
        if (request instanceof OpenAIResponsesRequest responsesRequest) {
            return responsesRequest;
        }
        if (request instanceof OpenAIResponsesCompactionRequest compactionRequest) {
            OpenAIResponsesRequest responsesRequest = new OpenAIResponsesRequest();
            responsesRequest.setModel(compactionRequest.getModel());
            responsesRequest.setInput(compactionRequest.getInput());
            responsesRequest.setInstructions(compactionRequest.getInstructions());
            responsesRequest.setPreviousResponseId(compactionRequest.getPreviousResponseId());
            return responsesRequest;
        }
        throw new IllegalArgumentException("invalid response request type");
    }

    private byte[] encodeTestRequestBody(Object requestBody) {
        if (requestBody instanceof byte[] bytes) {
            return bytes;
        }
        if (requestBody instanceof String stringBody) {
            return stringBody.getBytes(StandardCharsets.UTF_8);
        }
        return Convert.toJSONString(requestBody).getBytes(StandardCharsets.UTF_8);
    }

    private void updateRequestModelName(Object request, String modelName) {
        if (request instanceof GeneralOpenAIRequest chatRequest) {
            chatRequest.setModel(modelName);
            return;
        }
        if (request instanceof EmbeddingDTO embeddingRequest) {
            embeddingRequest.setModel(modelName);
            return;
        }
        if (request instanceof OpenAIImageDTO imageRequest) {
            imageRequest.setModel(modelName);
            return;
        }
        if (request instanceof RerankRequest rerankRequest) {
            rerankRequest.setModel(modelName);
            return;
        }
        if (request instanceof OpenAIResponsesRequest responsesRequest) {
            responsesRequest.setModel(modelName);
            return;
        }
        if (request instanceof OpenAIResponsesCompactionRequest compactionRequest) {
            compactionRequest.setModel(modelName);
        }
    }

    private String resolveChannelTestModel(Channel channel, String requestedModel) {
        String testModel = trimToNull(requestedModel);
        if (testModel != null) {
            return testModel;
        }
        testModel = trimToNull(channel.getTestModel());
        if (testModel != null) {
            return testModel;
        }
        List<String> models = splitCommaSeparated(channel.getModels());
        if (!models.isEmpty()) {
            return models.get(0);
        }
        return "gpt-4o-mini";
    }

    private String normalizeChannelTestEndpoint(Channel channel, String modelName, String endpointType) {
        String normalized = trimToNull(endpointType);
        if (normalized != null) {
            return normalized;
        }
        if (modelName != null && modelName.endsWith(COMPACT_MODEL_SUFFIX)) {
            return EndpointTypeEnum.OPENAI_RESPONSE_COMPACT.getValue();
        }
        if (channel != null && Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_CODEX)) {
            return EndpointTypeEnum.OPENAI_RESPONSE.getValue();
        }
        return null;
    }

    private int resolveChannelTestUserId(HttpServletRequest request) {
        Integer userId = request == null ? null : toInteger(request.getAttribute("id"));
        if (userId != null && userId > 0) {
            return userId;
        }
        User rootUser = userService.getRootUser();
        if (rootUser == null || rootUser.getId() == null || rootUser.getId() <= 0) {
            throw new IllegalStateException("failed to resolve channel test user");
        }
        return rootUser.getId();
    }

    private String resolveUserGroup(int userId) {
        String group = trimToNull(userService.getUserGroup(userId));
        return group == null ? "default" : group;
    }

    private Map<String, String> buildTestRequestHeaders(boolean stream) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", stream ? "text/event-stream" : "application/json");
        return headers;
    }

    private void applyChannelSpecificTestFields(RelayInfo info, Channel channel) {
        if (channel.getType() == null) {
            return;
        }
        switch (channel.getType()) {
            case ChannelConstants.CHANNEL_TYPE_AZURE,
                    ChannelConstants.CHANNEL_TYPE_XUNFEI,
                    ChannelConstants.CHANNEL_TYPE_GEMINI,
                    ChannelConstants.CHANNEL_TYPE_MOKA_AI -> info.setApiVersion(trimToNull(channel.getOther()));
            default -> {
            }
        }
    }

    private Map<String, Object> parseJsonMap(String raw, String errorMessage) {
        String normalized = trimToNull(raw);
        if (normalized == null || Objects.equals(normalized, "{}")) {
            return Map.of();
        }
        try {
            Map<String, Object> result = Convert.toJSONObject(normalized);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private ChannelSettingsDTO parseChannelSetting(Channel channel) {
        String raw = trimToNull(channel.getSetting());
        if (raw == null) {
            return new ChannelSettingsDTO();
        }
        try {
            ChannelSettingsDTO setting = Convert.toJavaBean(raw, ChannelSettingsDTO.class);
            return setting == null ? new ChannelSettingsDTO() : setting;
        } catch (Exception e) {
            throw new IllegalStateException(I18nUtils.get("channel.setting_parse_failed"));
        }
    }

    private RelayException buildBadResponseError(HttpResponse<?> upstreamResponse) throws IOException {
        int statusCode = upstreamResponse == null ? 500 : upstreamResponse.statusCode();
        String message = "upstream status: " + statusCode;
        if (upstreamResponse != null && upstreamResponse.body() instanceof InputStream inputStream) {
            try (InputStream bodyStream = inputStream) {
                byte[] responseBody = bodyStream.readAllBytes();
                String parsedMessage = extractErrorMessage(responseBody);
                if (parsedMessage != null) {
                    message = parsedMessage;
                }
            }
        }
        return newApiError(message, ErrorCode.BAD_RESPONSE, statusCode);
    }

    private Usage coerceTestUsage(Object usageObject, boolean stream, int estimatePromptTokens) {
        if (usageObject instanceof Usage usage) {
            return usage;
        }
        if (usageObject == null) {
            if (!stream) {
                throw new IllegalStateException("usage is nil");
            }
            Usage usage = new Usage();
            usage.setPromptTokens(estimatePromptTokens);
            usage.setTotalTokens(estimatePromptTokens);
            return usage;
        }
        try {
            return Convert.toJavaBean(usageObject, Usage.class);
        } catch (IllegalArgumentException e) {
            if (!stream) {
                throw new IllegalStateException("invalid usage type: " + usageObject.getClass().getName());
            }
            Usage usage = new Usage();
            usage.setPromptTokens(estimatePromptTokens);
            usage.setTotalTokens(estimatePromptTokens);
            return usage;
        }
    }

    private void validateTestResponseBody(byte[] responseBody, boolean stream) {
        String upstreamError = extractErrorMessage(responseBody);
        if (upstreamError != null) {
            throw new IllegalStateException("upstream error: " + upstreamError);
        }
        if (stream) {
            validateStreamResponseBody(responseBody);
            return;
        }
        if (responseBody == null || responseBody.length == 0) {
            throw new IllegalStateException("response body is empty");
        }
    }

    private void validateStreamResponseBody(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            throw new IllegalStateException("stream response body is empty");
        }
        String body = new String(responseBody, StandardCharsets.UTF_8).trim();
        for (String line : body.split("\n")) {
            String normalized = line.trim();
            if (!normalized.startsWith("data:")) {
                continue;
            }
            String payload = normalized.substring("data:".length()).trim();
            if (!payload.isEmpty() && !Objects.equals(payload, "[DONE]")) {
                return;
            }
        }
        throw new IllegalStateException("stream response body does not contain a valid stream event");
    }

    private String extractErrorMessage(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        String normalizedBody = new String(responseBody, StandardCharsets.UTF_8).trim();
        if (normalizedBody.isEmpty()) {
            return null;
        }
        if (normalizedBody.startsWith("{") || normalizedBody.startsWith("[")) {
            String directError = extractErrorMessageFromJson(normalizedBody);
            if (directError != null) {
                return directError;
            }
        }
        for (String line : normalizedBody.split("\n")) {
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("data:")) {
                continue;
            }
            String payload = normalizedLine.substring("data:".length()).trim();
            if (payload.isEmpty() || Objects.equals(payload, "[DONE]")) {
                continue;
            }
            String streamError = extractErrorMessageFromJson(payload);
            if (streamError != null) {
                return streamError;
            }
        }
        return null;
    }

    private String extractErrorMessageFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode errorNode = root.path("error");
            if (errorNode.isMissingNode() || errorNode.isNull()) {
                return null;
            }
            String message = trimToNull(errorNode.path("message").asText(null));
            if (message == null) {
                message = trimToNull(errorNode.path("error").path("message").asText(null));
            }
            if (message == null && errorNode.isTextual()) {
                message = trimToNull(errorNode.asText());
            }
            return message == null ? "upstream returned error payload" : message;
        } catch (Exception e) {
            return null;
        }
    }

    private void recordChannelTestLog(Channel channel,
                                      int testUserId,
                                      RelayInfo info,
                                      Usage usage,
                                      long elapsedMilliseconds) {
        try {
            Log logEntry = new Log();
            logEntry.setUserId(testUserId);
            logEntry.setType(LOG_TYPE_CONSUME);
            logEntry.setChannelId(channel.getId());
            logEntry.setChannelName(channel.getName());
            logEntry.setModelName(info.getOriginModelName());
            logEntry.setTokenName("模型测试");
            logEntry.setContent("模型测试");
            logEntry.setQuota(0);
            logEntry.setPromptTokens(usage == null ? 0 : usage.getPromptTokens());
            logEntry.setCompletionTokens(usage == null ? 0 : usage.getCompletionTokens());
            logEntry.setUseTime((int) (elapsedMilliseconds / 1000));
            logEntry.setIsStream(info.isStream());
            logEntry.setGroup(info.getUsingGroup());
            logEntry.setCreatedAt(currentUnixSeconds());

            Map<String, Object> other = new LinkedHashMap<>();
            other.put("is_channel_test", true);
            other.put("request_path", info.getRequestURLPath());
            other.put("upstream_model_name", info.getUpstreamModelName());
            other.put("response_time_ms", elapsedMilliseconds);
            if (info.isModelMapped()) {
                other.put("is_model_mapped", true);
            }
            logEntry.setOther(Convert.toJSONString(other));
            logMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("记录渠道测试日志失败, channelId={}: {}", channel.getId(), e.getMessage());
        }
    }

    private void updateChannelTestMetrics(Channel channel, long elapsedMilliseconds) {
        Channel update = new Channel();
        update.setId(channel.getId());
        update.setTestTime(currentUnixSeconds());
        update.setResponseTime((int) Math.min(elapsedMilliseconds, Integer.MAX_VALUE));
        channelMapper.updateById(update);
        channel.setTestTime(update.getTestTime());
        channel.setResponseTime(update.getResponseTime());
    }

    private boolean updateChannelStatus(Channel channel, int targetStatus) {
        if (channel.getId() == null || Objects.equals(channel.getStatus(), targetStatus)) {
            return false;
        }
        Channel update = new Channel();
        update.setId(channel.getId());
        update.setStatus(targetStatus);
        channelMapper.updateById(update);
        abilityMapper.update(null, new LambdaUpdateWrapper<Ability>()
                .eq(Ability::getChannelId, channel.getId())
                .set(Ability::getEnabled, targetStatus == CommonConstants.CHANNEL_STATUS_ENABLED));
        channel.setStatus(targetStatus);
        return true;
    }

    private void notifyChannelStatusChanged(Channel channel, int status, String reason) {
        String title;
        String content;
        if (status == CommonConstants.CHANNEL_STATUS_ENABLED) {
            title = String.format("通道「%s」（#%d）已被启用", channel.getName(), channel.getId());
            content = title;
        } else {
            title = String.format("通道「%s」（#%d）已被禁用", channel.getName(), channel.getId());
            content = reason == null || reason.isBlank()
                    ? title
                    : title + "，原因：" + reason;
        }
        userNotifyService.notifyRootUser(
                UserNotifyService.EVENT_CHANNEL_UPDATE + "_" + channel.getId() + "_" + status,
                title,
                content
        );
    }

    private boolean shouldDisableChannel(RelayException error) {
        if (!CommonConstants.automaticDisableChannelEnabled || error == null) {
            return false;
        }
        if (error.isSkipRetry()) {
            return false;
        }
        String errorCode = trimToNull(error.getErrorCode());
        if (errorCode != null && errorCode.startsWith("channel:")) {
            return true;
        }
        if (error.getStatusCode() == 401) {
            return true;
        }
        String lowerMessage = trimToNull(error.getMessage());
        if (lowerMessage == null) {
            return false;
        }
        String normalizedMessage = lowerMessage.toLowerCase(Locale.ROOT);
        for (String keyword : AUTOMATIC_DISABLE_KEYWORDS) {
            if (normalizedMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldEnableChannel(RelayException error, Integer status) {
        return CommonConstants.automaticEnableChannelEnabled
                && error == null
                && Objects.equals(status, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED);
    }

    private long resolveChannelDisableThresholdMillis() {
        if (!CommonConstants.automaticDisableChannelEnabled) {
            return Long.MAX_VALUE;
        }
        if (CommonConstants.channelDisableThreshold <= 0D) {
            return Long.MAX_VALUE;
        }
        return (long) (CommonConstants.channelDisableThreshold * 1000);
    }

    private void sleepBetweenChannelTests() {
        if (CommonConstants.requestInterval <= 0) {
            return;
        }
        try {
            Thread.sleep(CommonConstants.requestInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18nUtils.get("channel.batch_test_interrupted"));
        }
    }

    private boolean shouldUseStreamForAutomaticChannelTest(Channel channel) {
        return channel != null && Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_CODEX);
    }


    private RelayException newApiError(String message, String errorCode, int statusCode) {
        RelayException error = new RelayException(message, errorCode);
        error.setStatusCode(statusCode);
        return error;
    }

    private long elapsedMilliseconds(long startedAt) {
        return Math.max(System.currentTimeMillis() - startedAt, 0L);
    }

    private double toSeconds(long milliseconds) {
        return milliseconds / 1000.0d;
    }

    private boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return defaultValue;
    }

    private String stripCompactSuffix(String modelName) {
        if (modelName == null || !modelName.endsWith(COMPACT_MODEL_SUFFIX)) {
            return modelName;
        }
        return modelName.substring(0, modelName.length() - COMPACT_MODEL_SUFFIX.length());
    }

    private Map<String, Object> getMultiKeyStatus(ChannelIPO.MultiKeyManage ipo, Channel channel, ChannelInfoDTO info) {
        List<String> keys = parseKeys(channel.getKey());
        int page = Math.max(ipo.getPage() != null ? ipo.getPage() : 1, 1);
        int pageSize = Math.max(ipo.getPageSize() != null ? ipo.getPageSize() : 50, 1);
        Integer statusFilter = ipo.getStatus();

        int enabledCount = 0;
        int manualDisabledCount = 0;
        int autoDisabledCount = 0;
        List<Map<String, Object>> all = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            int status = getKeyStatus(info, i);
            switch (status) {
                case CommonConstants.CHANNEL_STATUS_ENABLED -> enabledCount++;
                case CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED -> manualDisabledCount++;
                case CommonConstants.CHANNEL_STATUS_AUTO_DISABLED -> autoDisabledCount++;
                default -> {
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i);
            item.put("status", status);
            item.put("disabledTime", info.getMultiKeyDisabledTime() == null ? 0L
                    : info.getMultiKeyDisabledTime().getOrDefault(i, 0L));
            item.put("reason", info.getMultiKeyDisabledReason() == null ? ""
                    : info.getMultiKeyDisabledReason().getOrDefault(i, ""));
            item.put("keyPreview", previewKey(keys.get(i)));
            all.add(item);
        }

        List<Map<String, Object>> filtered = all;
        if (statusFilter != null) {
            filtered = all.stream()
                    .filter(item -> Objects.equals(item.get("status"), statusFilter))
                    .toList();
        }

        int total = filtered.size();
        int totalPages = Math.max((total + pageSize - 1) / pageSize, 1);
        page = Math.min(page, totalPages);
        int start = Math.min((page - 1) * pageSize, total);
        int end = Math.min(start + pageSize, total);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keys", filtered.subList(start, end));
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("totalPages", totalPages);
        data.put("enabledCount", enabledCount);
        data.put("manualDisabledCount", manualDisabledCount);
        data.put("autoDisabledCount", autoDisabledCount);
        return data;
    }

    private Map<String, Object> disableSingleKey(ChannelIPO.MultiKeyManage ipo, Channel channel, ChannelInfoDTO info) {
        Integer keyIndex = ipo.getKeyIndex();
        validateKeyIndex(keyIndex, info.getMultiKeySize());
        ensureMultiKeyMaps(info);
        info.getMultiKeyStatusList().put(keyIndex, CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED);
        persistChannelInfo(channel, info, null);
        refreshChannelCache();
        return messageOnly(I18nUtils.get("channel.key_disabled"));
    }

    private Map<String, Object> enableSingleKey(ChannelIPO.MultiKeyManage ipo, Channel channel, ChannelInfoDTO info) {
        Integer keyIndex = ipo.getKeyIndex();
        validateKeyIndex(keyIndex, info.getMultiKeySize());
        if (info.getMultiKeyStatusList() != null) {
            info.getMultiKeyStatusList().remove(keyIndex);
        }
        if (info.getMultiKeyDisabledTime() != null) {
            info.getMultiKeyDisabledTime().remove(keyIndex);
        }
        if (info.getMultiKeyDisabledReason() != null) {
            info.getMultiKeyDisabledReason().remove(keyIndex);
        }
        persistChannelInfo(channel, info, null);
        refreshChannelCache();
        return messageOnly(I18nUtils.get("channel.key_enabled"));
    }

    private Map<String, Object> enableAllKeys(Channel channel, ChannelInfoDTO info) {
        int enabledCount = info.getMultiKeyStatusList() == null ? 0 : info.getMultiKeyStatusList().size();
        info.setMultiKeyStatusList(new LinkedHashMap<>());
        info.setMultiKeyDisabledTime(new LinkedHashMap<>());
        info.setMultiKeyDisabledReason(new LinkedHashMap<>());
        persistChannelInfo(channel, info, null);
        refreshChannelCache();
        return messageOnly(I18nUtils.get("channel.keys_enabled", enabledCount));
    }

    private Map<String, Object> disableAllKeys(Channel channel, ChannelInfoDTO info) {
        ensureMultiKeyMaps(info);
        int disabledCount = 0;
        for (int i = 0; i < info.getMultiKeySize(); i++) {
            if (getKeyStatus(info, i) == CommonConstants.CHANNEL_STATUS_ENABLED) {
                info.getMultiKeyStatusList().put(i, CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED);
                disabledCount++;
            }
        }
        if (disabledCount == 0) {
            throw new IllegalArgumentException(I18nUtils.get("channel.no_keys_to_disable"));
        }
        persistChannelInfo(channel, info, null);
        refreshChannelCache();
        return messageOnly(I18nUtils.get("channel.keys_disabled", disabledCount));
    }

    private Map<String, Object> deleteSingleKey(ChannelIPO.MultiKeyManage ipo, Channel channel, ChannelInfoDTO info) {
        Integer keyIndex = ipo.getKeyIndex();
        validateKeyIndex(keyIndex, info.getMultiKeySize());

        List<String> keys = parseKeys(channel.getKey());
        List<String> remainingKeys = new ArrayList<>();
        Map<Integer, Integer> newStatusList = new LinkedHashMap<>();
        Map<Integer, Long> newDisabledTime = new LinkedHashMap<>();
        Map<Integer, String> newDisabledReason = new LinkedHashMap<>();
        int newIndex = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (i == keyIndex) {
                continue;
            }
            remainingKeys.add(keys.get(i));
            copyKeyMetadata(info, i, newIndex, newStatusList, newDisabledTime, newDisabledReason, false);
            newIndex++;
        }
        if (remainingKeys.isEmpty()) {
            throw new IllegalArgumentException(I18nUtils.get("channel.cannot_delete_last_key"));
        }

        info.setMultiKeySize(remainingKeys.size());
        info.setMultiKeyStatusList(newStatusList);
        info.setMultiKeyDisabledTime(newDisabledTime);
        info.setMultiKeyDisabledReason(newDisabledReason);
        persistChannelInfo(channel, info, String.join("\n", remainingKeys));
        refreshChannelCache();
        return messageOnly(I18nUtils.get("channel.key_deleted"));
    }

    private Map<String, Object> deleteAutoDisabledKeys(Channel channel, ChannelInfoDTO info) {
        List<String> keys = parseKeys(channel.getKey());
        List<String> remainingKeys = new ArrayList<>();
        Map<Integer, Integer> newStatusList = new LinkedHashMap<>();
        Map<Integer, Long> newDisabledTime = new LinkedHashMap<>();
        Map<Integer, String> newDisabledReason = new LinkedHashMap<>();
        int deletedCount = 0;
        int newIndex = 0;
        for (int i = 0; i < keys.size(); i++) {
            int status = getKeyStatus(info, i);
            if (status == CommonConstants.CHANNEL_STATUS_AUTO_DISABLED) {
                deletedCount++;
                continue;
            }
            remainingKeys.add(keys.get(i));
            copyKeyMetadata(info, i, newIndex, newStatusList, newDisabledTime, newDisabledReason, true);
            newIndex++;
        }
        if (deletedCount == 0) {
            throw new IllegalArgumentException(I18nUtils.get("channel.no_auto_disabled_keys"));
        }

        info.setMultiKeySize(remainingKeys.size());
        info.setMultiKeyStatusList(newStatusList);
        info.setMultiKeyDisabledTime(newDisabledTime);
        info.setMultiKeyDisabledReason(newDisabledReason);
        persistChannelInfo(channel, info, String.join("\n", remainingKeys));
        refreshChannelCache();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", I18nUtils.get("channel.keys_auto_disabled_deleted", deletedCount));
        result.put("data", deletedCount);
        return result;
    }

    private void copyKeyMetadata(ChannelInfoDTO info,
                                 int oldIndex,
                                 int newIndex,
                                 Map<Integer, Integer> statusList,
                                 Map<Integer, Long> disabledTime,
                                 Map<Integer, String> disabledReason,
                                 boolean skipAutoDisabled) {
        int status = getKeyStatus(info, oldIndex);
        if (status != CommonConstants.CHANNEL_STATUS_ENABLED) {
            if (!(skipAutoDisabled && status == CommonConstants.CHANNEL_STATUS_AUTO_DISABLED)) {
                statusList.put(newIndex, status);
            }
            if (info.getMultiKeyDisabledTime() != null && info.getMultiKeyDisabledTime().containsKey(oldIndex)) {
                disabledTime.put(newIndex, info.getMultiKeyDisabledTime().get(oldIndex));
            }
            if (info.getMultiKeyDisabledReason() != null && info.getMultiKeyDisabledReason().containsKey(oldIndex)) {
                disabledReason.put(newIndex, info.getMultiKeyDisabledReason().get(oldIndex));
            }
        }
    }

    private CodexAuthorizationInput parseCodexAuthorizationInput(String input) {
        String value = trimToNull(input);
        if (value == null) {
            throw new IllegalArgumentException(I18nUtils.get("channel.auth_info_parse_failed"));
        }
        if (value.contains("#")) {
            String[] parts = value.split("#", 2);
            return new CodexAuthorizationInput(trimToNull(parts[0]), trimToNull(parts[1]));
        }
        if (value.contains("code=")) {
            Map<String, String> queryParameters = extractQueryParameters(value);
            String code = trimToNull(queryParameters.get("code"));
            String state = trimToNull(queryParameters.get("state"));
            if (code != null || state != null) {
                return new CodexAuthorizationInput(code, state);
            }
        }
        return new CodexAuthorizationInput(value, null);
    }

    private ChannelUpstreamDetectResult detectChannelUpstreamModelUpdates(Channel channel,
                                                                          boolean force,
                                                                          boolean allowAutoApply) {
        ChannelOtherSettingsDTO settings = parseChannelOtherSettings(channel);
        long now = currentUnixSeconds();
        if (!force) {
            long minInterval = Math.max(getLongOption("CHANNEL_UPSTREAM_MODEL_UPDATE_MIN_CHECK_INTERVAL_SECONDS", 300L), 0L);
            Long lastCheckTime = settings.getUpstreamModelUpdateLastCheckTime();
            if (lastCheckTime != null && lastCheckTime > 0 && now - lastCheckTime < minInterval) {
                return new ChannelUpstreamDetectResult(
                        channel.getId(),
                        channel.getName(),
                        normalizeModelNames(settings.getUpstreamModelUpdateLastDetectedModels()),
                        normalizeModelNames(settings.getUpstreamModelUpdateLastRemovedModels()),
                        lastCheckTime,
                        0,
                        false
                );
            }
        }

        PendingUpstreamModelChanges pendingChanges = collectPendingUpstreamModelChanges(channel, settings);
        List<String> pendingAddModels = pendingChanges.pendingAddModels();
        List<String> pendingRemoveModels = pendingChanges.pendingRemoveModels();
        settings.setUpstreamModelUpdateLastCheckTime(now);

        int autoAddedModels = 0;
        boolean modelsChanged = false;
        if (allowAutoApply && Boolean.TRUE.equals(settings.getUpstreamModelUpdateAutoSyncEnabled()) && !pendingAddModels.isEmpty()) {
            List<String> originModels = splitCommaSeparated(channel.getModels());
            List<String> mergedModels = mergeModelNames(originModels, pendingAddModels);
            if (mergedModels.size() > originModels.size()) {
                autoAddedModels = mergedModels.size() - originModels.size();
                channel.setModels(String.join(",", mergedModels));
                modelsChanged = true;
                recreateChannelAbilities(channel);
            }
            settings.setUpstreamModelUpdateLastDetectedModels(List.of());
        } else {
            settings.setUpstreamModelUpdateLastDetectedModels(pendingAddModels);
        }
        settings.setUpstreamModelUpdateLastRemovedModels(pendingRemoveModels);
        persistChannelOtherSettings(channel, settings, modelsChanged);

        return new ChannelUpstreamDetectResult(
                channel.getId(),
                channel.getName(),
                normalizeModelNames(settings.getUpstreamModelUpdateLastDetectedModels()),
                normalizeModelNames(settings.getUpstreamModelUpdateLastRemovedModels()),
                settings.getUpstreamModelUpdateLastCheckTime() == null ? now : settings.getUpstreamModelUpdateLastCheckTime(),
                autoAddedModels,
                modelsChanged
        );
    }

    private ChannelUpstreamApplyResult applyChannelUpstreamModelUpdates(Channel channel,
                                                                       List<String> addModelsInput,
                                                                       List<String> ignoreModelsInput,
                                                                       List<String> removeModelsInput) {
        ChannelOtherSettingsDTO settings = parseChannelOtherSettings(channel);
        List<String> pendingAddModels = normalizeModelNames(settings.getUpstreamModelUpdateLastDetectedModels());
        List<String> pendingRemoveModels = normalizeModelNames(settings.getUpstreamModelUpdateLastRemovedModels());
        List<String> addModels = intersectModelNames(addModelsInput, pendingAddModels);
        List<String> ignoreModels = intersectModelNames(ignoreModelsInput, pendingAddModels);
        List<String> removeModels = intersectModelNames(removeModelsInput, pendingRemoveModels);
        removeModels = subtractModelNames(removeModels, addModels);

        List<String> originModels = splitCommaSeparated(channel.getModels());
        List<String> nextModels = applySelectedModelChanges(originModels, addModels, removeModels);
        boolean modelsChanged = !Objects.equals(originModels, nextModels);
        if (modelsChanged) {
            channel.setModels(String.join(",", nextModels));
        }

        List<String> ignoredModels = mergeModelNames(settings.getUpstreamModelUpdateIgnoredModels(), ignoreModels);
        if (!addModels.isEmpty()) {
            ignoredModels = subtractModelNames(ignoredModels, addModels);
        }
        settings.setUpstreamModelUpdateIgnoredModels(ignoredModels);

        List<String> remainingModels = subtractModelNames(pendingAddModels, mergeModelNames(addModels, ignoreModels));
        List<String> remainingRemoveModels = subtractModelNames(pendingRemoveModels, removeModels);
        settings.setUpstreamModelUpdateLastDetectedModels(remainingModels);
        settings.setUpstreamModelUpdateLastRemovedModels(remainingRemoveModels);
        settings.setUpstreamModelUpdateLastCheckTime(currentUnixSeconds());
        persistChannelOtherSettings(channel, settings, modelsChanged);

        if (modelsChanged) {
            recreateChannelAbilities(channel);
        }

        return new ChannelUpstreamApplyResult(
                addModels,
                removeModels,
                ignoreModels,
                remainingModels,
                remainingRemoveModels,
                modelsChanged
        );
    }

    private PendingUpstreamModelChanges collectPendingUpstreamModelChanges(Channel channel, ChannelOtherSettingsDTO settings) {
        try {
            List<String> upstreamModels = fetchChannelUpstreamModelIds(channel);
            List<String> pendingAddModels = collectPendingUpstreamModelChangesFromModels(
                    splitCommaSeparated(channel.getModels()),
                    upstreamModels,
                    settings.getUpstreamModelUpdateIgnoredModels(),
                    normalizeChannelModelMapping(channel)
            );
            List<String> pendingRemoveModels = collectPendingRemoveModelsFromUpstream(
                    splitCommaSeparated(channel.getModels()),
                    upstreamModels,
                    normalizeChannelModelMapping(channel)
            );
            return new PendingUpstreamModelChanges(pendingAddModels, pendingRemoveModels);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18nUtils.get("channel.upstream_model_detection_interrupted"));
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private List<String> collectPendingUpstreamModelChangesFromModels(List<String> localModels,
                                                                      List<String> upstreamModels,
                                                                      List<String> ignoredModels,
                                                                      Map<String, String> modelMapping) {
        List<String> normalizedLocalModels = normalizeModelNames(localModels);
        List<String> normalizedUpstreamModels = normalizeModelNames(upstreamModels);
        Set<String> localSet = new LinkedHashSet<>(normalizedLocalModels);
        Set<String> ignoredSet = new LinkedHashSet<>(normalizeModelNames(ignoredModels));
        Set<String> redirectTargetSet = new LinkedHashSet<>(modelMapping.values());
        List<String> pendingAddModels = new ArrayList<>();
        for (String modelName : normalizedUpstreamModels) {
            if (localSet.contains(modelName) || redirectTargetSet.contains(modelName)) {
                continue;
            }
            if (matchesIgnoredModel(ignoredSet, modelName)) {
                continue;
            }
            pendingAddModels.add(modelName);
        }
        return normalizeModelNames(pendingAddModels);
    }

    private List<String> collectPendingRemoveModelsFromUpstream(List<String> localModels,
                                                                List<String> upstreamModels,
                                                                Map<String, String> modelMapping) {
        Set<String> upstreamSet = new LinkedHashSet<>(normalizeModelNames(upstreamModels));
        Set<String> redirectSourceSet = new LinkedHashSet<>(modelMapping.keySet());
        List<String> pendingRemoveModels = new ArrayList<>();
        for (String modelName : normalizeModelNames(localModels)) {
            if (redirectSourceSet.contains(modelName)) {
                continue;
            }
            if (!upstreamSet.contains(modelName)) {
                pendingRemoveModels.add(modelName);
            }
        }
        return normalizeModelNames(pendingRemoveModels);
    }

    private boolean matchesIgnoredModel(Set<String> ignoredModels, String modelName) {
        for (String ignoredModel : ignoredModels) {
            if (ignoredModel.startsWith("regex:")) {
                String regex = trimToNull(ignoredModel.substring("regex:".length()));
                if (regex != null) {
                    try {
                        if (Pattern.matches(regex, modelName)) {
                            return true;
                        }
                    } catch (RuntimeException ignored) {
                        // 非法正则按未命中处理，保持与 Go 的宽容语义一致。
                    }
                }
                continue;
            }
            if (Objects.equals(ignoredModel, modelName)) {
                return true;
            }
        }
        return false;
    }

    private List<String> applySelectedModelChanges(List<String> originModels, List<String> addModels, List<String> removeModels) {
        List<String> normalizedAddModels = normalizeModelNames(addModels);
        List<String> normalizedRemoveModels = subtractModelNames(removeModels, normalizedAddModels);
        return subtractModelNames(mergeModelNames(originModels, normalizedAddModels), normalizedRemoveModels);
    }

    private Map<String, String> normalizeChannelModelMapping(Channel channel) {
        String rawMapping = trimToNull(channel.getModelMapping());
        if (rawMapping == null || Objects.equals(rawMapping, "{}")) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = (Map<String, String>) (Map<?, ?>) Convert.toJSONObject(rawMapping);
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : parsed.entrySet()) {
                String source = trimToNull(entry.getKey());
                String target = trimToNull(entry.getValue());
                if (source != null && target != null) {
                    normalized.put(source, target);
                }
            }
            return normalized;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> normalizeModelNames(List<String> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String model : models) {
            String trimmed = trimToNull(model);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> mergeModelNames(List<String> base, List<String> appended) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(normalizeModelNames(base));
        merged.addAll(normalizeModelNames(appended));
        return List.copyOf(merged);
    }

    private List<String> subtractModelNames(List<String> base, List<String> removed) {
        Set<String> removedSet = new LinkedHashSet<>(normalizeModelNames(removed));
        List<String> result = new ArrayList<>();
        for (String model : normalizeModelNames(base)) {
            if (!removedSet.contains(model)) {
                result.add(model);
            }
        }
        return List.copyOf(result);
    }

    private List<String> intersectModelNames(List<String> base, List<String> allowed) {
        Set<String> allowedSet = new LinkedHashSet<>(normalizeModelNames(allowed));
        List<String> result = new ArrayList<>();
        for (String model : normalizeModelNames(base)) {
            if (allowedSet.contains(model)) {
                result.add(model);
            }
        }
        return List.copyOf(result);
    }

    private double updateChannelBalance(Channel channel) {
        int channelType = channel.getType() == null ? ChannelConstants.CHANNEL_TYPE_UNKNOWN : channel.getType();
        return switch (channelType) {
            case ChannelConstants.CHANNEL_TYPE_OPENAI, ChannelConstants.CHANNEL_TYPE_CUSTOM -> updateOpenAICompatibleBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_AI_PROXY -> updateAiProxyBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_API2GPT -> updateApi2GptBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_AIGC2D -> updateAigc2dBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_SILICON_FLOW -> updateSiliconFlowBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_DEEP_SEEK -> updateDeepSeekBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_OPEN_ROUTER -> updateOpenRouterBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_MOONSHOT -> updateMoonshotBalance(channel);
            case ChannelConstants.CHANNEL_TYPE_AZURE -> throw new IllegalStateException(I18nUtils.get("channel.not_implemented"));
            default -> throw new IllegalStateException(I18nUtils.get("channel.not_implemented"));
        };
    }

    private double updateOpenAICompatibleBalance(Channel channel) {
        String baseUrl = resolveBaseUrl(channel);
        String key = firstKey(channel.getKey());
        JsonNode subscription = requestBalanceJson(channel, baseUrl + "/v1/dashboard/billing/subscription", authHeaders(channel, key));
        boolean hasPaymentMethod = subscription.path("has_payment_method").asBoolean(false);
        BigDecimal hardLimitUsd = decimalOrZero(subscription.path("hard_limit_usd"));
        // OpenAI billing 端点已废弃，多数中转站返回占位符 hard_limit（如 1 亿），查询结果不可信
        if (hardLimitUsd.compareTo(BigDecimal.valueOf(1_000_000D)) >= 0) {
            throw new IllegalStateException(I18nUtils.get("channel.balance_unavailable"));
        }

        LocalDateTime now = LocalDateTime.now();
        String startDate = now.withDayOfMonth(1).toLocalDate().toString();
        if (!hasPaymentMethod) {
            startDate = now.minusDays(100).toLocalDate().toString();
        }
        String endDate = now.toLocalDate().toString();
        JsonNode usage = requestBalanceJson(
                channel,
                baseUrl + "/v1/dashboard/billing/usage?start_date=" + startDate + "&end_date=" + endDate,
                authHeaders(channel, key)
        );
        BigDecimal totalUsage = decimalOrZero(usage.path("total_usage"));
        BigDecimal balance = hardLimitUsd.subtract(totalUsage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return persistBalance(channel, balance);
    }

    private double updateAiProxyBalance(Channel channel) {
        Map<String, String> headers = Map.of("Api-Key", firstKey(channel.getKey()));
        JsonNode root = requestBalanceJson(channel, "https://aiproxy.io/api/report/getUserOverview", headers);
        if (!root.path("success").asBoolean(false)) {
            throw new IllegalStateException("code: " + root.path("error_code").asInt() + ", message: "
                    + root.path("message").asText(""));
        }
        return persistBalance(channel, decimalOrZero(root.path("data").path("totalPoints")));
    }

    private double updateApi2GptBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://api.api2gpt.com/dashboard/billing/credit_grants",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        return persistBalance(channel, decimalOrZero(root.path("total_remaining")));
    }

    private double updateAigc2dBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://api.aigc2d.com/dashboard/billing/credit_grants",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        return persistBalance(channel, decimalOrZero(root.path("total_available")));
    }

    private double updateSiliconFlowBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://api.siliconflow.cn/v1/user/info",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        if (root.path("code").asInt() != 20000) {
            throw new IllegalStateException("code: " + root.path("code").asInt() + ", message: "
                    + root.path("message").asText(""));
        }
        return persistBalance(channel, decimalOrZero(root.path("data").path("totalBalance")));
    }

    private double updateDeepSeekBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://api.deepseek.com/user/balance",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        JsonNode balances = root.path("balance_infos");
        if (!balances.isArray()) {
            throw new IllegalStateException("currency CNY not found");
        }
        for (JsonNode item : balances) {
            if ("CNY".equalsIgnoreCase(item.path("currency").asText(""))) {
                return persistBalance(channel, decimalOrZero(item.path("total_balance")));
            }
        }
        throw new IllegalStateException("currency CNY not found");
    }

    private double updateOpenRouterBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://openrouter.ai/api/v1/credits",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        BigDecimal totalCredits = decimalOrZero(root.path("data").path("total_credits"));
        BigDecimal totalUsage = decimalOrZero(root.path("data").path("total_usage"));
        return persistBalance(channel, totalCredits.subtract(totalUsage));
    }

    private double updateMoonshotBalance(Channel channel) {
        JsonNode root = requestBalanceJson(
                channel,
                "https://api.moonshot.cn/v1/users/me/balance",
                authHeaders(channel, firstKey(channel.getKey()))
        );
        if (!root.path("status").asBoolean(false) || root.path("code").asInt() != 0) {
            throw new IllegalStateException("failed to update moonshot balance, status: "
                    + root.path("status").asBoolean(false) + ", code: " + root.path("code").asInt()
                    + ", scode: " + root.path("scode").asText(""));
        }
        BigDecimal availableBalanceCny = decimalOrZero(root.path("data").path("available_balance"));
        BigDecimal usdPrice = BigDecimal.valueOf(getDoubleOption("Price", 7.3D));
        if (usdPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(I18nUtils.get("channel.price_config_invalid"));
        }
        return persistBalance(channel, availableBalanceCny.divide(usdPrice, 6, RoundingMode.HALF_UP));
    }

    private List<String> fetchModelsFromChannel(Channel channel) throws IOException, InterruptedException {
        return fetchChannelUpstreamModelIds(channel);
    }

    private List<String> fetchModelsFromRequest(int type, String baseUrl, String key)
            throws IOException, InterruptedException {
        if (type == ChannelConstants.CHANNEL_TYPE_OLLAMA) {
            return fetchOllamaModels(baseUrl, key);
        }
        if (type == ChannelConstants.CHANNEL_TYPE_GEMINI) {
            return fetchGeminiModels(baseUrl, key);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models"))
                .GET()
                .timeout(Duration.ofSeconds(30));
        if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }
        HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException(I18nUtils.get("channel.get_model_list_failed",
                    response.statusCode() + ": " + new String(response.body())));
        }
        return extractOpenAIModelIds(objectMapper.readTree(response.body()));
    }

    private List<String> fetchGeminiModels(String baseUrl, String apiKey) throws IOException, InterruptedException {
        List<String> allModels = new ArrayList<>();
        String nextPageToken = "";
        for (int page = 0; page < 100; page++) {
            String url = baseUrl + "/v1beta/models" + (nextPageToken.isBlank() ? "" : "?pageToken=" + nextPageToken);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("x-goog-api-key", apiKey);
            }
            HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException(I18nUtils.get("channel.get_gemini_models_failed",
                    response.statusCode() + ": " + new String(response.body())));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.path("models");
            if (models.isArray()) {
                for (JsonNode model : models) {
                    String name = trimToNull(model.path("name").asText(null));
                    if (name != null) {
                        allModels.add(name.replaceFirst("^models/", ""));
                    }
                }
            }
            nextPageToken = root.path("nextPageToken").asText("");
            if (nextPageToken.isBlank()) {
                break;
            }
        }
        return allModels;
    }

    private List<String> fetchOllamaModels(String baseUrl, String apiKey) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(30));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException(I18nUtils.get("channel.get_ollama_models_failed",
                    response.statusCode() + ": " + new String(response.body())));
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode models = root.path("models");
        List<String> result = new ArrayList<>();
        if (models.isArray()) {
            for (JsonNode model : models) {
                String name = trimToNull(model.path("name").asText(null));
                if (name != null) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private void applyChannelFetchHeaders(HttpRequest.Builder builder, Channel channel, String key) {
        if (channel.getType() != null && channel.getType() == ChannelConstants.CHANNEL_TYPE_ANTHROPIC) {
            if (key != null && !key.isBlank()) {
                builder.header("x-api-key", key);
            }
            builder.header("anthropic-version", "2023-06-01");
        } else if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }

        String headerOverride = channel.getHeaderOverride();
        if (headerOverride == null || headerOverride.isBlank()) {
            return;
        }
        try {
            Map<String, Object> headers = Convert.toJSONObject(headerOverride);
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (!(entry.getValue() instanceof String value)) {
                    continue;
                }
                String headerName = trimToNull(entry.getKey());
                if (headerName == null || headerName.contains("*")) {
                    continue;
                }
                builder.header(headerName, value.replace("{api_key}", key == null ? "" : key));
            }
        } catch (Exception e) {
            log.warn("Failed to parse header override for channel#{}: {}", channel.getId(), e.getMessage());
        }
    }

    private List<String> extractOpenAIModelIds(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<String> models = new ArrayList<>();
        for (JsonNode item : data) {
            String id = trimToNull(item.path("id").asText(null));
            if (id != null) {
                models.add(id);
            }
        }
        return models;
    }

    /**
     * 更新渠道并在同一事务内按需重建 abilities（原子操作）。
     * <p>
     * channels 表更新与 abilities 表重建必须在同一事务中原子完成，
     * 避免出现 channels.models 已更新但 abilities 表无对应记录的数据不一致。
     *
     * @param channel 已填充更新字段的渠道对象
     * @param abilitiesChanged models/group/status 是否变更（true 时重建 abilities）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateChannelWithAbilities(Channel channel, boolean abilitiesChanged) {
        channelMapper.updateById(channel);
        if (abilitiesChanged) {
            recreateChannelAbilities(channel);
        }
        refreshChannelCache();
    }

    /**
     * 重建指定渠道的 abilities（public 入口，供 ChannelController.update 调用）。
     * <p>
     * 删除该渠道的所有旧 abilities 记录，按当前 models/group/status 重建。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recreateChannelAbilitiesPublic(Channel channel) {
        recreateChannelAbilities(channel);
        refreshChannelCache();
    }

    private void recreateChannelAbilities(Channel channel) {
        abilityMapper.delete(new LambdaQueryWrapper<Ability>()
                .eq(Ability::getChannelId, channel.getId()));

        List<String> models = splitCommaSeparated(channel.getModels());
        if (models.isEmpty()) {
            return;
        }
        List<String> groups = splitCommaSeparated(channel.getGroup());
        if (groups.isEmpty()) {
            groups = List.of("default");
        }

        Set<String> uniquePairs = new LinkedHashSet<>();
        for (String model : models) {
            for (String group : groups) {
                String pair = group + "|" + model;
                if (!uniquePairs.add(pair)) {
                    continue;
                }
                Ability ability = new Ability();
                ability.setGroup(group);
                ability.setModel(model);
                ability.setChannelId(channel.getId());
                ability.setEnabled(Objects.equals(channel.getStatus(), CommonConstants.CHANNEL_STATUS_ENABLED));
                ability.setPriority(channel.getPriority());
                ability.setWeight(channel.getWeight());
                ability.setTag(channel.getTag());
                abilityMapper.insert(ability);
            }
        }
        // abilities 变更后失效 pricing 缓存，避免 60s 窗口期内模型广场/模型列表数据陈旧
        pricingService.invalidatePricingCache();
    }

    private List<String> fetchChannelUpstreamModelIds(Channel channel) throws IOException, InterruptedException {
        int type = channel.getType() == null ? 0 : channel.getType();
        String baseUrl = normalizeBaseUrl(type, channel.getBaseUrl());
        String key = resolveFirstAvailableKey(channel);
        if (type == ChannelConstants.CHANNEL_TYPE_OLLAMA) {
            return normalizeModelNames(fetchOllamaModels(baseUrl, key));
        }
        if (type == ChannelConstants.CHANNEL_TYPE_GEMINI) {
            return normalizeModelNames(fetchGeminiModels(baseUrl, key));
        }
        String url = resolveUpstreamModelFetchUrl(channel, baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30));
        applyChannelFetchHeaders(builder, channel, key);
        HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + new String(response.body()));
        }
        return normalizeModelNames(extractOpenAIModelIds(objectMapper.readTree(response.body())));
    }

    private String resolveUpstreamModelFetchUrl(Channel channel, String baseUrl) {
        ChannelConstants.ChannelSpecialBase specialBase = ChannelConstants.CHANNEL_SPECIAL_BASES.get(baseUrl);
        int type = channel.getType() == null ? ChannelConstants.CHANNEL_TYPE_UNKNOWN : channel.getType();
        return switch (type) {
            case ChannelConstants.CHANNEL_TYPE_ALI -> baseUrl + "/compatible-mode/v1/models";
            case ChannelConstants.CHANNEL_TYPE_ZHIPU_V4 -> specialBase != null && trimToNull(specialBase.openAIBaseURL()) != null
                    ? specialBase.openAIBaseURL() + "/models"
                    : baseUrl + "/api/paas/v4/models";
            case ChannelConstants.CHANNEL_TYPE_VOLC_ENGINE -> specialBase != null && trimToNull(specialBase.openAIBaseURL()) != null
                    ? specialBase.openAIBaseURL() + "/v1/models"
                    : baseUrl + "/v1/models";
            case ChannelConstants.CHANNEL_TYPE_MOONSHOT -> specialBase != null && trimToNull(specialBase.openAIBaseURL()) != null
                    ? specialBase.openAIBaseURL() + "/models"
                    : baseUrl + "/v1/models";
            default -> baseUrl + "/v1/models";
        };
    }

    private void persistChannelInfo(Channel channel, ChannelInfoDTO info, String newKey) {
        Channel update = new Channel();
        update.setId(channel.getId());
        update.setChannelInfo(serializeChannelInfo(info));
        if (newKey != null) {
            update.setKey(newKey);
            channel.setKey(newKey);
        }
        channel.setChannelInfo(update.getChannelInfo());
        channelMapper.updateById(update);
    }

    private ChannelInfoDTO parseChannelInfo(Channel channel) {
        String raw = channel.getChannelInfo();
        ChannelInfoDTO info;
        try {
            info = (raw == null || raw.isBlank()) ? new ChannelInfoDTO()
                    : Convert.toJavaBean(raw, ChannelInfoDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException(I18nUtils.get("channel.channel_info_parse_failed"));
        }
        if (info.getMultiKeyStatusList() == null) {
            info.setMultiKeyStatusList(new LinkedHashMap<>());
        }
        if (info.getMultiKeyDisabledTime() == null) {
            info.setMultiKeyDisabledTime(new LinkedHashMap<>());
        }
        if (info.getMultiKeyDisabledReason() == null) {
            info.setMultiKeyDisabledReason(new LinkedHashMap<>());
        }
        return info;
    }

    private ChannelOtherSettingsDTO parseChannelOtherSettings(Channel channel) {
        String raw = trimToNull(channel.getOtherSettings());
        if (raw == null) {
            return new ChannelOtherSettingsDTO();
        }
        try {
            ChannelOtherSettingsDTO settings = Convert.toJavaBean(raw, ChannelOtherSettingsDTO.class);
            return settings == null ? new ChannelOtherSettingsDTO() : settings;
        } catch (Exception e) {
            throw new IllegalStateException(I18nUtils.get("channel.settings_parse_failed"));
        }
    }

    private String serializeChannelOtherSettings(ChannelOtherSettingsDTO settings) {
        try {
            return Convert.toJSONString(settings == null ? new ChannelOtherSettingsDTO() : settings);
        } catch (Exception e) {
            throw new IllegalStateException(I18nUtils.get("channel.settings_serialize_failed"));
        }
    }

    private void persistChannelOtherSettings(Channel channel, ChannelOtherSettingsDTO settings, boolean updateModels) {
        Channel update = new Channel();
        update.setId(channel.getId());
        update.setOtherSettings(serializeChannelOtherSettings(settings));
        if (updateModels) {
            update.setModels(channel.getModels());
        }
        channel.setOtherSettings(update.getOtherSettings());
        channelMapper.updateById(update);
    }

    private String serializeChannelInfo(ChannelInfoDTO info) {
        try {
            return Convert.toJSONString(info);
        } catch (Exception e) {
            throw new IllegalStateException(I18nUtils.get("channel.channel_info_serialize_failed"));
        }
    }

    private void ensureMultiKeyMaps(ChannelInfoDTO info) {
        if (info.getMultiKeyStatusList() == null) {
            info.setMultiKeyStatusList(new LinkedHashMap<>());
        }
        if (info.getMultiKeyDisabledTime() == null) {
            info.setMultiKeyDisabledTime(new LinkedHashMap<>());
        }
        if (info.getMultiKeyDisabledReason() == null) {
            info.setMultiKeyDisabledReason(new LinkedHashMap<>());
        }
    }

    private int getKeyStatus(ChannelInfoDTO info, int keyIndex) {
        if (info.getMultiKeyStatusList() == null) {
            return CommonConstants.CHANNEL_STATUS_ENABLED;
        }
        return info.getMultiKeyStatusList().getOrDefault(keyIndex, CommonConstants.CHANNEL_STATUS_ENABLED);
    }

    private String resolveFirstAvailableKey(Channel channel) {
        List<String> keys = parseKeys(channel.getKey());
        if (keys.isEmpty()) {
            return "";
        }
        ChannelInfoDTO info = parseChannelInfo(channel);
        if (!info.isMultiKey()) {
            return keys.get(0);
        }
        for (int i = 0; i < keys.size(); i++) {
            if (getKeyStatus(info, i) == CommonConstants.CHANNEL_STATUS_ENABLED) {
                return keys.get(i);
            }
        }
        return keys.get(0);
    }

    private void validateKeyIndex(Integer keyIndex, int size) {
        if (keyIndex == null) {
            throw new IllegalArgumentException(I18nUtils.get("channel.key_index_not_specified"));
        }
        if (keyIndex < 0 || keyIndex >= size) {
            throw new IllegalArgumentException(I18nUtils.get("channel.key_index_out_of_range"));
        }
    }

    private Channel requireChannel(int id) {
        Channel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new IllegalArgumentException(I18nUtils.get("channel.not_exists"));
        }
        return channel;
    }

    private Channel requireCodexChannel(int id) {
        Channel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new IllegalArgumentException("channel not found");
        }
        if (!Objects.equals(channel.getType(), ChannelConstants.CHANNEL_TYPE_CODEX)) {
            throw new IllegalArgumentException("channel type is not Codex");
        }
        return channel;
    }

    private void refreshChannelCache() {
        ChannelCacheService.initChannelCache(channelMapper, abilityMapper);
    }

    private JsonNode requestBalanceJson(Channel channel, String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30));
        headers.forEach((headerName, headerValue) -> {
            if (headerValue != null && !headerValue.isBlank()) {
                builder.header(headerName, headerValue);
            }
        });
        applyProxyHeaderOverrides(builder, channel, headers.getOrDefault("Authorization", ""));
        try {
            HttpClient client = resolveBalanceHttpClient(channel);
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("status code: " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18nUtils.get("channel.balance_query_interrupted"));
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * 解析渠道代理配置并返回对应 HttpClient      * <p>
     * 仅支持 http/https 代理 URL；无代理或解析失败时回退到默认 HTTP_CLIENT。
     */
    private HttpClient resolveBalanceHttpClient(Channel channel) {
        String proxyURL = parseProxyFromSetting(channel.getSetting());
        if (proxyURL == null) {
            return HTTP_CLIENT;
        }
        return PROXY_CLIENTS.computeIfAbsent(proxyURL, key -> {
            PROXY_CLIENT_LOCK.lock();
            try {
                URI proxyUri = URI.create(key);
                String scheme = proxyUri.getScheme();
                if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                    log.warn("不支持的代理协议, channelId={}, proxy={}, 回退为直连", channel.getId(), key);
                    return HTTP_CLIENT;
                }
                HttpClient proxyClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .proxy(ProxySelector.of(InetSocketAddress.createUnresolved(
                                proxyUri.getHost(),
                                proxyUri.getPort() == -1 ? 80 : proxyUri.getPort())))
                        .build();
                log.info("已创建代理客户端, channelId={}, proxy={}", channel.getId(), key);
                return proxyClient;
            } catch (Exception e) {
                log.warn("创建代理客户端失败, channelId={}, proxy={}, 回退为直连: {}",
                        channel.getId(), key, e.getMessage());
                return HTTP_CLIENT;
            } finally {
                PROXY_CLIENT_LOCK.unlock();
            }
        });
    }

    /** 清空 proxyClients 缓存，由 ProxyClientCacheService 委托调用 */
    public void resetProxyClientCache() {
        PROXY_CLIENTS.clear();
    }

    private String validateJsonOverride(String value, String errorMessage) {
        if (value == null) {
            return null;
        }
        String trimmed = trimToEmpty(value);
        if (!trimmed.isEmpty()) {
            try {
                objectMapper.readTree(trimmed);
            } catch (Exception e) {
                throw new IllegalArgumentException(errorMessage);
            }
        }
        return trimmed;
    }

    private List<String> parseKeys(String rawKey) {
        String normalized = trimToNull(rawKey);
        if (normalized == null) {
            return List.of();
        }
        if (normalized.startsWith("[")) {
            try {
                String[] array = Convert.toJavaBean(normalized, String[].class);
                return Arrays.asList(array);
            } catch (Exception ignored) {
                // 回退到换行分割。
            }
        }
        return Arrays.stream(normalized.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> splitCommaSeparated(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String normalizeBaseUrl(int type, String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized != null) {
            // 去除尾部斜杠，避免后续拼接成重复分隔符
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            // 去除尾部 /v1 后缀，避免与后续拼接的 /v1/... 重复成 /v1/v1/...
            if (normalized.endsWith("/v1")) {
                normalized = normalized.substring(0, normalized.length() - 3);
            }
            return normalized;
        }
        if (type < 0 || type >= ChannelConstants.CHANNEL_BASE_URLS.size()) {
            return "";
        }
        return ChannelConstants.CHANNEL_BASE_URLS.get(type);
    }

    private String firstKey(String rawKey) {
        List<String> keys = parseKeys(rawKey);
        return keys.isEmpty() ? "" : keys.get(0);
    }

    private String previewKey(String key) {
        if (key == null) {
            return "";
        }
        return key.length() > 10 ? key.substring(0, 10) + "..." : key;
    }

    private Map<String, Object> messageOnly(String message) {
        return Map.of("message", message);
    }

    private Long currentUnixSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private String codexOAuthSessionKey(int channelId, String field) {
        return "codex_oauth_" + field + "_" + channelId;
    }

    private void requireSession(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("oauth flow not started or session expired");
        }
    }

    private String sessionString(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value instanceof String stringValue ? trimToNull(stringValue) : null;
    }

    private void clearCodexOAuthSession(HttpSession session, int channelId) {
        session.removeAttribute(codexOAuthSessionKey(channelId, "state"));
        session.removeAttribute(codexOAuthSessionKey(channelId, "verifier"));
        session.removeAttribute(codexOAuthSessionKey(channelId, "created_at"));
    }

    private String parseProxyFromSetting(String channelSetting) {
        if (channelSetting == null || channelSetting.isBlank()) {
            return null;
        }
        try {
            ChannelSettingsDTO setting = Convert.toJavaBean(channelSetting, ChannelSettingsDTO.class);
            return trimToNull(setting.getProxy());
        } catch (Exception e) {
            log.warn("Failed to parse channel setting proxy: {}", e.getMessage());
            return null;
        }
    }

    private void applyProxyHeaderOverrides(HttpRequest.Builder builder, Channel channel, String authHeaderValue) {
        String headerOverride = channel.getHeaderOverride();
        if (headerOverride == null || headerOverride.isBlank()) {
            return;
        }
        try {
            Map<String, Object> headers = Convert.toJSONObject(headerOverride);
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (!(entry.getValue() instanceof String value)) {
                    continue;
                }
                String headerName = trimToNull(entry.getKey());
                if (headerName == null || headerName.contains("*")) {
                    continue;
                }
                builder.header(headerName, value.replace("{api_key}", authHeaderValue.replaceFirst("^Bearer\\s+", "")));
            }
        } catch (Exception e) {
            log.warn("Failed to parse header override for channel#{} in balance update: {}",
                    channel.getId(), e.getMessage());
        }
    }

    private Map<String, String> authHeaders(Channel channel, String key) {
        if (channel.getType() != null && channel.getType() == ChannelConstants.CHANNEL_TYPE_ANTHROPIC) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("x-api-key", key);
            headers.put("anthropic-version", "2023-06-01");
            return headers;
        }
        return Map.of("Authorization", "Bearer " + key);
    }

    private String resolveBaseUrl(Channel channel) {
        return normalizeBaseUrl(channel.getType() == null ? 0 : channel.getType(), channel.getBaseUrl());
    }

    private double persistBalance(Channel channel, BigDecimal balance) {
        double balanceValue = balance.doubleValue();
        Channel update = new Channel();
        update.setId(channel.getId());
        update.setBalance(balanceValue);
        update.setBalanceUpdatedTime(currentUnixSeconds());
        channelMapper.updateById(update);
        channel.setBalance(balanceValue);
        channel.setBalanceUpdatedTime(update.getBalanceUpdatedTime());
        return balanceValue;
    }

    private boolean isAutoBanEnabled(Channel channel) {
        return channel.getAutoBan() != null && channel.getAutoBan() != 0;
    }

    private BigDecimal decimalOrZero(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String raw = trimToNull(node.asText());
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private double getDoubleOption(String key, double defaultValue) {
        try {
            String value = trimToNull(optionService.getValue(key));
            return value == null ? defaultValue : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLongOption(String key, long defaultValue) {
        try {
            String value = trimToNull(optionService.getValue(key));
            return value == null ? defaultValue : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String toEncodedCodexKey(CodexOAuthTokenResult tokenResult,
                                     String accountId,
                                     String email,
                                     String lastRefresh,
                                     String expiresAt) {
        CodexCredentialRefreshService.CodexOAuthKey oauthKey = new CodexCredentialRefreshService.CodexOAuthKey();
        oauthKey.setAccessToken(tokenResult.getAccessToken());
        oauthKey.setRefreshToken(tokenResult.getRefreshToken());
        oauthKey.setAccountId(accountId);
        oauthKey.setEmail(email);
        oauthKey.setLastRefresh(lastRefresh);
        oauthKey.setExpired(expiresAt);
        oauthKey.setType("codex");
        return CodexCredentialRefreshService.toEncodedJson(oauthKey);
    }

    private Map<String, Object> payload(String message, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("data", data);
        return payload;
    }

    private Object parseJsonBodyOrRawString(String body) {
        String normalized = trimToNull(body);
        if (normalized == null) {
            return "";
        }
        try {
            return Convert.toJavaBean(normalized, Object.class);
        } catch (Exception e) {
            return normalized;
        }
    }

    private Map<String, String> extractQueryParameters(String input) {
        List<String> candidates = new ArrayList<>();
        candidates.add(input);
        try {
            URI uri = URI.create(input);
            if (uri.getRawQuery() != null) {
                candidates.add(uri.getRawQuery());
            }
        } catch (IllegalArgumentException ignored) {
            // 允许继续回退到 query string 解析。
        }

        for (String candidate : candidates) {
            Map<String, String> result = parseQueryString(candidate);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return Map.of();
    }

    private Map<String, String> parseQueryString(String rawQuery) {
        String query = trimToNull(rawQuery);
        if (query == null) {
            return Map.of();
        }
        int questionMarkIndex = query.indexOf('?');
        if (questionMarkIndex >= 0) {
            query = query.substring(questionMarkIndex + 1);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String normalizedPair = trimToNull(pair);
            if (normalizedPair == null) {
                continue;
            }
            String[] keyValue = normalizedPair.split("=", 2);
            String key = decodeQueryParameter(keyValue[0]);
            if (key == null) {
                continue;
            }
            String value = keyValue.length > 1 ? decodeQueryParameter(keyValue[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private String decodeQueryParameter(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return trimToNull(URLDecoder.decode(normalized, StandardCharsets.UTF_8));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> objectStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            String normalized = trimToNull(stringValue(item));
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return normalizeModelNames(result);
    }

    private List<Channel> listEnabledChannels() {
        return channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getStatus, CommonConstants.CHANNEL_STATUS_ENABLED)
                .orderByAsc(Channel::getId));
    }


    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer toInteger(Object value) {
        return toInteger(value, null);
    }

    private Integer toInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class InMemoryHttpServletResponse {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final ServletOutputStream outputStream = new InMemoryServletOutputStream(body);
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private String characterEncoding = StandardCharsets.UTF_8.name();
        private String contentType;
        private int status = HttpServletResponse.SC_OK;
        private int bufferSize = 0;
        private boolean committed = false;
        private Locale locale = Locale.getDefault();
        private PrintWriter writer;

        private HttpServletResponse asResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        switch (methodName) {
                            case "getOutputStream":
                                return outputStream;
                            case "getWriter":
                                if (writer == null) {
                                    writer = new PrintWriter(new OutputStreamWriter(body, StandardCharsets.UTF_8), true);
                                }
                                return writer;
                            case "setContentType":
                                setContentType(args == null ? null : (String) args[0]);
                                return null;
                            case "getContentType":
                                return contentType;
                            case "setCharacterEncoding":
                                characterEncoding = args == null ? StandardCharsets.UTF_8.name() : (String) args[0];
                                return null;
                            case "getCharacterEncoding":
                                return characterEncoding;
                            case "setHeader":
                                replaceHeader((String) args[0], stringifyHeaderValue(args[1]));
                                return null;
                            case "addHeader":
                                appendHeader((String) args[0], stringifyHeaderValue(args[1]));
                                return null;
                            case "setDateHeader":
                            case "setIntHeader":
                                replaceHeader((String) args[0], stringifyHeaderValue(args[1]));
                                return null;
                            case "addDateHeader":
                            case "addIntHeader":
                                appendHeader((String) args[0], stringifyHeaderValue(args[1]));
                                return null;
                            case "getHeader":
                                return getHeader((String) args[0]);
                            case "getHeaders":
                                return headers.getOrDefault((String) args[0], List.of());
                            case "getHeaderNames":
                                return headers.keySet();
                            case "containsHeader":
                                return headers.containsKey((String) args[0]);
                            case "setStatus":
                                status = args == null ? HttpServletResponse.SC_OK : (Integer) args[0];
                                return null;
                            case "getStatus":
                                return status;
                            case "setContentLength":
                            case "setContentLengthLong":
                                return null;
                            case "setBufferSize":
                                bufferSize = args == null ? 0 : (Integer) args[0];
                                return null;
                            case "getBufferSize":
                                return bufferSize;
                            case "flushBuffer":
                                flush();
                                committed = true;
                                return null;
                            case "resetBuffer":
                                resetBuffer();
                                return null;
                            case "reset":
                                reset();
                                return null;
                            case "isCommitted":
                                return committed;
                            case "setLocale":
                                locale = args == null ? Locale.getDefault() : (Locale) args[0];
                                return null;
                            case "getLocale":
                                return locale;
                            case "sendError":
                                status = args == null ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : (Integer) args[0];
                                committed = true;
                                if (args != null && args.length > 1 && args[1] instanceof String message && !message.isBlank()) {
                                    writeBytes(message.getBytes(StandardCharsets.UTF_8));
                                }
                                return null;
                            case "sendRedirect":
                                status = HttpServletResponse.SC_FOUND;
                                replaceHeader("Location", args == null ? null : (String) args[0]);
                                committed = true;
                                return null;
                            case "toString":
                                return "InMemoryHttpServletResponse";
                            case "hashCode":
                                return System.identityHashCode(this);
                            case "equals":
                                return proxy == (args == null ? null : args[0]);
                            default:
                                Class<?> returnType = method.getReturnType();
                                if (returnType == boolean.class) {
                                    return false;
                                }
                                if (returnType == int.class) {
                                    return 0;
                                }
                                if (returnType == long.class) {
                                    return 0L;
                                }
                                return null;
                        }
                    }
            );
        }

        private byte[] body() {
            flush();
            return body.toByteArray();
        }

        private void setContentType(String value) {
            contentType = value;
            replaceHeader("Content-Type", value);
        }

        private String getHeader(String name) {
            List<String> values = headers.get(name);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(0);
        }

        private void replaceHeader(String name, String value) {
            if (name == null) {
                return;
            }
            if (value == null) {
                headers.remove(name);
                return;
            }
            headers.put(name, new ArrayList<>(List.of(value)));
        }

        private void appendHeader(String name, String value) {
            if (name == null || value == null) {
                return;
            }
            headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }

        private void writeBytes(byte[] bytes) {
            try {
                body.write(bytes);
            } catch (IOException e) {
                throw new IllegalStateException(I18nUtils.get("channel.memory_write_failed"), e);
            }
        }

        private void flush() {
            if (writer != null) {
                writer.flush();
            }
            try {
                outputStream.flush();
            } catch (IOException e) {
                throw new IllegalStateException(I18nUtils.get("channel.memory_flush_failed"), e);
            }
        }

        private void resetBuffer() {
            body.reset();
        }

        private void reset() {
            resetBuffer();
            headers.clear();
            contentType = null;
            status = HttpServletResponse.SC_OK;
            committed = false;
        }

        private String stringifyHeaderValue(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }

    private static final class InMemoryServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        private InMemoryServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // 内存输出流不支持异步写监听。
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }
    }

    private record CodexAuthorizationInput(String code, String state) {
    }

    private record ChannelTestPlan(String modelName,
                                   String endpointType,
                                   String requestPath,
                                   String relayFormat,
                                   int relayMode,
                                   int apiType) {
    }

    private record ChannelTestExecutionResult(boolean success,
                                              Throwable localError,
                                              RelayException newApiError,
                                              long elapsedMilliseconds,
                                              RelayInfo relayInfo,
                                              Usage usage) {
        private static ChannelTestExecutionResult success(long elapsedMilliseconds, RelayInfo relayInfo, Usage usage) {
            return new ChannelTestExecutionResult(true, null, null, elapsedMilliseconds, relayInfo, usage);
        }

        private static ChannelTestExecutionResult failure(Throwable localError,
                                                          RelayException newApiError,
                                                          long elapsedMilliseconds) {
            return new ChannelTestExecutionResult(false, localError, newApiError, elapsedMilliseconds, null, null);
        }
    }

    /**
     * 批量渠道测试的单个渠道结果条目，聚合测试结果与状态变更追踪。
     * <p>
     * responseTime 单位为毫秒；statusChanged 表示测试后渠道是否触发自动禁用/恢复。
     */
    private record ChannelBatchTestItem(int channelId,
                                        String channelName,
                                        String testModel,
                                        boolean success,
                                        long responseTime,
                                        boolean statusChanged,
                                        String error) {
    }

    private record PendingUpstreamModelChanges(List<String> pendingAddModels, List<String> pendingRemoveModels) {
    }

    /**
     * 中继路径错误触发的渠道禁用      * <p>
     * 仅在 shouldDisableChannel 判定为 true 且 AutoBan 开启时执行禁用。
     * 与批量自动测试的禁用逻辑（executeChannelTests）复用相同的 updateChannelStatus + notify 内部方法。
     *
     * @param channel 失败渠道（需含完整 Channel 信息，含 autoBan 字段）
     * @param error   触发禁用的上游错误
     */
    public void disableChannelForRelayError(Channel channel, RelayException error) {
        if (channel == null || error == null) return;
        if (!shouldDisableChannel(error) || !isAutoBanEnabled(channel)) return;

        log.warn("中继路径自动禁用渠道：channel={}(#{})，原因：{}",
                channel.getName(), channel.getId(), error.errorWithStatusCode());
        if (updateChannelStatus(channel, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED)) {
            notifyChannelStatusChanged(channel, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED, error.errorWithStatusCode());
        }
    }

    private record ChannelUpstreamDetectResult(Integer channelId,
                                               String channelName,
                                               List<String> addModels,
                                               List<String> removeModels,
                                               Long lastCheckTime,
                                               int autoAddedModels,
                                               boolean modelsChanged) {
    }

    private record ChannelUpstreamApplyResult(List<String> addedModels,
                                              List<String> removedModels,
                                              List<String> ignoredModels,
                                              List<String> remainingModels,
                                              List<String> remainingRemoveModels,
                                              boolean modelsChanged) {
    }
}
