package yaoshu.token.service;

import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.util.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 渠道亲和性服务  * <p>
 * 核心职责：
 * <ul>
 *   <li>基于请求特征（用户ID/IP/Header/Body JSONPath）将请求路由到固定渠道</li>
 *   <li>维护渠道亲和性缓存（TTL 键值对：特征指纹 → 渠道ID）</li>
 *   <li>提供亲和性模板覆盖机制（规则层 param_override 应用到渠道选择）</li>
 * </ul>
 * <p>
 * 依赖：Batch 2c 的 operation_setting 类型（ChannelAffinitySetting 等），当前使用内嵌 POJO 占位。
 * Redis 二级缓存留待后续集成。
 */
@Slf4j
public final class ChannelAffinityService {

    private ChannelAffinityService() {
    }

    // ======================== 缓存命名空间常量 ========================

    private static final String CACHE_NAMESPACE = "yaoshu-token:channel_affinity:v1";
    private static final String USAGE_CACHE_NAMESPACE = "yaoshu-token:channel_affinity_usage_cache_stats:v1";

    // ======================== Gin Context Key 常量 ========================

    private static final String ATTR_AFFINITY_CACHE_KEY = "channel_affinity_cache_key";
    private static final String ATTR_AFFINITY_TTL_SECONDS = "channel_affinity_ttl_seconds";
    private static final String ATTR_AFFINITY_META = "channel_affinity_meta";
    private static final String ATTR_AFFINITY_LOG_INFO = "channel_affinity_log_info";
    private static final String ATTR_AFFINITY_SKIP_RETRY = "channel_affinity_skip_retry";

    // ======================== 缓存 Token Rate Mode 常量 ========================

    private static final String CACHED_OVER_PROMPT = "cached_over_prompt";
    private static final String CACHED_OVER_PROMPT_PLUS_CACHED = "cached_over_prompt_plus_cached";
    private static final String CACHED_MIXED = "mixed";

    // ======================== 内嵌 POJO — 亲和性元数据 ======================== 
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AffinityMeta {
        private String cacheKey;
        private int ttlSeconds;
        private String ruleName;
        private boolean skipRetry;
        private Map<String, Object> paramTemplate;
        private String keySourceType;
        private String keySourceKey;
        private String keySourcePath;
        private String keyHint;
        private String keyFingerprint;
        private String usingGroup;
        private String modelName;
        private String requestPath;
    }

