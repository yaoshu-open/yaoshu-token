package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.config.SystemSettingConfig;
import yaoshu.token.mapper.OptionMapper;
import yaoshu.token.pojo.entity.Option;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static yaoshu.token.service.ChannelAffinityService.ChannelAffinitySetting;
import static yaoshu.token.service.ChannelAffinityService.ChannelAffinityRule;
import static yaoshu.token.service.ChannelAffinityService.ChannelAffinityKeySource;

/**
 * 系统配置服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptionService {

    private final OptionMapper optionMapper;

    /** 内存缓存 */
    private final Map<String, String> optionCache = new ConcurrentHashMap<>();

    /**
     * 启动时初始化 OptionMap      * <p>
     * 1. 设置内存默认值（与 Go InitOptionMap 一致）
     * 2. 从数据库加载已有配置覆盖默认值
     */
    @PostConstruct
    public void initOptionMap() {
        yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().lock();
        try {
            Map<String, String> map = new java.util.LinkedHashMap<>();

            // 权限配置
            map.put("FileUploadPermission", "0");
            map.put("FileDownloadPermission", "0");
            map.put("ImageUploadPermission", "0");
            map.put("ImageDownloadPermission", "0");
            map.put("PasswordLoginEnabled", "true");
            map.put("PasswordRegisterEnabled", "true");
            map.put("EmailVerificationEnabled", "false");
            map.put("GitHubOAuthEnabled", "false");
            map.put("LinuxDOOAuthEnabled", "false");
            map.put("TelegramOAuthEnabled", "false");
            map.put("WeChatAuthEnabled", "false");
            map.put("TurnstileCheckEnabled", "false");
            map.put("RegisterEnabled", "true");
            map.put("AutomaticDisableChannelEnabled", "false");
            map.put("AutomaticEnableChannelEnabled", "false");
            map.put("LogConsumeEnabled", "true");
            map.put("DisplayInCurrencyEnabled", "true");
            map.put("DisplayTokenStatEnabled", "true");
            map.put("DrawingEnabled", "true");
            map.put("TaskEnabled", "true");
            map.put("DataExportEnabled", "false");
            map.put("ChannelDisableThreshold", "0.9");
            map.put("EmailDomainRestrictionEnabled", "false");
            map.put("EmailAliasRestrictionEnabled", "false");
            map.put("EmailDomainWhitelist", "");

            // 邮件
            map.put("SMTPServer", "");
            map.put("SMTPFrom", "");
            map.put("SMTPPort", "587");
            map.put("SMTPAccount", "");
            map.put("SMTPToken", "");
            map.put("SMTPSSLEnabled", "false");
            map.put("SMTPForceAuthLogin", "false");

            // 内容与展示
            map.put("Notice", "");
            map.put("About", "");
            map.put("HomePageContent", "");
            map.put("Footer", "");
            map.put("SystemName", "YAOSHU TOKEN");
            map.put("Logo", "");
            map.put("ServerAddress", "");
            map.put("WorkerUrl", "");
            map.put("WorkerValidKey", "");
            map.put("WorkerAllowHttpImageRequestEnabled", "false");

            // 支付
            map.put("PayAddress", "");
            map.put("CustomCallbackAddress", "");
            map.put("EpayId", "");
            map.put("EpayKey", "");
            map.put("Price", "0.1");
            map.put("USDExchangeRate", "7.0");
            map.put("MinTopUp", "1");
            map.put("StripeMinTopUp", "1");
            map.put("StripeApiSecret", "");
            map.put("StripeWebhookSecret", "");
            map.put("StripePriceId", "");
            map.put("StripeUnitPrice", "0.1");
            map.put("StripePromotionCodesEnabled", "false");
            map.put("CreemApiKey", "");
            map.put("CreemProducts", "");
            map.put("CreemTestMode", "false");
            map.put("CreemWebhookSecret", "");
            map.put("WaffoEnabled", "false");
            map.put("WaffoApiKey", "");
            map.put("WaffoPrivateKey", "");
            map.put("WaffoPublicCert", "");
            map.put("WaffoSandboxPublicCert", "");
            map.put("WaffoSandboxApiKey", "");
            map.put("WaffoSandboxPrivateKey", "");
            map.put("WaffoSandbox", "false");
            map.put("WaffoMerchantId", "");
            map.put("WaffoNotifyUrl", "");
            map.put("WaffoReturnUrl", "");
            map.put("WaffoSubscriptionReturnUrl", "");
            map.put("WaffoCurrency", "USD");
            map.put("WaffoUnitPrice", "0.1");
            map.put("WaffoMinTopUp", "1");
            map.put("WaffoPayMethods", "[]");
            map.put("WaffoPancakeMerchantID", "");
            map.put("WaffoPancakePrivateKey", "");
            map.put("WaffoPancakeReturnURL", "");
            map.put("WaffoPancakeUnitPrice", "0.1");
            map.put("WaffoPancakeMinTopUp", "1");
            map.put("WaffoPancakeStoreID", "");
            map.put("WaffoPancakeProductID", "");

            // 分组与充值
            map.put("TopupGroupRatio", "[]");
            map.put("Chats", "[]");
            map.put("AutoGroups", "[]");
            map.put("DefaultUseAutoGroup", "false");
            map.put("PayMethods", "[]");

            // OAuth
            map.put("GitHubClientId", "");
            map.put("GitHubClientSecret", "");
            map.put("TelegramBotToken", "");
            map.put("TelegramBotName", "");
            map.put("WeChatServerAddress", "");
            map.put("WeChatServerToken", "");
            map.put("WeChatAccountQRCodeImageURL", "");
            map.put("TurnstileSiteKey", "");
            map.put("TurnstileSecretKey", "");

            // 配额
            map.put("QuotaForNewUser", "0");
            map.put("QuotaForInviter", "0");
            map.put("QuotaForInvitee", "0");
            map.put("QuotaRemindThreshold", "0");
            map.put("PreConsumedQuota", "0");
            map.put("QuotaPerUnit", "500000.0");

            // 模型与倍率
            map.put("ModelRequestRateLimitCount", "0");
            map.put("ModelRequestRateLimitDurationMinutes", "3");
            map.put("ModelRequestRateLimitSuccessCount", "0");
            map.put("ModelRequestRateLimitGroup", "{}");
            map.put("ModelRequestRateLimitEnabled", "false");
            map.put("ModelRatio", "{}");
            map.put("ModelPrice", "{}");
            map.put("CacheRatio", "{}");
            map.put("CreateCacheRatio", "{}");
            map.put("GroupRatio", "{}");
            map.put("GroupGroupRatio", "{}");
            map.put("UserUsableGroups", "{}");
            map.put("CompletionRatio", "{}");
            map.put("ImageRatio", "{}");
            map.put("AudioRatio", "{}");
            map.put("AudioCompletionRatio", "{}");

            map.put("TopUpLink", "");
            map.put("RetryTimes", "3");
            map.put("DataExportInterval", "60");
            map.put("DataExportDefaultTime", "24h");
            map.put("DefaultCollapseSidebar", "false");

            // Midjourney
            map.put("MjNotifyEnabled", "false");
            map.put("MjAccountFilterEnabled", "false");
            map.put("MjModeClearEnabled", "false");
            map.put("MjForwardUrlEnabled", "false");
            map.put("MjActionCheckSuccessEnabled", "false");

            // 敏感词检查
            map.put("CheckSensitiveEnabled", "false");
            map.put("CheckSensitiveOnPromptEnabled", "false");
            map.put("StopOnSensitiveEnabled", "false");
            map.put("SensitiveWords", "");

            // 其他
            map.put("DemoSiteEnabled", "false");
            map.put("SelfUseModeEnabled", "false");
            map.put("StreamCacheQueueLength", "100");
            map.put("AutomaticDisableKeywords", "");
            map.put("AutomaticDisableStatusCodes", "");
            map.put("AutomaticRetryStatusCodes", "");
            map.put("ExposeRatioEnabled", "false");

            // HeaderNavModules / SidebarModulesAdmin（前端导航模块开关）
            map.put("HeaderNavModules", "{}");
            map.put("SidebarModulesAdmin", "{}");

            yaoshu.token.constant.CommonConstants.optionMap = map;

            // 同步到 optionCache（供 getValue() 使用）
            optionCache.putAll(map);
        } finally {
            yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().unlock();
        }

        // 从数据库加载已有配置覆盖默认值
        loadOptionsFromDatabase();
    }

    /**
     * 从数据库加载配置覆盖内存中的默认值      * <p>
     * 加载完成后对所有 ratio key 调用一次 {@link #syncRatioMemoryMap(String, String)}，
     * 确保数据库已存量的 ratio JSON 在启动期立即生效（否则需等管理员重新写入才同步内存）。
     */
    private void loadOptionsFromDatabase() {
        try {
            List<Option> options = optionMapper.selectList(null);
            if (options != null) {
                yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().lock();
                try {
                    for (Option option : options) {
                        yaoshu.token.constant.CommonConstants.optionMap.put(option.getKey(), option.getValue());
                        optionCache.put(option.getKey(), option.getValue());
                    }
                } finally {
                    yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().unlock();
                }
                log.info("已从数据库加载 {} 条配置项到 OptionMap", options.size());

                // 启动期对所有 ratio key 同步一次内存 Map，覆盖 Config 类的 default Map
                for (Option option : options) {
                    if (isRatioSyncKey(option.getKey())) {
                        syncRatioMemoryMap(option.getKey(), option.getValue());
                    }
                }

                // 同步 PasskeySetting
                syncPasskeySetting();

                // 同步 CommonConstants（RetryTimes 等）
                syncCommonConstants();

                // 同步 SMTP 配置
                syncSmtpSettings();

                // 同步渠道亲和性配置
                syncChannelAffinitySetting();
            }
        } catch (Exception e) {
            log.warn("从数据库加载配置失败（可能数据表尚未初始化）: {}", e.getMessage());
        }
    }

    /** 是否是需要同步到 ratio 内存 Map 的 key，与 syncRatioMemoryMap switch case 对齐 */
    private boolean isRatioSyncKey(String key) {
        return "ModelRatio".equals(key) || "ModelPrice".equals(key) || "CompletionRatio".equals(key)
                || "ImageRatio".equals(key) || "AudioRatio".equals(key) || "AudioCompletionRatio".equals(key)
                || "CacheRatio".equals(key) || "CreateCacheRatio".equals(key)
                || "GroupRatio".equals(key) || "GroupGroupRatio".equals(key);
    }

    /**
     * 从 OptionMap 同步 PasskeySetting      */
    private void syncPasskeySetting() {
        try {
            String enabled = yaoshu.token.constant.CommonConstants.optionMap.get("PasskeyLoginEnabled");
            if (enabled != null) {
                SystemSettingConfig.PasskeySetting.current()
                        .setEnabled("true".equalsIgnoreCase(enabled));
            }
        } catch (Exception e) {
            log.warn("同步 PasskeySetting 失败: {}", e.getMessage());
        }
    }

    /**
     * 从 OptionMap 同步 CommonConstants 静态字段（RetryTimes 等）
     * <p>
     * Java 端 dispatchRelay 直接使用 {@code CommonConstants.retryTimes}，必须从 optionMap 同步。
     */
    private void syncCommonConstants() {
        try {
            Map<String, String> map = yaoshu.token.constant.CommonConstants.optionMap;
            String retryTimesStr = map.get("RetryTimes");
            if (retryTimesStr != null && !retryTimesStr.isEmpty()) {
                yaoshu.token.constant.CommonConstants.retryTimes = Integer.parseInt(retryTimesStr);
            }
            // 同步 SystemName（EmailService 发件人名称、TotpService 等多处使用）
            String systemName = map.get("SystemName");
            if (systemName != null && !systemName.isEmpty()) {
                yaoshu.token.constant.CommonConstants.systemName = systemName;
            }
            // 同步 MemoryCacheEnabled（ChannelCacheService 内存缓存开关，默认开启）
            String memoryCacheStr = map.get("MemoryCacheEnabled");
            yaoshu.token.constant.CommonConstants.memoryCacheEnabled =
                    memoryCacheStr == null || memoryCacheStr.isEmpty() || "true".equalsIgnoreCase(memoryCacheStr);
        } catch (Exception e) {
            log.warn("同步 CommonConstants 失败: {}", e.getMessage());
        }
    }

    /**
     * 从 OptionMap 同步 SMTP 配置到 CommonConstants 静态字段      * <p>
     * EmailService.sendEmail() 使用 CommonConstants 的 SMTP 静态字段，
     * 必须从 optionMap 同步以确保管理员配置即时生效。
     */
    private void syncSmtpSettings() {
        try {
            Map<String, String> map = yaoshu.token.constant.CommonConstants.optionMap;
            yaoshu.token.constant.CommonConstants.smtpServer = map.getOrDefault("SMTPServer", "");
            yaoshu.token.constant.CommonConstants.smtpPort = Integer.parseInt(map.getOrDefault("SMTPPort", "587"));
            yaoshu.token.constant.CommonConstants.smtpAccount = map.getOrDefault("SMTPAccount", "");
            yaoshu.token.constant.CommonConstants.smtpFrom = map.getOrDefault("SMTPFrom", "");
            yaoshu.token.constant.CommonConstants.smtpToken = map.getOrDefault("SMTPToken", "");
            yaoshu.token.constant.CommonConstants.smtpSslEnabled = Boolean.parseBoolean(map.getOrDefault("SMTPSSLEnabled", "false"));
            yaoshu.token.constant.CommonConstants.smtpForceAuthLogin = Boolean.parseBoolean(map.getOrDefault("SMTPForceAuthLogin", "false"));
        } catch (Exception e) {
            log.warn("同步 SMTP 配置失败: {}", e.getMessage());
        }
    }

    /**
     * 从 OptionMap 同步渠道亲和性配置到 ChannelAffinityService。
     * <p>
     * 如果 options 表中有 ChannelAffinitySetting JSON 配置则使用之；
     * 否则使用默认规则：按 token_id + 模型名 + 分组名 做亲和，TTL 3600秒。
     * <p>
     * 渠道亲和性确保同一用户的同一模型请求稳定命中同一渠道，
     * 避免随机命中不同上游导致间歇性错误。
     */
    private void syncChannelAffinitySetting() {
        try {
            Map<String, String> map = yaoshu.token.constant.CommonConstants.optionMap;
            String json = map.get("ChannelAffinitySetting");

            ChannelAffinityService.ChannelAffinitySetting setting;
            if (json != null && !json.isBlank()) {
                setting = ai.yue.library.base.convert.Convert.toJavaBean(json,
                        ChannelAffinityService.ChannelAffinitySetting.class);
            } else {
                // 默认规则：按 token_id 亲和，确保同一 Token 的请求稳定命中同一渠道
                setting = new ChannelAffinityService.ChannelAffinitySetting();
                setting.setEnabled(true);
                setting.setDefaultTTLSeconds(3600);
                setting.setKeepOnChannelDisabled(false);

                ChannelAffinityService.ChannelAffinityRule rule =
                        new ChannelAffinityService.ChannelAffinityRule();
                rule.setName("default_token_affinity");
                rule.setModelRegex(List.of(".*"));
                rule.setPathRegex(List.of("/v1/.*"));
                rule.setIncludeModelName(true);
                rule.setIncludeUsingGroup(true);
                rule.setTtlSeconds(3600);

                ChannelAffinityService.ChannelAffinityKeySource keySource =
                        new ChannelAffinityService.ChannelAffinityKeySource();
                keySource.setType("context_int");
                keySource.setKey("token_id");
                rule.setKeySources(List.of(keySource));

                setting.setRules(List.of(rule));
            }

            ChannelAffinityService.setGlobalSetting(setting);
            log.info("渠道亲和性配置已加载: enabled={}, rules={}",
                    setting.isEnabled(), setting.getRules() != null ? setting.getRules().size() : 0);
        } catch (Exception e) {
            log.warn("同步渠道亲和性配置失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有配置
     */
    public List<Option> getAll() {
        return optionMapper.selectList(null);
    }

    /**
     * 根据 key 获取配置值
     */
    public String getValue(String key) {
        // 先查缓存
        String cached = optionCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 查数据库
        Option option = optionMapper.selectById(key);
        if (option != null) {
            optionCache.put(key, option.getValue());
            return option.getValue();
        }
        return null;
    }

    /**
     * 更新配置（不存在则插入）。      * <p>
     * 本方法将校验下沉到 Service 层入口统一拦截，校验失败抛 RuntimeException 由全局异常处理器返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(String key, String value) {
        validateOptionValue(key, value);
        upsertOption(key, value);
    }

    /**
     * 写入配置（写库+缓存+内存同步），跳过业务校验。
     * <p>
     * 仅供已具备独立校验逻辑的专用端点调用（如支付合规确认 {@code confirmPaymentCompliance}），
     * 通用设置接口必须使用 {@link #saveOrUpdate(String, String)}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateRaw(String key, String value) {
        upsertOption(key, value);
    }

    /**
     * 实际写库+缓存+内存同步（内部方法）      */
    private void upsertOption(String key, String value) {
        Option option = optionMapper.selectById(key);
        if (option != null) {
            option.setValue(value);
            optionMapper.updateById(option);
        } else {
            option = new Option();
            option.setKey(key);
            option.setValue(value);
            optionMapper.insert(option);
        }
        optionCache.put(key, value);

        // 写库成功后同步刷新 CommonConstants.optionMap（SystemController.getStatus 等公开端点读此 Map）
        // 缺失此同步会导致管理员修改配置后 /api/status 返回旧值，直到重启才生效
        yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().lock();
        try {
            Map<String, String> optionMap = yaoshu.token.constant.CommonConstants.optionMap;
            if (optionMap != null) {
                optionMap.put(key, value);
            }
        } finally {
            yaoshu.token.constant.CommonConstants.optionMapLock.writeLock().unlock();
        }

        // 写库成功后同步刷新内存 Map
        // 覆盖 10 个 ratio key（详见 syncRatioMemoryMap）
        syncRatioMemoryMap(key, value);

        // 同步 PasskeySetting
        if ("PasskeyLoginEnabled".equals(key)) {
            syncPasskeySetting();
        }

        // 同步 CommonConstants（RetryTimes/SystemName/MemoryCacheEnabled 等）
        if ("RetryTimes".equals(key) || "SystemName".equals(key) || "MemoryCacheEnabled".equals(key)) {
            syncCommonConstants();
        }

        // 同步 SMTP 配置
        if (key != null && key.startsWith("SMTP")) {
            syncSmtpSettings();
        }

        // 同步渠道亲和性配置
        if ("ChannelAffinitySetting".equals(key)) {
            syncChannelAffinitySetting();
        }
    }

    /**
     * 写库成功后将 ratio JSON 同步到内存 Map。
     * <p>
     * 覆盖 10 个 ratio key：ModelRatio / ModelPrice / CompletionRatio / ImageRatio / AudioRatio /
     * AudioCompletionRatio / CacheRatio / CreateCacheRatio / GroupRatio / GroupGroupRatio。
     */
    private void syncRatioMemoryMap(String key, String value) {
        if (value == null || value.isBlank()) return;
        try {
            switch (key) {
                case "ModelRatio":
                    yaoshu.token.config.ratio.ModelRatioConfig.update(parseStringDoubleMap(value));
                    break;
                case "ModelPrice":
                    yaoshu.token.config.ratio.ModelPriceConfig.update(parseStringDoubleMap(value));
                    break;
                case "CompletionRatio":
                    yaoshu.token.config.ratio.CompletionRatioConfig.update(parseStringDoubleMap(value));
                    break;
                case "ImageRatio":
                    yaoshu.token.config.ratio.ImageRatioConfig.update(parseStringDoubleMap(value));
                    break;
                case "AudioRatio":
                    yaoshu.token.config.ratio.AudioRatioConfig.update(parseStringDoubleMap(value));
                    break;
                case "AudioCompletionRatio":
                    yaoshu.token.config.ratio.AudioCompletionRatioConfig.update(parseStringDoubleMap(value));
                    break;
                case "CacheRatio":
                    yaoshu.token.config.ratio.CacheRatioConfig.updateCacheRatio(parseStringDoubleMap(value));
                    break;
                case "CreateCacheRatio":
                    yaoshu.token.config.ratio.CacheRatioConfig.updateCreateCacheRatio(parseStringDoubleMap(value));
                    break;
                case "GroupRatio":
                    yaoshu.token.config.ratio.GroupRatioConfig.updateFromMaps(parseStringDoubleMap(value), null, null);
                    break;
                case "GroupGroupRatio":
                    yaoshu.token.config.ratio.GroupRatioConfig.updateFromMaps(null, parseStringMapStringDoubleMap(value), null);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // 校验阶段已通过 JSON 合法性检查，此处反序列化异常仅记日志，不影响写库结果
            log.warn("同步 ratio 内存 Map 失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 解析 {@code {"name": ratio}} 形态 JSON 为 Map<String, Double> */
    private Map<String, Double> parseStringDoubleMap(String jsonStr) {
        Map<String, Double> result = new java.util.HashMap<>();
        com.alibaba.fastjson2.JSONObject parsed = ai.yue.library.base.convert.Convert.toJSONObject(jsonStr);
        if (parsed == null) return result;
        parsed.forEach((name, raw) -> {
            if (raw instanceof Number num) {
                result.put(name, num.doubleValue());
            }
        });
        return result;
    }

    /** 解析 {@code {"userGroup": {"usingGroup": ratio}}} 形态 JSON 为 Map<String, Map<String, Double>> */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Double>> parseStringMapStringDoubleMap(String jsonStr) {
        Map<String, Map<String, Double>> result = new java.util.HashMap<>();
        com.alibaba.fastjson2.JSONObject parsed = ai.yue.library.base.convert.Convert.toJSONObject(jsonStr);
        if (parsed == null) return result;
        parsed.forEach((userGroup, raw) -> {
            if (raw instanceof Map<?, ?> inner) {
                Map<String, Double> innerMap = new java.util.HashMap<>();
                ((Map<String, Object>) inner).forEach((usingGroup, ratio) -> {
                    if (ratio instanceof Number num) innerMap.put(usingGroup, num.doubleValue());
                });
                result.put(userGroup, innerMap);
            }
        });
        return result;
    }

    /**
     * 校验 option 值。      * <p>
     * 覆盖：
     * <ul>
     *   <li>payment_setting.compliance_*：拒绝通过通用接口修改合规字段</li>
     *   <li>OAuth 启用前置（GitHubOAuthEnabled / discord.enabled / oidc.enabled / LinuxDOOAuthEnabled / WeChatAuthEnabled / TelegramOAuthEnabled / TurnstileCheckEnabled / EmailDomainRestrictionEnabled）：启用前必须先填入对应 ClientId/Token/白名单</li>
     *   <li>JSON 倍率字段（GroupRatio/ImageRatio/AudioRatio/AudioCompletionRatio/CreateCacheRatio）：JSON 合法性校验</li>
     *   <li>AutomaticDisableStatusCodes/AutomaticRetryStatusCodes：HTTP 状态码区间格式校验</li>
     *   <li>ModelRequestRateLimitGroup：分组限流配置 JSON 校验</li>      *   <li>console_setting.{api_info|announcements|faq|uptime_kuma_groups}：控制台设置 JSON 列表校验</li>      * </ul>
     */
    private void validateOptionValue(String key, String value) {
        if (key == null) {
            throw new RuntimeException("无效的参数");
        }
        // 1.a payment_setting.compliance_* 拒绝
        if (key.startsWith("payment_setting.compliance_")) {
            throw new RuntimeException("合规确认字段不允许通过通用设置接口修改");
        }
        // 1.b QuotaForInviter/QuotaForInvitee：值为正数时强制要求支付合规确认
        if ("QuotaForInviter".equals(key) || "QuotaForInvitee".equals(key)) {
            if (isPositiveOptionValue(value) && !isPaymentComplianceConfirmed()) {
                throw new RuntimeException("启用邀请奖励前请先确认支付合规声明");
            }
        }
        // 2. OAuth/CAPTCHA 启用前置依赖检查
        if ("true".equalsIgnoreCase(value)) {
            switch (key) {
                case "GitHubOAuthEnabled":
                    requireOptionPresent("GitHubClientId",
                            "无法启用 GitHub OAuth，请先填入 GitHub Client Id 以及 GitHub Client Secret！");
                    break;
                case "discord.enabled":
                    requireOptionPresent("discord.client_id",
                            "无法启用 Discord OAuth，请先填入 Discord Client Id 以及 Discord Client Secret！");
                    break;
                case "oidc.enabled":
                    requireOptionPresent("oidc.client_id",
                            "无法启用 OIDC 登录，请先填入 OIDC Client Id 以及 OIDC Client Secret！");
                    break;
                case "LinuxDOOAuthEnabled":
                    requireOptionPresent("LinuxDOClientId",
                            "无法启用 LinuxDO OAuth，请先填入 LinuxDO Client Id 以及 LinuxDO Client Secret！");
                    break;
                case "EmailDomainRestrictionEnabled":
                    requireOptionPresent("EmailDomainWhitelist",
                            "无法启用邮箱域名限制，请先填入限制的邮箱域名！");
                    break;
                case "WeChatAuthEnabled":
                    requireOptionPresent("WeChatServerAddress",
                            "无法启用微信登录，请先填入微信登录相关配置信息！");
                    break;
                case "TurnstileCheckEnabled":
                    requireOptionPresent("TurnstileSiteKey",
                            "无法启用 Turnstile 校验，请先填入 Turnstile 校验相关配置信息！");
                    break;
                case "TelegramOAuthEnabled":
                    requireOptionPresent("TelegramBotToken",
                            "无法启用 Telegram OAuth，请先填入 Telegram Bot Token！");
                    break;
                default:
                    break;
            }
        }
        // 4. JSON 倍率字段：JSON 合法性校验
        if (isRatioJsonOption(key)) {
            if (value == null || value.isBlank()) return;
            try {
                ai.yue.library.base.convert.Convert.toJSONObject(value);
            } catch (Exception e) {
                throw new RuntimeException(humanizeRatioKey(key) + "设置失败：JSON 格式非法 - " + e.getMessage());
            }
        }
        // 4.b GroupRatio：除 JSON 解析外，还需校验所有 ratio >= 0
        if ("GroupRatio".equals(key)) {
            if (value != null && !value.isBlank()) {
                try {
                    checkGroupRatio(value);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
        // 5. HTTP 状态码区间字段格式校验（如 "500-599,408,429"）
        if ("AutomaticDisableStatusCodes".equals(key) || "AutomaticRetryStatusCodes".equals(key)) {
            if (value == null || value.isBlank()) return;
            try {
                parseStatusCodeRanges(value);
            } catch (Exception e) {
                throw new RuntimeException(key + " 格式非法：" + e.getMessage());
            }
        }
        // 6. ModelRequestRateLimitGroup：分组限流配置 JSON 校验，
        if ("ModelRequestRateLimitGroup".equals(key)) {
            if (value == null || value.isBlank()) return;
            try {
                checkModelRequestRateLimitGroup(value);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        // 7. console_setting.{api_info|announcements|faq|uptime_kuma_groups}：
        String consoleSettingType = mapConsoleSettingType(key);
        if (consoleSettingType != null) {
            try {
                yaoshu.token.config.console.ConsoleSettingValidator
                        .validateConsoleSettings(value, consoleSettingType);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    /**
     * 将 console_setting.* key 映射为 ConsoleSettingValidator 的 settingType，
     */
    private String mapConsoleSettingType(String key) {
        switch (key) {
            case "console_setting.api_info":
                return "ApiInfo";
            case "console_setting.announcements":
                return "Announcements";
            case "console_setting.faq":
                return "FAQ";
            case "console_setting.uptime_kuma_groups":
                return "UptimeKumaGroups";
            default:
                return null;
        }
    }

    /**
     * 校验分组限流配置 JSON。      * <p>
     * 格式：{@code {"group": [totalCount, successCount]}}
     * 校验规则：totalCount >= 0、successCount >= 1、两者均不超过 Integer.MAX_VALUE。
     */
    private void checkModelRequestRateLimitGroup(String jsonStr) {
        ai.yue.library.base.convert.Convert.toJSONObject(jsonStr)
                .forEach((group, raw) -> {
                    if (!(raw instanceof java.util.List)) {
                        throw new IllegalArgumentException("group " + group + " 配置格式错误，期望 [total, success] 两元素数组");
                    }
                    java.util.List<?> limits = (java.util.List<?>) raw;
                    if (limits.size() != 2) {
                        throw new IllegalArgumentException("group " + group + " 配置长度必须为 2（[total, success]）");
                    }
                    long total = ((Number) limits.get(0)).longValue();
                    long success = ((Number) limits.get(1)).longValue();
                    if (total < 0 || success < 1) {
                        throw new IllegalArgumentException("group " + group + " has negative rate limit values: ["
                                + total + ", " + success + "]");
                    }
                    if (total > Integer.MAX_VALUE || success > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("group " + group + " [" + total + ", " + success
                                + "] has max rate limits value 2147483647");
                    }
                });
    }

    /** 检查 option 是否非空（用于 OAuth 启用前置依赖校验） */
    private void requireOptionPresent(String dependentKey, String errorMessage) {
        String v = optionCache.get(dependentKey);
        if (v == null) {
            v = getValue(dependentKey);
        }
        if (v == null || v.isBlank()) {
            throw new RuntimeException(errorMessage);
        }
    }

    private boolean isRatioJsonOption(String key) {
        return "GroupRatio".equals(key)
                || "ImageRatio".equals(key)
                || "AudioRatio".equals(key)
                || "AudioCompletionRatio".equals(key)
                || "CreateCacheRatio".equals(key);
    }

    /**
     * 判断 option 字符串值是否为正数（先尝试整数，再尝试浮点）。
     */
    private boolean isPositiveOptionValue(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        try {
            return Integer.parseInt(trimmed) > 0;
        } catch (NumberFormatException ignored) {
            // ignore，尝试浮点
        }
        try {
            return Double.parseDouble(trimmed) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * 判断当前是否已确认支付合规声明。
     * payment_setting.compliance_confirmed=true 且 compliance_terms_version=v1
     */
    private boolean isPaymentComplianceConfirmed() {
        String confirmed = optionCache.get("payment_setting.compliance_confirmed");
        if (confirmed == null) confirmed = getValue("payment_setting.compliance_confirmed");
        String termsVersion = optionCache.get("payment_setting.compliance_terms_version");
        if (termsVersion == null) termsVersion = getValue("payment_setting.compliance_terms_version");
        return "true".equalsIgnoreCase(confirmed) && "v1".equals(termsVersion);
    }

    /**
     * 校验 GroupRatio JSON：所有分组的 ratio 必须 >= 0。
     */
    private void checkGroupRatio(String jsonStr) {
        com.alibaba.fastjson2.JSONObject parsed =
                ai.yue.library.base.convert.Convert.toJSONObject(jsonStr);
        if (parsed == null) return;
        parsed.forEach((name, raw) -> {
            if (!(raw instanceof Number num)) {
                throw new IllegalArgumentException("group ratio must be a number: " + name);
            }
            if (num.doubleValue() < 0) {
                throw new IllegalArgumentException("group ratio must be not less than 0: " + name);
            }
        });
    }

    private String humanizeRatioKey(String key) {
        switch (key) {
            case "ImageRatio": return "图片倍率";
            case "AudioRatio": return "音频倍率";
            case "AudioCompletionRatio": return "音频补全倍率";
            case "CreateCacheRatio": return "缓存创建倍率";
            case "GroupRatio": return "分组倍率";
            default: return key;
        }
    }

    /**
     * 解析 HTTP 状态码区间字符串。      * 支持格式：单个状态码（如 "429"）、区间（如 "500-599"）、逗号分隔混合（"500-599,408,429"）。
     */
    private void parseStatusCodeRanges(String value) {
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.contains("-")) {
                String[] range = trimmed.split("-");
                if (range.length != 2) {
                    throw new IllegalArgumentException("无效的区间：" + trimmed);
                }
                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());
                if (start < 100 || end > 599 || start > end) {
                    throw new IllegalArgumentException("无效的状态码区间（须在 100-599 之间且起始 <= 结束）：" + trimmed);
                }
            } else {
                int code = Integer.parseInt(trimmed);
                if (code < 100 || code > 599) {
                    throw new IllegalArgumentException("无效的状态码（须在 100-599 之间）：" + trimmed);
                }
            }
        }
    }

    /**
     * 批量更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(Map<String, String> options) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
            saveOrUpdate(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 删除配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String key) {
        optionMapper.deleteById(key);
        optionCache.remove(key);
    }

    /**
     * 刷新缓存
     */
    public void refreshCache() {
        optionCache.clear();
        List<Option> all = getAll();
        for (Option option : all) {
            optionCache.put(option.getKey(), option.getValue());
        }
    }
}