package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;
import yaoshu.token.config.ratio.CacheRatioConfig;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.ModelRatioConstants;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.pojo.entity.Channel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 比率同步服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatioSyncService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final String DEFAULT_ENDPOINT = "/api/pricing";
    private static final int MAX_CONCURRENT_FETCHES = 8;
    private static final int OFFICIAL_RATIO_PRESET_ID = -100;
    private static final String OFFICIAL_RATIO_PRESET_NAME = "官方倍率预设";
    private static final String OFFICIAL_RATIO_PRESET_BASE_URL = "https://basellm.github.io";
    private static final int MODELS_DEV_PRESET_ID = -101;
    private static final String MODELS_DEV_PRESET_NAME = "models.dev 价格预设";
    private static final String MODELS_DEV_PRESET_BASE_URL = "https://models.dev";
    private static final double FLOAT_EPSILON = 1e-9;

    private final ChannelMapper channelMapper;
    private final UpstreamSyncHttpService upstreamSyncHttpService;
    private final PricingService pricingService;
    private final OptionService optionService;
    public List<Map<String, Object>> getSyncableChannels() {
        List<Map<String, Object>> result = channelMapper.selectList(null).stream()
                .map(this::toSyncableChannel)
                .filter(item -> item.get("base_url") != null && !item.get("base_url").toString().isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
        result.add(syncableChannel(OFFICIAL_RATIO_PRESET_ID, OFFICIAL_RATIO_PRESET_NAME, OFFICIAL_RATIO_PRESET_BASE_URL, 1, 0));
        result.add(syncableChannel(MODELS_DEV_PRESET_ID, MODELS_DEV_PRESET_NAME, MODELS_DEV_PRESET_BASE_URL, 1, 0));
        return result;
    }

    public Map<String, Object> fetch(Map<String, Object> body) {
        int timeout = parseInt(body.get("timeout"), DEFAULT_TIMEOUT_SECONDS);
        List<UpstreamSpec> upstreams = resolveUpstreams(body);
        if (upstreams.isEmpty()) {
            throw new IllegalArgumentException(I18nUtils.get("channel.no_valid_upstream_generic"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(upstreams.size(), MAX_CONCURRENT_FETCHES));
        try {
            List<CompletableFuture<UpstreamResult>> futures = upstreams.stream()
                    .map(spec -> CompletableFuture.supplyAsync(() -> fetchOne(spec, timeout), executor))
                    .toList();

            List<UpstreamResult> results = futures.stream().map(CompletableFuture::join).toList();
            List<Map<String, Object>> testResults = new ArrayList<>();
            List<UpstreamResult> successfulResults = new ArrayList<>();
            for (UpstreamResult result : results) {
                if (result.error() != null) {
                    testResults.add(new LinkedHashMap<>(Map.of(
                            "name", result.name(),
                            "status", "error",
                            "error", result.error()
                    )));
                } else {
                    testResults.add(new LinkedHashMap<>(Map.of(
                            "name", result.name(),
                            "status", "success"
                    )));
                    successfulResults.add(result);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("differences", buildDifferences(getLocalPricingSyncData(), successfulResults));
            data.put("test_results", testResults);
            return data;
        } finally {
            executor.shutdown();
        }
    }

    private List<UpstreamSpec> resolveUpstreams(Map<String, Object> body) {
        List<UpstreamSpec> specs = new ArrayList<>();
        Object upstreamsRaw = body.get("upstreams");
        if (upstreamsRaw instanceof List<?> rawList && !rawList.isEmpty()) {
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                String baseUrl = trimToNull(rawMap.get("base_url"));
                String name = trimToNull(rawMap.get("name"));
                if (baseUrl == null || name == null || !baseUrl.startsWith("http")) {
                    continue;
                }
                specs.add(new UpstreamSpec(
                        parseInt(rawMap.get("id"), 0),
                        name,
                        trimRightSlash(baseUrl),
                        trimToNull(rawMap.get("endpoint")),
                        null
                ));
            }
            return specs;
        }

        Object channelIdsRaw = body.get("channel_ids");
        if (!(channelIdsRaw instanceof List<?> idList) || idList.isEmpty()) {
            return specs;
        }
        List<Integer> channelIds = idList.stream()
                .map(item -> parseInt(item, 0))
                .filter(id -> id > 0)
                .toList();
        if (channelIds.isEmpty()) {
            return specs;
        }
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>().in(Channel::getId, channelIds));
        for (Channel channel : channels) {
            String baseUrl = getBaseUrl(channel);
            if (baseUrl == null || !baseUrl.startsWith("http")) {
                continue;
            }
            specs.add(new UpstreamSpec(
                    channel.getId(),
                    channel.getName(),
                    trimRightSlash(baseUrl),
                    null,
                    channel
            ));
        }
        return specs;
    }

    private UpstreamResult fetchOne(UpstreamSpec spec, int timeout) {
        try {
            String fullUrl;
            boolean openRouter = "openrouter".equalsIgnoreCase(spec.endpoint());
            if (openRouter) {
                fullUrl = spec.baseUrl() + "/v1/models";
            } else if (spec.endpoint() != null && (spec.endpoint().startsWith("http://") || spec.endpoint().startsWith("https://"))) {
                fullUrl = spec.endpoint();
            } else {
                String endpoint = spec.endpoint();
                if (endpoint == null || endpoint.isBlank()) {
                    endpoint = DEFAULT_ENDPOINT;
                } else if (!endpoint.startsWith("/")) {
                    endpoint = "/" + endpoint;
                }
                fullUrl = spec.baseUrl() + endpoint;
            }

            if (openRouter && spec.channel() == null) {
                return new UpstreamResult(spec.displayName(), null, "OpenRouter requires a valid channel with API key");
            }

            // OpenRouter 需注入 Authorization: Bearer xxx 头部（Go controller/ratio_sync.go L223-L240）
            // 从 channel.key 或 channel.keys 第一个非空项取可用密钥
            Map<String, String> headers = null;
            if (openRouter) {
                String apiKey = resolveChannelApiKey(spec.channel());
                if (apiKey == null || apiKey.isBlank()) {
                    return new UpstreamResult(spec.displayName(), null, "no API key configured for this channel");
                }
                headers = Map.of("Authorization", "Bearer " + apiKey.trim());
            }

            JsonNode root = upstreamSyncHttpService.fetchJson(fullUrl, timeout, 10, headers);
            Map<String, Object> converted;
            if (openRouter) {
                converted = convertOpenRouterToRatioData(root);
            } else if (isModelsDevEndpoint(fullUrl)) {
                converted = convertModelsDevToRatioData(root);
            } else {
                converted = convertStandardRatioData(root);
            }
            return new UpstreamResult(spec.displayName(), converted, null);
        } catch (Exception e) {
            return new UpstreamResult(spec.displayName(), null, e.getMessage());
        }
    }

    private Map<String, Object> convertStandardRatioData(JsonNode root) throws IOException {
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        JsonNode successNode = root.get("success");
        if (successNode != null && successNode.isBoolean() && !successNode.booleanValue()) {
            throw new IOException(root.path("message").asText(I18nUtils.get("channel.cannot_parse_upstream_data")));
        }

        if (dataNode != null && dataNode.isObject() && containsAnyField(dataNode, pricingSyncFields())) {
            return Convert.toJavaBean(dataNode, LinkedHashMap.class);
        }
        if (dataNode != null && dataNode.isArray()) {
            Map<String, Object> converted = new LinkedHashMap<>();
            Map<String, Object> modelRatio = new LinkedHashMap<>();
            Map<String, Object> completionRatio = new LinkedHashMap<>();
            Map<String, Object> cacheRatio = new LinkedHashMap<>();
            Map<String, Object> createCacheRatio = new LinkedHashMap<>();
            Map<String, Object> imageRatio = new LinkedHashMap<>();
            Map<String, Object> audioRatio = new LinkedHashMap<>();
            Map<String, Object> audioCompletionRatio = new LinkedHashMap<>();
            Map<String, Object> modelPrice = new LinkedHashMap<>();
            Map<String, Object> billingMode = new LinkedHashMap<>();
            Map<String, Object> billingExpr = new LinkedHashMap<>();

            for (JsonNode item : dataNode) {
                String modelName = item.path("model_name").asText("");
                if (modelName.isBlank()) {
                    continue;
                }
                if (item.path("quota_type").asInt(0) == 1) {
                    if (item.has("model_price")) {
                        modelPrice.put(modelName, item.path("model_price").asDouble());
                    }
                } else if (item.has("model_ratio")) {
                    modelRatio.put(modelName, item.path("model_ratio").asDouble());
                    completionRatio.put(modelName, item.path("completion_ratio").asDouble());
                }
                putIfPresent(item, "cache_ratio", modelName, cacheRatio);
                putIfPresent(item, "create_cache_ratio", modelName, createCacheRatio);
                putIfPresent(item, "image_ratio", modelName, imageRatio);
                putIfPresent(item, "audio_ratio", modelName, audioRatio);
                putIfPresent(item, "audio_completion_ratio", modelName, audioCompletionRatio);
                if (item.has("billing_mode") && !item.path("billing_mode").asText("").isBlank()) {
                    billingMode.put(modelName, item.path("billing_mode").asText());
                }
                if (item.has("billing_expr") && !item.path("billing_expr").asText("").isBlank()) {
                    billingExpr.put(modelName, item.path("billing_expr").asText());
                }
            }
            putMapIfNotEmpty(converted, "model_ratio", modelRatio);
            putMapIfNotEmpty(converted, "completion_ratio", completionRatio);
            putMapIfNotEmpty(converted, "cache_ratio", cacheRatio);
            putMapIfNotEmpty(converted, "create_cache_ratio", createCacheRatio);
            putMapIfNotEmpty(converted, "image_ratio", imageRatio);
            putMapIfNotEmpty(converted, "audio_ratio", audioRatio);
            putMapIfNotEmpty(converted, "audio_completion_ratio", audioCompletionRatio);
            putMapIfNotEmpty(converted, "model_price", modelPrice);
            putMapIfNotEmpty(converted, "billing_mode", billingMode);
            putMapIfNotEmpty(converted, "billing_expr", billingExpr);
            return converted;
        }
        throw new IOException(I18nUtils.get("channel.cannot_parse_upstream_data"));
    }

    private Map<String, Object> convertOpenRouterToRatioData(JsonNode root) {
        Map<String, Object> modelRatio = new LinkedHashMap<>();
        Map<String, Object> completionRatio = new LinkedHashMap<>();
        Map<String, Object> cacheRatio = new LinkedHashMap<>();
        for (JsonNode item : upstreamSyncHttpService.extractDataArrayOrRoot(root)) {
            String id = item.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            double prompt = parseDouble(item.path("pricing").path("prompt").asText(), Double.NaN);
            double completion = parseDouble(item.path("pricing").path("completion").asText(), Double.NaN);
            if (Double.isNaN(prompt) && Double.isNaN(completion)) {
                continue;
            }
            if (Double.isNaN(prompt)) {
                prompt = 0;
            }
            if (Double.isNaN(completion)) {
                completion = 0;
            }
            if (prompt < 0 || completion < 0) {
                continue;
            }
            if (prompt == 0 && completion == 0) {
                modelRatio.put(id, 0.0);
                continue;
            }
            if (prompt <= 0) {
                continue;
            }
            modelRatio.put(id, roundRatioValue(prompt * 1000 * ModelRatioConstants.USD));
            completionRatio.put(id, roundRatioValue(completion / prompt));
            double cacheRead = parseDouble(item.path("pricing").path("input_cache_read").asText(), Double.NaN);
            if (!Double.isNaN(cacheRead) && cacheRead >= 0) {
                cacheRatio.put(id, roundRatioValue(cacheRead / prompt));
            }
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        putMapIfNotEmpty(converted, "model_ratio", modelRatio);
        putMapIfNotEmpty(converted, "completion_ratio", completionRatio);
        putMapIfNotEmpty(converted, "cache_ratio", cacheRatio);
        return converted;
    }

    private Map<String, Object> convertModelsDevToRatioData(JsonNode root) {
        if (!root.isObject() || root.isEmpty()) {
            throw new IllegalArgumentException("empty models.dev response");
        }
        Map<String, ModelsDevCandidate> selected = new LinkedHashMap<>();
        List<String> providers = new ArrayList<>();
        root.fieldNames().forEachRemaining(providers::add);
        providers.sort(Comparator.naturalOrder());
        for (String provider : providers) {
            JsonNode modelsNode = root.path(provider).path("models");
            if (!modelsNode.isObject()) {
                continue;
            }
            List<String> modelNames = new ArrayList<>();
            modelsNode.fieldNames().forEachRemaining(modelNames::add);
            modelNames.sort(Comparator.naturalOrder());
            for (String modelName : modelNames) {
                JsonNode cost = modelsNode.path(modelName).path("cost");
                ModelsDevCandidate candidate = buildModelsDevCandidate(provider, cost);
                if (candidate == null) {
                    continue;
                }
                ModelsDevCandidate current = selected.get(modelName);
                if (current == null || shouldReplaceModelsDevCandidate(current, candidate)) {
                    selected.put(modelName, candidate);
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("no valid models.dev pricing entries found");
        }

        Map<String, Object> modelRatio = new LinkedHashMap<>();
        Map<String, Object> completionRatio = new LinkedHashMap<>();
        Map<String, Object> cacheRatio = new LinkedHashMap<>();
        for (Map.Entry<String, ModelsDevCandidate> entry : selected.entrySet()) {
            String modelName = entry.getKey();
            ModelsDevCandidate candidate = entry.getValue();
            if (candidate.input() == 0) {
                modelRatio.put(modelName, 0.0);
                continue;
            }
            modelRatio.put(modelName, roundRatioValue(candidate.input() * ModelRatioConstants.USD / 1000.0));
            if (candidate.output() != null) {
                completionRatio.put(modelName, roundRatioValue(candidate.output() / candidate.input()));
            }
            if (candidate.cacheRead() != null) {
                cacheRatio.put(modelName, roundRatioValue(candidate.cacheRead() / candidate.input()));
            }
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        putMapIfNotEmpty(converted, "model_ratio", modelRatio);
        putMapIfNotEmpty(converted, "completion_ratio", completionRatio);
        putMapIfNotEmpty(converted, "cache_ratio", cacheRatio);
        return converted;
    }

    private Map<String, Object> getLocalPricingSyncData() {
        Map<String, Object> data = new LinkedHashMap<>();
        // ① 5 字段 model_ratio/completion_ratio/cache_ratio/create_cache_ratio/model_price
        // 来源：PricingService.getRatioExposureSnapshot()
        Map<String, Map<String, Double>> snapshot;
        try {
            snapshot = pricingService.getRatioExposureSnapshot();
        } catch (Exception e) {
            log.warn("getRatioExposureSnapshot failed, fallback to defaults: {}", e.getMessage());
            snapshot = new LinkedHashMap<>();
        }
        data.put("model_ratio", new LinkedHashMap<>(
                snapshot.getOrDefault("model_ratio", ModelRatioConstants.DEFAULT_MODEL_RATIO.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)))));
        data.put("completion_ratio", new LinkedHashMap<>(
                snapshot.getOrDefault("completion_ratio", new LinkedHashMap<>())));
        data.put("cache_ratio", new LinkedHashMap<>(
                snapshot.getOrDefault("cache_ratio", CacheRatioConfig.getCacheRatioMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)))));
        data.put("create_cache_ratio", new LinkedHashMap<>(
                snapshot.getOrDefault("create_cache_ratio", CacheRatioConfig.getCreateCacheRatioMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)))));
        data.put("model_price", new LinkedHashMap<>(
                snapshot.getOrDefault("model_price", new LinkedHashMap<>())));

        // ② image_ratio / audio_ratio / audio_completion_ratio：从 OptionService 读取 JSON 配置
        data.put("image_ratio", parseRatioOptionAsMap("ImageRatio"));
        data.put("audio_ratio", parseRatioOptionAsMap("AudioRatio"));
        data.put("audio_completion_ratio", parseRatioOptionAsMap("AudioCompletionRatio"));

        // ③ billing_mode / billing_expr 当前未提供独立读取接口，保持空集合（待 BillingExprConfig 提供后接入）
        data.put("billing_mode", new LinkedHashMap<>());
        data.put("billing_expr", new LinkedHashMap<>());
        return data;
    }

    /** 解析 OptionService 中的 JSON 倍率配置为 Map<String, Object>，失败返回空集合 */
    private Map<String, Object> parseRatioOptionAsMap(String optionKey) {
        try {
            String value = optionService.getValue(optionKey);
            if (value == null || value.isBlank()) return new LinkedHashMap<>();
            Map<String, Object> parsed = Convert.toJSONObject(value);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("parseRatioOptionAsMap {} failed: {}", optionKey, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> buildDifferences(Map<String, Object> localData, List<UpstreamResult> successfulResults) {
        Map<String, Map<String, Object>> differences = new LinkedHashMap<>();
        Set<String> allModels = new LinkedHashSet<>();
        for (String field : pricingSyncFields()) {
            allModels.addAll(valueMap(localData.get(field)).keySet());
        }
        for (UpstreamResult result : successfulResults) {
            for (String field : pricingSyncFields()) {
                allModels.addAll(valueMap(result.data().get(field)).keySet());
            }
        }

        Map<String, Map<String, Boolean>> confidenceMap = new LinkedHashMap<>();
        for (UpstreamResult result : successfulResults) {
            Map<String, Boolean> confidence = new LinkedHashMap<>();
            Map<String, Object> modelRatios = valueMap(result.data().get("model_ratio"));
            Map<String, Object> completionRatios = valueMap(result.data().get("completion_ratio"));
            for (String modelName : allModels) {
                boolean trusted = true;
                if (modelRatios.containsKey(modelName) && completionRatios.containsKey(modelName)) {
                    double modelRatio = toDouble(modelRatios.get(modelName));
                    double completionRatio = toDouble(completionRatios.get(modelName));
                    if (nearlyEqual(modelRatio, 37.5) && nearlyEqual(completionRatio, 1.0)) {
                        trusted = false;
                    }
                }
                confidence.put(modelName, trusted);
            }
            confidenceMap.put(result.name(), confidence);
        }

        for (String modelName : allModels) {
            for (String field : pricingSyncFields()) {
                Object localValue = valueMap(localData.get(field)).get(modelName);
                if (localValue != null) {
                    localValue = normalizeSyncValue(field, localValue);
                }
                Map<String, Object> upstreamValues = new LinkedHashMap<>();
                Map<String, Boolean> confidenceValues = new LinkedHashMap<>();
                boolean hasUpstreamValue = false;
                boolean hasDifference = false;
                for (UpstreamResult result : successfulResults) {
                    Object upstreamValue = valueMap(result.data().get(field)).get(modelName);
                    if (upstreamValue != null) {
                        upstreamValue = normalizeSyncValue(field, upstreamValue);
                        hasUpstreamValue = true;
                        if (localValue != null && !valuesEqual(localValue, upstreamValue)) {
                            hasDifference = true;
                        } else if (valuesEqual(localValue, upstreamValue)) {
                            upstreamValue = "same";
                        }
                    } else if (localValue == null) {
                        upstreamValue = "same";
                    }
                    if (localValue == null && upstreamValue != null && !"same".equals(upstreamValue)) {
                        hasDifference = true;
                    }
                    upstreamValues.put(result.name(), upstreamValue);
                    confidenceValues.put(result.name(), confidenceMap.getOrDefault(result.name(), Map.of()).getOrDefault(modelName, true));
                }
                boolean shouldInclude = localValue != null ? hasDifference : hasUpstreamValue;
                if (!shouldInclude) {
                    continue;
                }
                Map<String, Object> fieldDiff = new LinkedHashMap<>();
                fieldDiff.put("current", localValue);
                fieldDiff.put("upstreams", upstreamValues);
                fieldDiff.put("confidence", confidenceValues);
                differences.computeIfAbsent(modelName, key -> new LinkedHashMap<>()).put(field, fieldDiff);
            }
        }

        Map<String, Boolean> channelHasDiff = new LinkedHashMap<>();
        for (Map<String, Object> ratioMap : differences.values()) {
            for (Object itemObj : ratioMap.values()) {
                if (!(itemObj instanceof Map<?, ?> item)) {
                    continue;
                }
                Object upstreamsObj = item.get("upstreams");
                if (!(upstreamsObj instanceof Map<?, ?> upstreams)) {
                    continue;
                }
                for (Map.Entry<?, ?> entry : upstreams.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null && !"same".equals(value)) {
                        channelHasDiff.put(entry.getKey().toString(), true);
                    }
                }
            }
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> modelEntry : differences.entrySet()) {
            Map<String, Object> modelFields = new LinkedHashMap<>();
            for (Map.Entry<String, Object> fieldEntry : modelEntry.getValue().entrySet()) {
                if (!(fieldEntry.getValue() instanceof Map<?, ?> fieldDiff)) {
                    continue;
                }
                Map<String, Object> upstreams = new LinkedHashMap<>(valueMap(fieldDiff.get("upstreams")));
                Map<String, Boolean> confidence = new LinkedHashMap<>();
                Object confidenceObj = fieldDiff.get("confidence");
                if (confidenceObj instanceof Map<?, ?> confidenceMapObj) {
                    for (Map.Entry<?, ?> confidenceEntry : confidenceMapObj.entrySet()) {
                        confidence.put(confidenceEntry.getKey().toString(), Boolean.TRUE.equals(confidenceEntry.getValue()));
                    }
                }
                upstreams.entrySet().removeIf(entry -> !channelHasDiff.getOrDefault(entry.getKey(), false));
                confidence.entrySet().removeIf(entry -> !channelHasDiff.getOrDefault(entry.getKey(), false));
                boolean allSame = upstreams.isEmpty() || upstreams.values().stream().allMatch("same"::equals);
                if (allSame) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("current", fieldDiff.get("current"));
                normalized.put("upstreams", upstreams);
                normalized.put("confidence", confidence);
                modelFields.put(fieldEntry.getKey(), normalized);
            }
            if (!modelFields.isEmpty()) {
                cleaned.put(modelEntry.getKey(), modelFields);
            }
        }
        return cleaned;
    }

    private Map<String, Object> toSyncableChannel(Channel channel) {
        return syncableChannel(
                channel.getId(),
                channel.getName(),
                getBaseUrl(channel),
                channel.getStatus(),
                channel.getType()
        );
    }

    private Map<String, Object> syncableChannel(Integer id, String name, String baseUrl, Integer status, Integer type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("base_url", baseUrl);
        item.put("status", status);
        item.put("type", type);
        return item;
    }

    private String getBaseUrl(Channel channel) {
        String baseUrl = trimToNull(channel.getBaseUrl());
        if (baseUrl != null) {
            return baseUrl;
        }
        Integer type = channel.getType();
        if (type == null || type < 0 || type >= ChannelConstants.CHANNEL_BASE_URLS.size()) {
            return null;
        }
        String fallback = ChannelConstants.CHANNEL_BASE_URLS.get(type);
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private boolean containsAnyField(JsonNode node, List<String> fields) {
        return fields.stream().anyMatch(node::has);
    }

    private void putIfPresent(JsonNode item, String fieldName, String modelName, Map<String, Object> target) {
        if (item.has(fieldName) && !item.get(fieldName).isNull()) {
            target.put(modelName, item.get(fieldName).asDouble());
        }
    }

    private void putMapIfNotEmpty(Map<String, Object> container, String key, Map<String, Object> value) {
        if (!value.isEmpty()) {
            container.put(key, value);
        }
    }

    /**
     * 从 channel 取第一个可用 API key      * <p>
     * 优先级：channel.keys 第一个非空项 > channel.key 单字段。
     * 复杂的 key 状态/轮询/禁用机制在 Go 端是 per-channel 状态管理，Java 同步场景下简化为取首个。
     */
    private String resolveChannelApiKey(Channel channel) {
        if (channel == null) return null;
        List<String> keys = channel.getKeys();
        if (keys != null) {
            for (String k : keys) {
                if (k != null && !k.isBlank()) return k;
            }
        }
        String single = channel.getKey();
        return (single != null && !single.isBlank()) ? single : null;
    }

    private boolean isModelsDevEndpoint(String rawUrl) {
        return rawUrl != null
                && rawUrl.toLowerCase(Locale.ROOT).startsWith("https://models.dev")
                && rawUrl.replaceAll("/+$", "").endsWith("/api.json");
    }

    private ModelsDevCandidate buildModelsDevCandidate(String provider, JsonNode cost) {
        if (!cost.has("input") || cost.get("input").isNull()) {
            return null;
        }
        double input = cost.path("input").asDouble(Double.NaN);
        if (!isValidNonNegativeCost(input)) {
            return null;
        }
        Double output = null;
        if (cost.has("output") && !cost.get("output").isNull()) {
            output = cost.path("output").asDouble(Double.NaN);
            if (!isValidNonNegativeCost(output)) {
                return null;
            }
        }
        if (input == 0 && output != null && output > 0) {
            return null;
        }
        Double cacheRead = null;
        if (cost.has("cache_read") && !cost.get("cache_read").isNull()) {
            double value = cost.path("cache_read").asDouble(Double.NaN);
            if (isValidNonNegativeCost(value)) {
                cacheRead = value;
            }
        }
        return new ModelsDevCandidate(provider, input, output, cacheRead);
    }

    private boolean shouldReplaceModelsDevCandidate(ModelsDevCandidate current, ModelsDevCandidate next) {
        boolean currentNonZero = current.input() > 0;
        boolean nextNonZero = next.input() > 0;
        if (currentNonZero != nextNonZero) {
            return nextNonZero;
        }
        if (nextNonZero && !nearlyEqual(next.input(), current.input())) {
            return next.input() < current.input();
        }
        return next.provider().compareTo(current.provider()) < 0;
    }

    private boolean isValidNonNegativeCost(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    private List<String> pricingSyncFields() {
        return List.of(
                "model_ratio",
                "completion_ratio",
                "cache_ratio",
                "create_cache_ratio",
                "image_ratio",
                "audio_ratio",
                "audio_completion_ratio",
                "model_price",
                "billing_mode",
                "billing_expr"
        );
    }

    private Map<String, Object> valueMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object normalizeSyncValue(String field, Object value) {
        if (Set.of("model_ratio", "completion_ratio", "cache_ratio", "create_cache_ratio",
                "image_ratio", "audio_ratio", "audio_completion_ratio", "model_price").contains(field)) {
            return toDouble(value);
        }
        return value;
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a instanceof Number || b instanceof Number) {
            return nearlyEqual(toDouble(a), toDouble(b));
        }
        return Objects.equals(a, b);
    }

    private boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) < FLOAT_EPSILON;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return parseDouble(value == null ? null : value.toString(), 0);
    }

    private double parseDouble(String raw, double defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double roundRatioValue(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String trimRightSlash(String value) {
        return value == null ? null : value.replaceAll("/+$", "");
    }

    private record UpstreamSpec(Integer id, String name, String baseUrl, String endpoint, Channel channel) {
        private String displayName() {
            return id != null && id > 0 ? name + "(" + id + ")" : name;
        }
    }

    private record UpstreamResult(String name, Map<String, Object> data, String error) {
    }

    private record ModelsDevCandidate(String provider, double input, Double output, Double cacheRead) {
    }
}
