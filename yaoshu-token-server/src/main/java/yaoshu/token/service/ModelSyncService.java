package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.mapper.AbilityMapper;
import yaoshu.token.mapper.ModelMetaMapper;
import yaoshu.token.mapper.VendorMapper;
import yaoshu.token.pojo.entity.Ability;
import yaoshu.token.pojo.entity.ModelMeta;
import yaoshu.token.pojo.entity.Vendor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 模型上游同步服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelSyncService {

    private static final String DEFAULT_BASE = "https://basellm.github.io/llm-metadata";

    private final ModelMetaMapper modelMetaMapper;
    private final VendorMapper vendorMapper;
    private final AbilityMapper abilityMapper;
    private final UpstreamSyncHttpService upstreamSyncHttpService;

    /**
     * 自注入代理引用，用于跨方法事务调用。
     * <p>missing 创建为单条 mi.Insert（无外层事务）。      * overwrite 阶段每条 model.DB.Transaction 局部事务（任一项失败仅该项回滚）。
     * Java 必须通过代理自调用才能让 @Transactional 生效。
     */
    @Autowired
    @Lazy
    private ModelSyncService self;    public List<String> getMissingModels() {
        List<Object> enabledObjects = abilityMapper.selectObjs(new QueryWrapper<Ability>()
                .select("DISTINCT model")
                .eq("enabled", true));
        List<String> enabledModels = enabledObjects.stream()
                .map(Objects::toString)
                .filter(s -> !s.isBlank())
                .toList();
        if (enabledModels.isEmpty()) {
            return List.of();
        }
        List<ModelMeta> existingModels = modelMetaMapper.selectList(new LambdaQueryWrapper<ModelMeta>()
                .in(ModelMeta::getModelName, enabledModels)
                .select(ModelMeta::getModelName));
        Set<String> existingNames = existingModels.stream()
                .map(ModelMeta::getModelName)
                .collect(Collectors.toSet());
        return enabledModels.stream()
                .filter(name -> !existingNames.contains(name))
                .toList();
    }

    public Map<String, Object> preview(String locale) {
        UpstreamBundle bundle = fetchUpstreamBundle(locale);
        List<String> missingCandidates = getMissingModels().stream()
                .filter(bundle.modelsByName::containsKey)
                .toList();

        List<ModelMeta> locals = bundle.modelsByName.isEmpty()
                ? List.of()
                : modelMetaMapper.selectList(new LambdaQueryWrapper<ModelMeta>()
                .in(ModelMeta::getModelName, bundle.modelsByName.keySet())
                .ne(ModelMeta::getSyncOfficial, 0));

        Set<Integer> vendorIds = locals.stream()
                .map(ModelMeta::getVendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, String> vendorNameById = vendorIds.isEmpty()
                ? Map.of()
                : vendorMapper.selectBatchIds(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName, (a, b) -> a, LinkedHashMap::new));

        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (ModelMeta local : locals) {
            UpstreamModel upstream = bundle.modelsByName.get(local.getModelName());
            if (upstream == null) {
                continue;
            }
            List<Map<String, Object>> fields = new ArrayList<>();
            appendConflict(fields, "description", local.getDescription(), upstream.description());
            appendConflict(fields, "icon", local.getIcon(), upstream.icon());
            appendConflict(fields, "tags", local.getTags(), upstream.tags());
            appendConflict(fields, "vendor", vendorNameById.get(local.getVendorId()), upstream.vendorName());
            if (!Objects.equals(local.getNameRule(), upstream.nameRule())) {
                fields.add(conflictField("name_rule", local.getNameRule(), upstream.nameRule()));
            }
            Integer targetStatus = chooseStatus(upstream.status(), local.getStatus());
            if (!Objects.equals(local.getStatus(), targetStatus)) {
                fields.add(conflictField("status", local.getStatus(), upstream.status()));
            }
            if (!fields.isEmpty()) {
                conflicts.add(new LinkedHashMap<>(Map.of(
                        "model_name", local.getModelName(),
                        "fields", fields
                )));
            }
        }

        return new LinkedHashMap<>(Map.of(
                "missing", missingCandidates,
                "conflicts", conflicts,
                "source", sourceMap(locale, bundle.modelsUrl, bundle.vendorsUrl)
        ));
    }

    /**
     * 模型同步主入口：
     * <ul>
     *   <li>missing 阶段：每条独立 REQUIRES_NEW 事务，单条失败不影响其他模型</li>
     *   <li>overwrite 阶段：每条独立 REQUIRES_NEW 事务，单项失败仅该项回滚</li>
     * </ul>
     * 方法本身不开事务。      */
    public Map<String, Object> sync(String locale, Object overwriteRaw) {
        List<OverwriteField> overwriteFields = parseOverwriteFields(overwriteRaw);
        List<String> missingModels = getMissingModels();
        if (missingModels.isEmpty() && overwriteFields.isEmpty()) {
            String[] urls = getUpstreamUrls(locale);
            return new LinkedHashMap<>(Map.of(
                    "created_models", 0,
                    "created_vendors", 0,
                    "updated_models", 0,
                    "skipped_models", List.of(),
                    "created_list", List.of(),
                    "updated_list", List.of(),
                    "source", sourceMap(locale, urls[0], urls[1])
            ));
        }

        UpstreamBundle bundle = fetchUpstreamBundle(locale);
        Map<String, Integer> vendorIdCache = new LinkedHashMap<>();
        int createdVendors = 0;
        int createdModels = 0;
        int updatedModels = 0;
        List<String> skipped = new ArrayList<>();
        List<String> createdList = new ArrayList<>();
        List<String> updatedList = new ArrayList<>();

        // 单条失败不影响其他模型。Java 用 REQUIRES_NEW 让每条独立事务。
        for (String modelName : missingModels) {
            try {
                CreateOutcome outcome = self.createMissingModelTx(modelName, bundle, vendorIdCache);
                if (outcome == null) {
                    skipped.add(modelName);
                    continue;
                }
                if (outcome.skipped()) {
                    skipped.add(modelName);
                    continue;
                }
                createdVendors += outcome.createdVendor() ? 1 : 0;
                createdModels++;
                createdList.add(modelName);
            } catch (RuntimeException e) {
                // 单条失败仅记录，不阻断后续模型
                log.warn("missing 模型创建失败 modelName={} err={}", modelName, e.getMessage());
                skipped.add(modelName);
            }
        }

        // 单项失败仅该项回滚。Java 用 REQUIRES_NEW 让每条独立事务。
        for (OverwriteField overwriteField : overwriteFields) {
            try {
                UpdateOutcome outcome = self.overwriteModelTx(overwriteField, bundle, vendorIdCache);
                if (outcome == null) {
                    continue;
                }
                createdVendors += outcome.createdVendor() ? 1 : 0;
                if (outcome.updated()) {
                    updatedModels++;
                    updatedList.add(overwriteField.modelName());
                }
            } catch (RuntimeException e) {
                log.warn("overwrite 模型更新失败 modelName={} err={}", overwriteField.modelName(), e.getMessage());
            }
        }

        return new LinkedHashMap<>(Map.of(
                "created_models", createdModels,
                "created_vendors", createdVendors,
                "updated_models", updatedModels,
                "skipped_models", skipped,
                "created_list", createdList,
                "updated_list", updatedList,
                "source", sourceMap(locale, bundle.modelsUrl, bundle.vendorsUrl)
        ));
    }

    /**
     * per-model 局部事务：创建单个缺失模型（含按需创建 vendor）。
     * 失败仅回滚本模型，不影响其他模型。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CreateOutcome createMissingModelTx(String modelName,
                                              UpstreamBundle bundle,
                                              Map<String, Integer> vendorIdCache) {
        UpstreamModel upstream = bundle.modelsByName.get(modelName);
        if (upstream == null) {
            return null;
        }
        ModelMeta existing = modelMetaMapper.selectOne(new LambdaQueryWrapper<ModelMeta>()
                .eq(ModelMeta::getModelName, modelName)
                .last("LIMIT 1"));
        if (existing != null && Objects.equals(existing.getSyncOfficial(), 0)) {
            return new CreateOutcome(true, false);
        }
        VendorResolveResult vendorResolve = ensureVendorId(upstream.vendorName(), bundle.vendorsByName, vendorIdCache);

        ModelMeta meta = new ModelMeta();
        meta.setModelName(modelName);
        meta.setDescription(upstream.description());
        meta.setIcon(upstream.icon());
        meta.setTags(upstream.tags());
        meta.setVendorId(vendorResolve.vendorId());
        meta.setStatus(chooseStatus(upstream.status(), 1));
        meta.setNameRule(upstream.nameRule());
        long now = nowSeconds();
        meta.setCreatedTime(now);
        meta.setUpdatedTime(now);
        modelMetaMapper.insert(meta);
        return new CreateOutcome(false, vendorResolve.created());
    }

    /**
     * per-model 局部事务：按 OverwriteField 更新单个模型字段（含按需创建 vendor）。
     * 失败仅回滚本模型，不影响其他模型。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public UpdateOutcome overwriteModelTx(OverwriteField overwriteField,
                                          UpstreamBundle bundle,
                                          Map<String, Integer> vendorIdCache) {
        UpstreamModel upstream = bundle.modelsByName.get(overwriteField.modelName());
        if (upstream == null) {
            return null;
        }
        ModelMeta local = modelMetaMapper.selectOne(new LambdaQueryWrapper<ModelMeta>()
                .eq(ModelMeta::getModelName, overwriteField.modelName())
                .last("LIMIT 1"));
        if (local == null || Objects.equals(local.getSyncOfficial(), 0)) {
            return null;
        }
        VendorResolveResult vendorResolve = ensureVendorId(upstream.vendorName(), bundle.vendorsByName, vendorIdCache);

        boolean needUpdate = false;
        if (overwriteField.contains("description")) {
            local.setDescription(upstream.description());
            needUpdate = true;
        }
        if (overwriteField.contains("icon")) {
            local.setIcon(upstream.icon());
            needUpdate = true;
        }
        if (overwriteField.contains("tags")) {
            local.setTags(upstream.tags());
            needUpdate = true;
        }
        if (overwriteField.contains("vendor")) {
            local.setVendorId(vendorResolve.vendorId());
            needUpdate = true;
        }
        if (overwriteField.contains("name_rule")) {
            local.setNameRule(upstream.nameRule());
            needUpdate = true;
        }
        if (overwriteField.contains("status")) {
            local.setStatus(chooseStatus(upstream.status(), local.getStatus()));
            needUpdate = true;
        }
        if (!needUpdate) {
            return new UpdateOutcome(false, vendorResolve.created());
        }
        local.setUpdatedTime(nowSeconds());
        modelMetaMapper.updateById(local);
        return new UpdateOutcome(true, vendorResolve.created());
    }

    private UpstreamBundle fetchUpstreamBundle(String locale) {
        String[] urls = getUpstreamUrls(locale);
        int timeoutSec = readIntEnv("SYNC_HTTP_TIMEOUT_SECONDS", 15);
        // models 拉取失败必须拦截
        JsonNode modelsNode;
        try {
            modelsNode = upstreamSyncHttpService.fetchJson(urls[0], timeoutSec, 10);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18nUtils.get("model.get_upstream_failed", e.getMessage()), e);
        }
        // vendors 拉取失败不拦截：Go 用 `_ = fetchJSON(ctx, vendorsURL, &vendorsEnv)` 静默吞错，
        // Java 用空 map 继续（vendor 信息缺失则后续走默认 vendor 0）
        JsonNode vendorsNode = null;
        try {
            vendorsNode = upstreamSyncHttpService.fetchJson(urls[1], timeoutSec, 10);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("vendors.json 拉取失败 locale={} url={} err={}（继续走默认 vendor）",
                    locale, urls[1], e.getMessage());
        }

        Map<String, UpstreamModel> modelsByName = new LinkedHashMap<>();
        for (JsonNode item : upstreamSyncHttpService.extractDataArrayOrRoot(modelsNode)) {
            UpstreamModel model = Convert.toJavaBean(item, UpstreamModel.class);
            if (model.modelName() != null && !model.modelName().isBlank()) {
                modelsByName.put(model.modelName(), model);
            }
        }
        Map<String, UpstreamVendor> vendorsByName = new LinkedHashMap<>();
        if (vendorsNode != null) {
            for (JsonNode item : upstreamSyncHttpService.extractDataArrayOrRoot(vendorsNode)) {
                UpstreamVendor vendor = Convert.toJavaBean(item, UpstreamVendor.class);
                if (vendor.name() != null && !vendor.name().isBlank()) {
                    vendorsByName.put(vendor.name(), vendor);
                }
            }
        }
        return new UpstreamBundle(urls[0], urls[1], modelsByName, vendorsByName);
    }

    private VendorResolveResult ensureVendorId(String vendorName,
                                               Map<String, UpstreamVendor> vendorsByName,
                                               Map<String, Integer> vendorIdCache) {
        if (vendorName == null || vendorName.isBlank()) {
            return new VendorResolveResult(0, false);
        }
        Integer cached = vendorIdCache.get(vendorName);
        if (cached != null) {
            return new VendorResolveResult(cached, false);
        }
        Vendor existing = vendorMapper.selectOne(new LambdaQueryWrapper<Vendor>()
                .eq(Vendor::getName, vendorName)
                .last("LIMIT 1"));
        if (existing != null) {
            vendorIdCache.put(vendorName, existing.getId());
            return new VendorResolveResult(existing.getId(), false);
        }

        UpstreamVendor upstreamVendor = vendorsByName.get(vendorName);
        Vendor vendor = new Vendor();
        vendor.setName(vendorName);
        vendor.setDescription(upstreamVendor != null ? upstreamVendor.description() : null);
        vendor.setIcon(upstreamVendor != null ? upstreamVendor.icon() : null);
        vendor.setStatus(chooseStatus(upstreamVendor != null ? upstreamVendor.status() : null, 1));
        long now = nowSeconds();
        vendor.setCreatedTime(now);
        vendor.setUpdatedTime(now);
        vendorMapper.insert(vendor);
        vendorIdCache.put(vendorName, vendor.getId());
        return new VendorResolveResult(vendor.getId(), true);
    }

    private List<OverwriteField> parseOverwriteFields(Object overwriteRaw) {
        if (!(overwriteRaw instanceof List<?> rawList)) {
            return List.of();
        }
        List<OverwriteField> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String modelName = trimToNull(rawMap.get("model_name"));
            if (modelName == null) {
                continue;
            }
            Set<String> fields = new LinkedHashSet<>();
            Object fieldsObj = rawMap.get("fields");
            if (fieldsObj instanceof List<?> fieldList) {
                for (Object field : fieldList) {
                    String normalized = trimToNull(field);
                    if (normalized != null) {
                        fields.add(normalized.toLowerCase(Locale.ROOT));
                    }
                }
            }
            result.add(new OverwriteField(modelName, fields));
        }
        return result;
    }

    private String[] getUpstreamUrls(String locale) {
        String base = System.getenv().getOrDefault("SYNC_UPSTREAM_BASE", DEFAULT_BASE).replaceAll("/+$", "");
        String normalized = normalizeLocale(locale);
        if (normalized != null) {
            return new String[]{
                    base + "/api/i18n/" + normalized + "/newapi/models.json",
                    base + "/api/i18n/" + normalized + "/newapi/vendors.json"
            };
        }
        return new String[]{
                base + "/api/newapi/models.json",
                base + "/api/newapi/vendors.json"
        };
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        return switch (locale.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> "en";
            case "zh-cn" -> "zh-CN";
            case "zh-tw" -> "zh-TW";
            case "ja" -> "ja";
            default -> null;
        };
    }

    private Map<String, Object> sourceMap(String locale, String modelsUrl, String vendorsUrl) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("locale", locale);
        source.put("models_url", modelsUrl);
        source.put("vendors_url", vendorsUrl);
        return source;
    }

    private void appendConflict(List<Map<String, Object>> fields, String field, Object local, Object upstream) {
        if (!Objects.equals(blankToNull(local), blankToNull(upstream))) {
            fields.add(conflictField(field, local, upstream));
        }
    }

    private Map<String, Object> conflictField(String field, Object local, Object upstream) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("local", local);
        result.put("upstream", upstream);
        return result;
    }

    private Object blankToNull(Object value) {
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        return value;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer chooseStatus(Integer primary, Integer fallback) {
        if (primary != null && primary != 0) {
            return primary;
        }
        if (fallback != null && fallback != 0) {
            return fallback;
        }
        return 1;
    }

    private int readIntEnv(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long nowSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private record UpstreamBundle(String modelsUrl,
                                  String vendorsUrl,
                                  Map<String, UpstreamModel> modelsByName,
                                  Map<String, UpstreamVendor> vendorsByName) {
    }

    private record UpstreamModel(String description,
                                 String endpoints,
                                 String icon,
                                 String modelName,
                                 Integer nameRule,
                                 Integer status,
                                 String tags,
                                 String vendorName) {
    }

    private record UpstreamVendor(String description, String icon, String name, Integer status) {
    }

    private record VendorResolveResult(Integer vendorId, boolean created) {
    }

    /**
     * missing 模型创建结果。
     * @param skipped 是否被跳过（如本地存在且 syncOfficial=0）
     * @param createdVendor 是否同时新建了 vendor
     */
    public record CreateOutcome(boolean skipped, boolean createdVendor) {
    }

    /**
     * overwrite 模型更新结果。
     * @param updated 是否实际执行了 update
     * @param createdVendor 是否同时新建了 vendor
     */
    public record UpdateOutcome(boolean updated, boolean createdVendor) {
    }

    private record OverwriteField(String modelName, Set<String> fields) {
        private boolean contains(String field) {
            return fields.contains(field);
        }
    }
}