    // ======================== 内嵌 POJO — 统计相关 ========================

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AffinityStatsContext {
        private String ruleName;
        private String usingGroup;
        private String keyFingerprint;
        private long ttlSeconds;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class AffinityCacheStats {
        private boolean enabled;
        private int total;
        private int unknown;
        private Map<String, Integer> byRuleName;
        private int cacheCapacity;
        private String cacheAlgo;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class UsageCacheStats {
        private String cachedTokenRateMode;
        private String ruleName;
        private String usingGroup;
        private String keyFp;
        private long hit;
        private long total;
        private long windowSeconds;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long cachedTokens;
        private long promptCacheHitTokens;
        private long lastSeenAt;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class UsageCacheCounters {
        private String cachedTokenRateMode;
        private long hit;
        private long total;
        private long windowSeconds;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long cachedTokens;
        private long promptCacheHitTokens;
        private long lastSeenAt;
    }

    // ======================== 内嵌 POJO — Setting 类型（Batch 2c 翻译后移出） ========================

    @lombok.Data
    public static class ChannelAffinitySetting {
        private boolean enabled;
        private int maxEntries = 100_000;
        private int defaultTTLSeconds = 3600;
        private boolean keepOnChannelDisabled;
        private boolean switchOnSuccess;
        private List<ChannelAffinityRule> rules = new ArrayList<>();
    }

    @lombok.Data
    public static class ChannelAffinityRule {
        private String name;
        private List<String> modelRegex = new ArrayList<>();
        private List<String> pathRegex = new ArrayList<>();
        private List<String> userAgentInclude = new ArrayList<>();
        private String valueRegex;
        private boolean skipRetryOnFailure;
        private int ttlSeconds;
        private boolean includeRuleName;
        private boolean includeModelName;
        private boolean includeUsingGroup;
        private List<ChannelAffinityKeySource> keySources = new ArrayList<>();
        private Map<String, Object> paramOverrideTemplate = new LinkedHashMap<>();
    }

    @lombok.Data
    public static class ChannelAffinityKeySource {
        private String type;  // context_int / context_string / request_header / gjson
        private String key;
        private String path;
    }

    // ======================== 缓存键追踪（仅用于统计/清理，不存值） ========================

    /** 亲和性缓存键注册表：仅追踪已有键，不存值。实际缓存由 ChannelAffinityCacheManager（JetCache）管理 */
    private static final ConcurrentHashMap<String, Boolean> affinityKeyRegistry = new ConcurrentHashMap<>();

    /** 用统缓存键注册表 */
    private static final ConcurrentHashMap<String, Boolean> usageKeyRegistry = new ConcurrentHashMap<>();

    /** 正则编译缓存 */
    private static final ConcurrentHashMap<String, Pattern> regexCache = new ConcurrentHashMap<>();

    /** JSON 解析器（gjson path 提取用） */
    private static ObjectMapper MAPPER;

    private static ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = SpringUtils.getBean(ObjectMapper.class);
        }
        return MAPPER;
    }

    /** JetCache L2 缓存管理器（由 ChannelAffinityCacheManager @PostConstruct 注入） */
    static volatile ChannelAffinityCacheManager cacheManager;

    /** 供 ChannelAffinityCacheManager 注入引用 */
    static void setCacheManager(ChannelAffinityCacheManager mgr) {
        cacheManager = mgr;
    }

    /** 用量统计锁数组（64 个分段锁*/
    private static final ReentrantLock[] usageStatsLocks = new ReentrantLock[64];

    static {
        for (int i = 0; i < 64; i++) {
            usageStatsLocks[i] = new ReentrantLock();
        }
    }

    /** 全局 Setting 引用（Batch 2c 翻译后由 Setting 模块赋值） */
    private static volatile ChannelAffinitySetting globalSetting;

    /**
     * 设置全局渠道亲和性配置（由 Batch 2c Setting 模块启动时调用）
     */
    public static void setGlobalSetting(ChannelAffinitySetting setting) {
        globalSetting = setting;
    }

    /**
     * 获取全局渠道亲和性配置
     */
    public static ChannelAffinitySetting getGlobalSetting() {
        return globalSetting;
    }

    // ======================== 公共 API — 缓存统计与清理 ========================

    /**
     * 获取渠道亲和性缓存统计      */
    public static AffinityCacheStats getAffinityCacheStats() {
        if (globalSetting == null) {
            AffinityCacheStats s = new AffinityCacheStats();
            s.setEnabled(false);
            return s;
        }

        List<ChannelAffinityRule> rules = globalSetting.rules;

        Map<String, ChannelAffinityRule> ruleByName = new LinkedHashMap<>();
        for (ChannelAffinityRule r : rules) {
            String name = trimToEmpty(r.name);
            if (name.isEmpty() || !r.includeRuleName) continue;
            ruleByName.put(name, r);
        }

        Map<String, Integer> byRuleName = new LinkedHashMap<>();
        for (String name : ruleByName.keySet()) {
            byRuleName.put(name, 0);
        }

        Set<String> keys = affinityKeyRegistry.keySet();
        int total = keys.size();
        int unknown = 0;

        String prefix = CACHE_NAMESPACE + ":";
        for (String k : keys) {
            if (!k.startsWith(prefix)) {
                unknown++;
                continue;
            }
            String rest = k.substring(prefix.length());
            String[] parts = rest.split(":");
            if (parts.length < 2) {
                unknown++;
                continue;
            }
            String ruleName = parts[0];
            ChannelAffinityRule rule = ruleByName.get(ruleName);
            if (rule == null) {
                unknown++;
                continue;
            }
            if (rule.includeModelName && parts.length < 3) {
                unknown++;
                continue;
            }
            if (rule.includeUsingGroup) {
                int minParts = rule.includeModelName ? 4 : 3;
                if (parts.length < minParts) {
                    unknown++;
                    continue;
                }
            }
            byRuleName.merge(ruleName, 1, Integer::sum);
        }

        AffinityCacheStats s = new AffinityCacheStats();
        s.setEnabled(globalSetting.enabled);
        s.setTotal(total);
        s.setUnknown(unknown);
        s.setByRuleName(byRuleName);
        s.setCacheCapacity(globalSetting.maxEntries > 0 ? globalSetting.maxEntries : 100_000);
        s.setCacheAlgo("JetCache-Caffeine-LRU");
        return s;
    }

    /**
     * 清空全部渠道亲和性缓存      */
    public static int clearAllAffinityCache() {
        int count = affinityKeyRegistry.size();
        affinityKeyRegistry.clear();
        // 同步清理 Redis L2 中的 JetCache 键
        if (cacheManager != null) {
            try {
                cacheManager.deleteKeysByPattern("jetcache:channelAffinity:*");
            } catch (Exception ignored) {
                // Redis 不可用时静默降级，内存注册表已清空
            }
        }
        return count;
    }

    /**
     * 按规则名称清空渠道亲和性缓存      */
    public static int clearAffinityCacheByRuleName(String ruleName, ChannelAffinitySetting setting) {
        ruleName = trimToEmpty(ruleName);
        if (ruleName.isEmpty()) {
            return 0;
        }
        if (setting == null) {
            return 0;
        }

        ChannelAffinityRule matchedRule = null;
        for (ChannelAffinityRule r : setting.rules) {
            if (trimToEmpty(r.name).equals(ruleName)) {
                matchedRule = r;
                break;
            }
        }
        if (matchedRule == null || !matchedRule.includeRuleName) {
            return 0;
        }

        int count = 0;
        String prefix = ruleName;
        for (String key : affinityKeyRegistry.keySet()) {
            if (key.startsWith(prefix) && affinityKeyRegistry.remove(key) != null) {
                count++;
            }
        }
        // 同步清理 Redis L2 中该规则前缀的 JetCache 键
        if (cacheManager != null && count > 0) {
            try {
                cacheManager.deleteKeysByPattern("jetcache:channelAffinity:*" + ruleName + "*");
            } catch (Exception ignored) {
                // Redis 不可用时静默降级，内存注册表已清空
            }
        }
        return count;
    }

    // ======================== 公共 API — 渠道亲和性选择 ========================

    /**
     * 根据亲和性规则获取首选渠道ID      * <p>
     * 遍历所有亲和性规则，匹配 modelName/path/userAgent，提取亲和性 Key，
     * 查询缓存返回绑定的渠道ID。
     *
     * @param request     HTTP 请求
     * @param modelName   模型名称
     * @param usingGroup  使用的分组
     * @return [渠道ID, 是否命中]；未命中返回 [0, false]
     */
    public static int[] getPreferredChannelByAffinity(HttpServletRequest request, String modelName, String usingGroup) {
        if (globalSetting == null || !globalSetting.enabled) {
            return new int[]{0, 0};
        }

        String path = (request != null && request.getRequestURI() != null) ? request.getRequestURI() : "";
        String userAgent = (request != null) ? request.getHeader("User-Agent") : "";
        if (userAgent == null) userAgent = "";

        for (ChannelAffinityRule rule : globalSetting.rules) {
            // 模型名正则匹配
            if (!matchAnyRegexCached(rule.modelRegex, modelName)) continue;
            // 路径正则匹配
            if (!rule.pathRegex.isEmpty() && !matchAnyRegexCached(rule.pathRegex, path)) continue;
            // User-Agent 包含匹配
            if (!rule.userAgentInclude.isEmpty() && !matchAnyIncludeFold(rule.userAgentInclude, userAgent)) continue;

            // 提取亲和性值
            String affinityValue = "";
            ChannelAffinityKeySource usedSource = null;
            for (ChannelAffinityKeySource src : rule.keySources) {
                affinityValue = extractAffinityValue(request, src);
                if (!affinityValue.isEmpty()) {
                    usedSource = src;
                    break;
                }
            }
            if (affinityValue.isEmpty()) continue;

            // 值正则校验
            if (rule.valueRegex != null && !rule.valueRegex.isEmpty()
                    && !matchAnyRegexCached(List.of(rule.valueRegex), affinityValue)) {
                continue;
            }

            // 构建缓存 Key 并存入上下文
            int ttlSeconds = rule.ttlSeconds > 0 ? rule.ttlSeconds : globalSetting.defaultTTLSeconds;
            String cacheKeySuffix = buildCacheKeySuffix(rule, modelName, usingGroup, affinityValue);
            String cacheKeyFull = CACHE_NAMESPACE + ":" + cacheKeySuffix;

            AffinityMeta meta = new AffinityMeta();
            meta.cacheKey = cacheKeyFull;
            meta.ttlSeconds = ttlSeconds;
            meta.ruleName = trimToEmpty(rule.name);
            meta.skipRetry = rule.skipRetryOnFailure;
            meta.paramTemplate = cloneStringAnyMap(rule.paramOverrideTemplate);
            meta.keySourceType = usedSource != null ? trimToEmpty(usedSource.type) : "";
            meta.keySourceKey = usedSource != null ? trimToEmpty(usedSource.key) : "";
            meta.keySourcePath = usedSource != null ? trimToEmpty(usedSource.path) : "";
            meta.keyHint = buildKeyHint(affinityValue);
            meta.keyFingerprint = affinityFingerprint(affinityValue);
            meta.usingGroup = usingGroup;
            meta.modelName = modelName;
            meta.requestPath = path;

            setAffinityContext(request, meta);

            // 通过 JetCache（Caffeine L1 + Redis L2）查询渠道亲和性
            if (cacheManager != null) {
                Integer channelId = cacheManager.getAffinity(cacheKeySuffix);
                if (channelId != null && channelId > 0) {
                    return new int[]{channelId, 1};
                }
            }

            return new int[]{0, 0};
        }

        return new int[]{0, 0};
    }

    /**
     * 记录渠道亲和性（请求成功后调用）      */
    public static void recordChannelAffinity(HttpServletRequest request, int channelId) {
        if (channelId <= 0) return;
        if (globalSetting == null || !globalSetting.enabled) return;

        // Go: setting.SwitchOnSuccess → 使用上下文中 channel_id（successChannelID）
        if (globalSetting.switchOnSuccess && request != null) {
            Object successId = request.getAttribute("channel_id");
            if (successId instanceof Integer && (Integer) successId > 0) {
                channelId = (Integer) successId;
            }
        }

        AffinityMeta meta = getAffinityMeta(request);
        if (meta == null) return;

        // 通过 JetCache 写入（Caffeine L1 + Redis L2），同步更新键注册表
        if (cacheManager != null) {
            cacheManager.setAffinity(meta.cacheKey, channelId);
            affinityKeyRegistry.put(meta.cacheKey, Boolean.TRUE);
        }
    }

    /**
     * 标记渠道亲和性已被使用（选择渠道后调用）      */
    public static void markChannelAffinityUsed(HttpServletRequest request, String selectedGroup, int channelId) {
        if (request == null || channelId <= 0) return;

        AffinityMeta meta = getAffinityMeta(request);
        if (meta == null) return;

        request.setAttribute(ATTR_AFFINITY_SKIP_RETRY, meta.skipRetry);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("reason", meta.ruleName);
        info.put("rule_name", meta.ruleName);
        info.put("using_group", meta.usingGroup);
        info.put("selected_group", selectedGroup);
        info.put("model", meta.modelName);
        info.put("request_path", meta.requestPath);
        info.put("channel_id", channelId);
        info.put("key_source", meta.keySourceType);
        info.put("key_key", meta.keySourceKey);
        info.put("key_path", meta.keySourcePath);
        info.put("key_hint", meta.keyHint);
        info.put("key_fp", meta.keyFingerprint);
        request.setAttribute(ATTR_AFFINITY_LOG_INFO, info);
    }

    /**
     * 将渠道亲和性信息追加到管理员信息中      */
    @SuppressWarnings("unchecked")
    public static void appendAffinityAdminInfo(HttpServletRequest request, Map<String, Object> adminInfo) {
        if (request == null || adminInfo == null) return;
        Object anyInfo = request.getAttribute(ATTR_AFFINITY_LOG_INFO);
        if (anyInfo != null) {
            adminInfo.put("channel_affinity", anyInfo);
        }
    }

    /**
     * 清除当前请求的渠道亲和性缓存（请求失败时调用）      */
    public static boolean clearCurrentAffinityCache(HttpServletRequest request) {
        if (request == null) return false;

        String cacheKey = (String) request.getAttribute(ATTR_AFFINITY_CACHE_KEY);
        if (cacheKey == null || cacheKey.isEmpty()) return false;

        affinityKeyRegistry.remove(cacheKey);
        if (cacheManager != null) {
            cacheManager.removeAffinity(cacheKey);
        }
        request.setAttribute(ATTR_AFFINITY_SKIP_RETRY, false);
        return true;
    }

    /**
     * 亲和性失败后是否应跳过重试      */
    public static boolean shouldSkipRetryAfterAffinityFailure(HttpServletRequest request) {
        if (request == null) return false;
        Object v = request.getAttribute(ATTR_AFFINITY_SKIP_RETRY);
        if (v instanceof Boolean) return (Boolean) v;

        AffinityMeta meta = getAffinityMeta(request);
        return meta != null && meta.skipRetry;
    }

    /**
     * 渠道禁用时是否保留亲和性      */
    public static boolean shouldKeepAffinityOnChannelDisabled() {
        return globalSetting != null && globalSetting.keepOnChannelDisabled;
    }

    // ======================== 公共 API — 参数模板覆盖 ========================

    /**
     * 应用渠道亲和性参数覆盖模板      */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyOverrideTemplate(HttpServletRequest request, Map<String, Object> paramOverride) {
        if (request == null) return paramOverride;

        AffinityMeta meta = getAffinityMeta(request);
        if (meta == null || meta.paramTemplate == null || meta.paramTemplate.isEmpty()) {
            return paramOverride;
        }

        Map<String, Object> merged = mergeChannelOverride(paramOverride, meta.paramTemplate);
        appendTemplateAdminInfo(request, meta);
        return merged;
    }

    // ======================== 公共 API — 用量统计 ========================

    /**
     * 观察渠道亲和性用量缓存（按 RelayFormat）      */
    public static void observeUsageCacheByRelayFormat(HttpServletRequest request, Usage usage, String relayFormat) {
        observeUsageCacheFromContext(request, usage, cachedTokenRateModeByRelayFormat(relayFormat));
    }

    /**
     * 观察渠道亲和性用量缓存      */
    public static void observeUsageCacheFromContext(HttpServletRequest request, Usage usage, String cachedTokenRateMode) {
        AffinityStatsContext ctx = getAffinityStatsContext(request);
        if (ctx == null) return;
        observeUsageCache(ctx, usage, cachedTokenRateMode);
    }

    /**
     * 获取渠道亲和性用量缓存统计      */
    public static UsageCacheStats getUsageCacheStats(String ruleName, String usingGroup, String keyFp) {
        ruleName = trimToEmpty(ruleName);
        usingGroup = trimToEmpty(usingGroup);
        keyFp = trimToEmpty(keyFp);

        String entryKey = usageCacheEntryKey(ruleName, usingGroup, keyFp);
        if (entryKey.isEmpty()) {
            return new UsageCacheStats();
        }

        UsageCacheCounters v = cacheManager != null ? cacheManager.getUsageStats(entryKey) : null;
        if (v == null) {
            UsageCacheStats s = new UsageCacheStats();
            s.setRuleName(ruleName);
            s.setUsingGroup(usingGroup);
            s.setKeyFp(keyFp);
            return s;
        }

        UsageCacheStats s = new UsageCacheStats();
        s.setCachedTokenRateMode(v.cachedTokenRateMode);
        s.setRuleName(ruleName);
        s.setUsingGroup(usingGroup);
        s.setKeyFp(keyFp);
        s.setHit(v.hit);
        s.setTotal(v.total);
        s.setWindowSeconds(v.windowSeconds);
        s.setPromptTokens(v.promptTokens);
        s.setCompletionTokens(v.completionTokens);
        s.setTotalTokens(v.totalTokens);
        s.setCachedTokens(v.cachedTokens);
        s.setPromptCacheHitTokens(v.promptCacheHitTokens);
        s.setLastSeenAt(v.lastSeenAt);
        return s;
    }

    // ======================== 内部方法 — 缓存 Key 构建 ========================

    private static String buildCacheKeySuffix(ChannelAffinityRule rule, String modelName, String usingGroup, String affinityValue) {
        List<String> parts = new ArrayList<>(4);
        if (rule.includeRuleName && !trimToEmpty(rule.name).isEmpty()) {
            parts.add(rule.name);
        }
        if (rule.includeModelName && modelName != null && !modelName.isEmpty()) {
            parts.add(modelName);
        }
        if (rule.includeUsingGroup && usingGroup != null && !usingGroup.isEmpty()) {
            parts.add(usingGroup);
        }
        parts.add(affinityValue);
        return String.join(":", parts);
    }

    // ======================== 内部方法 — 亲和性值提取 ========================

    /**
     * 从请求中提取亲和性 Key 值      */
    private static String extractAffinityValue(HttpServletRequest request, ChannelAffinityKeySource src) {
        if (src == null) return "";

        switch (trimToEmpty(src.type)) {
            case "context_int":
                if (src.key == null || src.key.isEmpty()) return "";
                Object intVal = request != null ? request.getAttribute(src.key) : null;
                if (intVal instanceof Integer && (Integer) intVal > 0) {
                    return String.valueOf(intVal);
                }
                return "";

            case "context_string":
                if (src.key == null || src.key.isEmpty()) return "";
                Object strVal = request != null ? request.getAttribute(src.key) : null;
                return strVal != null ? trimToEmpty(strVal.toString()) : "";

            case "request_header":
                if (request == null || src.key == null || src.key.isEmpty()) return "";
                return trimToEmpty(request.getHeader(src.key));

            case "gjson":
                // 从请求体按 JSON path 提取（yue-library 已开启 repeatedly-read，可重复读取请求体）
                if (request == null || src.path == null || src.path.isEmpty()) return "";
                return extractGjsonValue(request, src.path);

            default:
                return "";
        }
    }

    /**
     * 从请求体按 JSON path 提取标量值。      * <p>
     * path 语法：点号分隔，纯数字段视为数组下标（如 messages.0.role）。
     * 标量（string/number/bool）返回其文本，复合类型返回其原始 JSON 串。
     */
    private static String extractGjsonValue(HttpServletRequest request, String path) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0) return "";
            JsonNode node = getMapper().readTree(body);
            for (String seg : path.split("\\.")) {
                if (node == null) return "";
                if (node.isArray()) {
                    int idx;
                    try {
                        idx = Integer.parseInt(seg);
                    } catch (NumberFormatException e) {
                        return "";
                    }
                    if (idx < 0 || idx >= node.size()) return "";
                    node = node.get(idx);
                } else if (node.isObject()) {
                    node = node.get(seg);
                } else {
                    return "";
                }
            }
            if (node == null || node.isNull()) return "";
            // 标量返回文本，复合类型返回原始 JSON
            return trimToEmpty(node.isValueNode() ? node.asText() : node.toString());
        } catch (Exception e) {
            log.debug("提取渠道亲和性 gjson 值失败 path={}: {}", path, e.getMessage());
            return "";
        }
    }

    // ======================== 内部方法 — 正则匹配 ========================

    private static boolean matchAnyRegexCached(List<String> patterns, String s) {
        if (patterns == null || patterns.isEmpty() || s == null || s.isEmpty()) return false;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            Pattern p = regexCache.computeIfAbsent(pattern, k -> {
                try {
                    return Pattern.compile(k);
                } catch (Exception e) {
                    return null;
                }
            });
            if (p != null && p.matcher(s).find()) return true;
        }
        return false;
    }

    private static boolean matchAnyIncludeFold(List<String> patterns, String s) {
        if (patterns == null || patterns.isEmpty() || s == null || s.isEmpty()) return false;
        String sLower = s.toLowerCase();
        for (String p : patterns) {
            p = trimToEmpty(p);
            if (p.isEmpty()) continue;
            if (sLower.contains(p.toLowerCase())) return true;
        }
        return false;
    }

    // ======================== 内部方法 — 指纹与 Hint ========================

    private static String affinityFingerprint(String s) {
        if (s == null || s.isEmpty()) return "";
        String hex = sha1(s);
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String buildKeyHint(String s) {
        s = trimToEmpty(s);
        if (s.isEmpty()) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        if (s.length() <= 12) return s;
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4);
    }

    // ======================== 内部方法 — Map 操作 ========================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cloneStringAnyMap(Map<String, Object> src) {
        if (src == null || src.isEmpty()) return new LinkedHashMap<>();
        return new LinkedHashMap<>(src);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeChannelOverride(Map<String, Object> base, Map<String, Object> tpl) {
        if ((base == null || base.isEmpty()) && (tpl == null || tpl.isEmpty())) {
            return new LinkedHashMap<>();
        }
        if (tpl == null || tpl.isEmpty()) return base;

        Map<String, Object> out = cloneStringAnyMap(base);
        for (Map.Entry<String, Object> entry : tpl.entrySet()) {
            String k = entry.getKey();
            if ("operations".equalsIgnoreCase(trimToEmpty(k))) {
                List<Object> baseOps = extractParamOperations(out.get(k));
                List<Object> tplOps = extractParamOperations(entry.getValue());
                if (tplOps != null) {
                    if (baseOps != null) {
                        List<Object> merged = new ArrayList<>(tplOps);
                        merged.addAll(baseOps);
                        out.put(k, merged);
                    } else {
                        out.put(k, tplOps);
                    }
                }
                continue;
            }
            if (out.containsKey(k)) continue;
            out.put(k, entry.getValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> extractParamOperations(Object value) {
        if (value instanceof List) {
            List<?> ops = (List<?>) value;
            if (ops.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(ops);
        }
        return null;
    }

    // ======================== 内部方法 — 上下文操作 ========================

    private static void setAffinityContext(HttpServletRequest request, AffinityMeta meta) {
        if (request == null) return;
        request.setAttribute(ATTR_AFFINITY_CACHE_KEY, meta.cacheKey);
        request.setAttribute(ATTR_AFFINITY_TTL_SECONDS, meta.ttlSeconds);
        request.setAttribute(ATTR_AFFINITY_META, meta);
    }

    private static AffinityMeta getAffinityMeta(HttpServletRequest request) {
        if (request == null) return null;
        Object obj = request.getAttribute(ATTR_AFFINITY_META);
        if (obj instanceof AffinityMeta) return (AffinityMeta) obj;
        return null;
    }

    private static AffinityStatsContext getAffinityStatsContext(HttpServletRequest request) {
        if (request == null) return null;
        AffinityMeta meta = getAffinityMeta(request);
        if (meta == null) return null;

        String ruleName = trimToEmpty(meta.ruleName);
        String keyFp = trimToEmpty(meta.keyFingerprint);
        String usingGroup = trimToEmpty(meta.usingGroup);
        if (ruleName.isEmpty() || keyFp.isEmpty()) return null;

        long ttlSeconds = meta.ttlSeconds;
        if (ttlSeconds <= 0) return null;

        return new AffinityStatsContext(ruleName, usingGroup, keyFp, ttlSeconds);
    }

    @SuppressWarnings("unchecked")
    private static void appendTemplateAdminInfo(HttpServletRequest request, AffinityMeta meta) {
        if (request == null || meta.paramTemplate == null || meta.paramTemplate.isEmpty()) return;

        Map<String, Object> templateInfo = new LinkedHashMap<>();
        templateInfo.put("applied", true);
        templateInfo.put("rule_name", meta.ruleName);
        templateInfo.put("param_override_keys", meta.paramTemplate.size());

        Object anyInfo = request.getAttribute(ATTR_AFFINITY_LOG_INFO);
        if (anyInfo instanceof Map) {
            ((Map<String, Object>) anyInfo).put("override_template", templateInfo);
            return;
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("reason", meta.ruleName);
        info.put("rule_name", meta.ruleName);
        info.put("using_group", meta.usingGroup);
        info.put("model", meta.modelName);
        info.put("request_path", meta.requestPath);
        info.put("key_source", meta.keySourceType);
        info.put("key_key", meta.keySourceKey);
        info.put("key_path", meta.keySourcePath);
        info.put("key_hint", meta.keyHint);
        info.put("key_fp", meta.keyFingerprint);
        info.put("override_template", templateInfo);
        request.setAttribute(ATTR_AFFINITY_LOG_INFO, info);
    }

    // ======================== 内部方法 — 用量统计 ========================

    private static void observeUsageCache(AffinityStatsContext ctx, Usage usage, String cachedTokenRateMode) {
        String entryKey = usageCacheEntryKey(ctx.ruleName, ctx.usingGroup, ctx.keyFingerprint);
        if (entryKey.isEmpty()) return;

        long windowSeconds = ctx.ttlSeconds;
        if (windowSeconds <= 0) return;

        Lock lock = usageStatsLock(entryKey);
        lock.lock();
        try {
            // JetCache 返回值可能被共享，需防御性复制后再修改
            UsageCacheCounters prev = cacheManager != null ? cacheManager.getUsageStats(entryKey) : null;
            UsageCacheCounters current;
            if (prev != null) {
                current = new UsageCacheCounters();
                current.cachedTokenRateMode = prev.cachedTokenRateMode;
                current.hit = prev.hit;
                current.total = prev.total;
                current.windowSeconds = prev.windowSeconds;
                current.promptTokens = prev.promptTokens;
                current.completionTokens = prev.completionTokens;
                current.totalTokens = prev.totalTokens;
                current.cachedTokens = prev.cachedTokens;
                current.promptCacheHitTokens = prev.promptCacheHitTokens;
                current.lastSeenAt = prev.lastSeenAt;
            } else {
                current = new UsageCacheCounters();
            }

            String currentMode = normalizeCachedTokenRateMode(cachedTokenRateMode);
            if (!currentMode.isEmpty()) {
                if (current.cachedTokenRateMode == null || current.cachedTokenRateMode.isEmpty()) {
                    current.cachedTokenRateMode = currentMode;
                } else if (!current.cachedTokenRateMode.equals(currentMode) && !current.cachedTokenRateMode.equals(CACHED_MIXED)) {
                    current.cachedTokenRateMode = CACHED_MIXED;
                }
            }

            current.total++;
            long[] signals = usageCacheSignals(usage);
            if (signals[0] == 1) current.hit++;
            current.windowSeconds = windowSeconds;
            current.lastSeenAt = System.currentTimeMillis() / 1000;
            current.cachedTokens += signals[1];
            current.promptCacheHitTokens += signals[2];
            current.promptTokens += usagePromptTokens(usage);
            current.completionTokens += usageCompletionTokens(usage);
            current.totalTokens += usageTotalTokens(usage);

            if (cacheManager != null) {
                cacheManager.setUsageStats(entryKey, current);
            }
            usageKeyRegistry.put(entryKey, Boolean.TRUE);
        } finally {
            lock.unlock();
        }
    }

    private static String normalizeCachedTokenRateMode(String mode) {
        if (mode == null) return "";
        switch (mode) {
            case CACHED_OVER_PROMPT:
            case CACHED_OVER_PROMPT_PLUS_CACHED:
            case CACHED_MIXED:
                return mode;
            default:
                return "";
        }
    }

    private static String cachedTokenRateModeByRelayFormat(String relayFormat) {
        if (relayFormat == null) return "";
        switch (relayFormat) {
            case "OpenAI":
            case "OpenAIResponses":
            case "OpenAIResponsesCompaction":
                return CACHED_OVER_PROMPT;
            case "Claude":
                return CACHED_OVER_PROMPT_PLUS_CACHED;
            default:
                return "";
        }
    }

    private static String usageCacheEntryKey(String ruleName, String usingGroup, String keyFp) {
        ruleName = trimToEmpty(ruleName);
        usingGroup = trimToEmpty(usingGroup);
        keyFp = trimToEmpty(keyFp);
        if (ruleName.isEmpty() || keyFp.isEmpty()) return "";
        return ruleName + "\n" + usingGroup + "\n" + keyFp;
    }

    /**
     * 解析 Usage 中的缓存信号，返回 [hit(0/1), cachedTokens, promptCacheHitTokens]
     */
    private static long[] usageCacheSignals(Usage usage) {
        if (usage == null) return new long[]{0, 0, 0};

        // 缓存命中数：优先 promptTokensDetails.cachedTokens；为 0 时回落到 inputTokensDetails.cachedTokens
        // （OpenAI Responses API 流式响应使用 inputTokensDetails 承载缓存命中数）
        long cached = 0;
        if (usage.promptTokensDetails != null && usage.promptTokensDetails.cachedTokens > 0) {
            cached = usage.promptTokensDetails.cachedTokens;
        } else if (usage.inputTokensDetails != null && usage.inputTokensDetails.cachedTokens > 0) {
            cached = usage.inputTokensDetails.cachedTokens;
        }
        long pcht = 0;
        if (usage.promptCacheHitTokens > 0) {
            pcht = usage.promptCacheHitTokens;
        }

        long hit = (cached > 0 || pcht > 0) ? 1 : 0;
        return new long[]{hit, cached, pcht};
    }

    private static long usagePromptTokens(Usage usage) {
        if (usage == null) return 0;
        if (usage.promptTokens > 0) return usage.promptTokens;
        return usage.inputTokens;
    }

    private static long usageCompletionTokens(Usage usage) {
        if (usage == null) return 0;
        if (usage.completionTokens > 0) return usage.completionTokens;
        return usage.outputTokens;
    }

    private static long usageTotalTokens(Usage usage) {
        if (usage == null) return 0;
        if (usage.totalTokens > 0) return usage.totalTokens;
        long pt = usagePromptTokens(usage);
        long ct = usageCompletionTokens(usage);
        if (pt > 0 || ct > 0) return pt + ct;
        return 0;
    }

    private static Lock usageStatsLock(String key) {
        int hash = Math.abs(key.hashCode());
        return usageStatsLocks[hash % 64];
    }

    // ======================== 工具方法 ========================

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    // ======================== Usage POJO（简化版，正式定义在 dto/Usage.java） ========================

    @lombok.Data
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int inputTokens;
        private int outputTokens;
        private long promptCacheHitTokens;
        private PromptTokensDetails promptTokensDetails;
        private InputTokensDetails inputTokensDetails;

        @lombok.Data
        public static class PromptTokensDetails {
            private int cachedTokens;
        }

        @lombok.Data
        public static class InputTokensDetails {
            private int cachedTokens;
        }
    }
}
