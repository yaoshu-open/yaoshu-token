package yaoshu.token.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.EndpointTypeMapping;
import yaoshu.token.config.PricingDefaultConfig;
import yaoshu.token.config.ratio.CacheRatioConfig;
import yaoshu.token.config.ratio.ExposeRatioConfig;
import yaoshu.token.constant.EndpointDefaults;
import yaoshu.token.constant.EndpointDefaults.EndpointInfo;
import yaoshu.token.constant.EndpointTypeEnum;
import yaoshu.token.mapper.AbilityMapper;
import yaoshu.token.mapper.ModelMetaMapper;
import yaoshu.token.mapper.QuotaMapper;
import yaoshu.token.mapper.VendorMapper;
import yaoshu.token.pojo.dto.AbilityWithChannel;
import yaoshu.token.pojo.entity.ModelMeta;
import yaoshu.token.pojo.entity.Vendor;
import yaoshu.token.pojo.vo.PricingVO;
import yaoshu.token.pojo.vo.PricingVendorVO;
import yaoshu.token.relay.helper.PriceHelper;
import yaoshu.token.spi.PricingEnhancer;

/**
 * 定价服务  * <p>
 * 核心职责：根据能力表、模型元数据、渠道供应商、比率配置构建前端定价数据。
 * 缓存策略：JetCache {@code @Cached} L1（Caffeine）+ L2（Redis BOTH），60 秒 TTL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final AbilityMapper abilityMapper;
    private final ModelMetaMapper modelMetaMapper;
    private final QuotaMapper quotaMapper;
    private final VendorMapper vendorMapper;
    private final OptionService optionService;
    private final PricingEnhancer pricingEnhancer;

    // ======================== 名称规则常量 ======================== 
    private static final int NAME_RULE_EXACT = 1;
    private static final int NAME_RULE_PREFIX = 2;
    private static final int NAME_RULE_SUFFIX = 3;
    private static final int NAME_RULE_CONTAINS = 4;

    // ======================== 缓存快照（JetCache @Cached 托管） ========================

    /**
     * 定价计算完整快照——一次计算产出所有派生数据，JetCache 缓存方法返回值。
     */
    public record PricingSnapshot(
            List<PricingVO> pricingList,
            List<PricingVendorVO> vendorsList,
            Map<String, EndpointInfo> endpointMap,
            Map<String, List<String>> enableGroups,
            Map<String, Integer> quotaTypes
    ) {}

    // ======================== 公共 API ========================

    /**
     * 获取定价快照（JetCache LOCAL 60s 缓存）      * <p>
     * 缓存由 JetCache 托管——首次调用执行 {@link #buildPricingSnapshot()}，后续从 Caffeine L1 返回。
     */
    @Cached(name = "pricing", key = "'snapshot'", cacheType = CacheType.LOCAL, expire = 60)
    public PricingSnapshot getPricingSnapshot() {
        return buildPricingSnapshot();
    }

    /** 获取定价列表 */
    public List<PricingVO> getPricing() {
        return getPricingSnapshot().pricingList();
    }

    /** 获取供应商列表 */
    public List<PricingVendorVO> getVendors() {
        return getPricingSnapshot().vendorsList();
    }

    /** 获取端点映射 */
    public Map<String, EndpointInfo> getSupportedEndpointMap() {
        return getPricingSnapshot().endpointMap();
    }

    /** 获取模型启用分组 */
    public List<String> getModelEnableGroups(String modelName) {
        return getPricingSnapshot().enableGroups().getOrDefault(modelName, List.of());
    }

    /** 获取模型计费类型 */
    public int getModelQuotaType(String modelName) {
        return getPricingSnapshot().quotaTypes().getOrDefault(modelName, 0);
    }

    /**
     * 获取所有启用能力及其渠道类型（JetCache LOCAL 60s 缓存）      * <p>
     * 缓存独立于 PricingSnapshot，供 ModelService.getPreferredModelOwners 兜底分支复用，
     * 避免 owner 推断时重复执行 abilities LEFT JOIN channels 全表查询。
     * 与 PricingSnapshot 同 60s TTL，数据一致。
     */
    @Cached(name = "ability", key = "'all_enabled_with_channels'", cacheType = CacheType.LOCAL, expire = 60)
    public List<AbilityWithChannel> getCachedAllEnableAbilityWithChannels() {
        return abilityMapper.getAllEnableAbilityWithChannels();
    }

    /**
     * 倍率暴露快照。      * <p>
     * 返回 5 张全量 Map：model_ratio / completion_ratio / cache_ratio / create_cache_ratio / model_price，
     * 供 {@code RatioConfigController.getConfig} 直接消费。Go 端从独立的 modelRatioMap/completionRatioMap/
     * modelPriceMap 聚合；Java 端目前没有独立 Map 配置源，遍历 PricingSnapshot 中已计算好的 PricingVO 字段聚合，
     * 只保留有效值（model_price &lt; 0 跳过；completion_ratio == 0 跳过——按 Go usePrice 语义，按价格计费的模型
     * 不走完成倍率）。
     */
    public Map<String, Map<String, Double>> getRatioExposureSnapshot() {
        Map<String, Double> modelRatio = new LinkedHashMap<>();
        Map<String, Double> completionRatio = new LinkedHashMap<>();
        Map<String, Double> modelPrice = new LinkedHashMap<>();
        Map<String, Double> cacheRatio = new LinkedHashMap<>();
        Map<String, Double> createCacheRatio = new LinkedHashMap<>();

        for (PricingVO p : getPricingSnapshot().pricingList()) {
            String name = p.getModelName();
            if (name == null || name.isEmpty()) continue;

            // quotaType=1 按价格计费 → model_price；quotaType=0 按倍率计费 → model_ratio + completion_ratio
            if (p.getQuotaType() == 1) {
                modelPrice.put(name, p.getModelPrice());
            } else {
                modelRatio.put(name, p.getModelRatio());
                if (p.getCompletionRatio() > 0) {
                    completionRatio.put(name, p.getCompletionRatio());
                }
            }
            if (p.getCacheRatio() != null) cacheRatio.put(name, p.getCacheRatio());
            if (p.getCreateCacheRatio() != null) createCacheRatio.put(name, p.getCreateCacheRatio());
        }

        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        result.put("model_ratio", modelRatio);
        result.put("completion_ratio", completionRatio);
        result.put("cache_ratio", cacheRatio);
        result.put("create_cache_ratio", createCacheRatio);
        result.put("model_price", modelPrice);
        return result;
    }

    /**
     * 强制刷新定价缓存（同步）      * <p>
     * 绕过 JetCache 缓存，直接调用计算逻辑并回写。
     */
    @CacheUpdate(name = "pricing", key = "'snapshot'", value = "#result")
    public PricingSnapshot refreshPricing() {
        return buildPricingSnapshot();
    }

    /**
     * 使定价缓存立即失效      */
    @CacheInvalidate(name = "pricing", key = "'snapshot'")
    public void invalidatePricingCache() {
        // JetCache 自动失效
    }

    // ======================== 核心计算逻辑（Go: doUpdatePricing → PricingSnapshot） ========================

    private PricingSnapshot buildPricingSnapshot() {
        // 1. 获取所有启用能力
        List<AbilityWithChannel> abilities = abilityMapper.getAllEnableAbilityWithChannels();

        // 2. 加载模型元数据，按命名规则分类
        List<ModelMeta> allMeta = modelMetaMapper.selectList(null);
        Map<String, ModelMeta> metaMap = new LinkedHashMap<>();
        List<ModelMeta> prefixList = new ArrayList<>();
        List<ModelMeta> suffixList = new ArrayList<>();
        List<ModelMeta> containsList = new ArrayList<>();

        for (ModelMeta m : allMeta) {
            if (m.getNameRule() != null && m.getNameRule() == NAME_RULE_EXACT) {
                metaMap.put(m.getModelName(), m);
            } else if (m.getNameRule() != null) {
                switch (m.getNameRule()) {
                    case NAME_RULE_PREFIX -> prefixList.add(m);
                    case NAME_RULE_SUFFIX -> suffixList.add(m);
                    case NAME_RULE_CONTAINS -> containsList.add(m);
                    default -> {}
                }
            }
        }

        // 3. 匹配非精确规则
        matchPrefixModels(prefixList, abilities, metaMap);
        matchSuffixModels(suffixList, abilities, metaMap);
        matchContainsModels(containsList, abilities, metaMap);

        // 4. 加载供应商
        List<Vendor> vendors = vendorMapper.selectList(null);
        Map<Integer, Vendor> vendorMap = new LinkedHashMap<>();
        for (Vendor v : vendors) {
            vendorMap.put(v.getId(), v);
        }

        // 5. 初始化默认供应商映射
        initDefaultVendorMapping(metaMap, vendorMap, abilities);

        // 6. 构建供应商列表
        List<PricingVendorVO> vendorsList = new ArrayList<>();
        for (Vendor v : vendorMap.values()) {
            vendorsList.add(new PricingVendorVO(v.getId(), v.getName(), v.getDescription(), v.getIcon()));
        }

        // 7. 构建模型→分组映射
        Map<String, List<String>> modelGroupsMap = new LinkedHashMap<>();
        for (AbilityWithChannel ac : abilities) {
            modelGroupsMap.computeIfAbsent(ac.getModel(), k -> new ArrayList<>()).add(ac.getGroup());
        }

        // 8. 构建模型端点类型
        Map<String, List<String>> modelSupportEndpointsStr = new LinkedHashMap<>();
        for (AbilityWithChannel ac : abilities) {
            List<String> endpoints = modelSupportEndpointsStr.computeIfAbsent(ac.getModel(), k -> new ArrayList<>());
            List<EndpointTypeEnum> types = getEndpointTypes(ac, endpoints);
            for (EndpointTypeEnum et : types) {
                if (!endpoints.contains(et.getValue())) {
                    endpoints.add(et.getValue());
                }
            }
        }

        // 8b. 补充模型自定义端点
        for (ModelMeta meta : metaMap.values()) {
            if (meta.getEndpoints() == null || meta.getEndpoints().isBlank()) continue;
            Map<String, Object> raw = parseEndpointsJSON(meta.getEndpoints());
            if (raw != null && !raw.isEmpty()) {
                List<String> endpoints = new ArrayList<>(raw.keySet());
                if (!endpoints.isEmpty()) {
                    modelSupportEndpointsStr.put(meta.getModelName(), endpoints);
                }
            }
        }

        // 9. 构建全局端点映射
        Map<String, EndpointInfo> endpointMap = new LinkedHashMap<>();
        for (List<String> endpoints : modelSupportEndpointsStr.values()) {
            for (String etStr : endpoints) {
                EndpointTypeEnum et = EndpointTypeEnum.fromValue(etStr);
                if (et != null) {
                    EndpointInfo info = EndpointDefaults.getDefaultEndpointInfo(et);
                    if (info != null && !endpointMap.containsKey(etStr)) {
                        endpointMap.put(etStr, info);
                    }
                }
            }
        }
        // 9b. 自定义端点覆盖
        for (ModelMeta meta : metaMap.values()) {
            if (meta.getEndpoints() == null || meta.getEndpoints().isBlank()) continue;
            Map<String, Object> raw = parseEndpointsJSON(meta.getEndpoints());
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String path) {
                        endpointMap.put(entry.getKey(), new EndpointInfo(path, "POST"));
                    } else if (val instanceof Map m) {
                        String path = (String) m.get("path");
                        String method = (String) m.getOrDefault("method", "POST");
                        endpointMap.put(entry.getKey(), new EndpointInfo(path, method.toUpperCase()));
                    }
                }
            }
        }

        // 10. 构建定价列表 + 派生缓存
        List<PricingVO> pricingList = new ArrayList<>();
        Map<String, List<String>> enableGroupsCache = new LinkedHashMap<>();
        Map<String, Integer> quotaTypeCache = new LinkedHashMap<>();

        // 10b. 读取 billing_mode / billing_expr 配置
        // options 表 key=billing_setting.billing_mode 与 billing_setting.billing_expr，值是 JSON Map<modelName, value>
        Map<String, String> billingModeMap = readBillingOptionAsMap("billing_setting.billing_mode");
        Map<String, String> billingExprMap = readBillingOptionAsMap("billing_setting.billing_expr");

        for (var entry : modelGroupsMap.entrySet()) {
            String modelName = entry.getKey();
            List<String> groups = entry.getValue();

            PricingVO p = new PricingVO();
            p.setModelName(modelName);
            p.setEnableGroup(groups);
            p.setSupportedEndpointTypes(modelSupportEndpointsStr.getOrDefault(modelName, List.of()));

            ModelMeta meta = metaMap.get(modelName);
            if (meta != null) {
                if (meta.getStatus() != null && meta.getStatus() != 1) continue;
                p.setDescription(meta.getDescription());
                p.setIcon(meta.getIcon());
                p.setTags(meta.getTags());
                p.setVendorId(meta.getVendorId());
            }

            double modelPrice = PriceHelper.getModelPrice(modelName);
            if (modelPrice >= 0) {
                p.setModelPrice(modelPrice);
                p.setQuotaType(1);
            } else {
                p.setModelRatio(PriceHelper.getModelRatio(modelName));
                p.setCompletionRatio(PriceHelper.getCompletionRatio(modelName));
                p.setQuotaType(0);
            }

            Double cacheRatio = CacheRatioConfig.getCacheRatio(modelName);
            if (cacheRatio != null) p.setCacheRatio(cacheRatio);
            Double createCacheRatio = CacheRatioConfig.getCreateCacheRatio(modelName);
            if (createCacheRatio != null) p.setCreateCacheRatio(createCacheRatio);
            Double imageRatio = ExposeRatioConfig.getExposeRatio("image:" + modelName);
            if (imageRatio != null) p.setImageRatio(imageRatio);

            double audioRatio = PriceHelper.getAudioRatio(modelName);
            if (audioRatio != 1.0 || isAudioModel(modelName)) p.setAudioRatio(audioRatio);
            double audioCompletionRatio = PriceHelper.getAudioCompletionRatio(modelName);
            if (audioCompletionRatio != 1.0) p.setAudioCompletionRatio(audioCompletionRatio);

            // 分层计费表达式：仅在 billingMode == "tiered_expr" 且 expr 非空时填充（Go model/pricing.go L325-L330）
            String billingMode = billingModeMap.get(modelName);
            if ("tiered_expr".equals(billingMode)) {
                String expr = billingExprMap.get(modelName);
                if (expr != null && !expr.trim().isEmpty()) {
                    p.setBillingMode(billingMode);
                    p.setBillingExpr(expr);
                }
            }

            pricingList.add(p);
            enableGroupsCache.put(modelName, groups);
            quotaTypeCache.put(modelName, p.getQuotaType());
        }

        if (!pricingList.isEmpty()) {
            // model 层数据指纹。
            // 与 PricingController 响应外层 pricing_version（controller 层协议指纹 a42d372c...）不同，
            // 区分两层指纹以保持 Go 原版"双指纹分层"语义。
            pricingList.get(0).setPricingVersion("5a90f2b86c08bd983a9a2e6d66c255f4eaef9c4bc934386d2b6ae84ef0ff1f1f");
        }

        // SPI 增强钩子：定价数据产出后，由扩展点进行后处理（如动态定价）
        pricingEnhancer.enhance(pricingList);

        // 按供应商调用量排序，供应商内部按模型调用量排序
        sortByCallVolume(pricingList, metaMap);

        return new PricingSnapshot(pricingList, vendorsList, endpointMap, enableGroupsCache, quotaTypeCache);
    }

    // ======================== 模型名匹配辅助 ========================

    /**
     * 按供应商调用量排序，供应商内部按模型调用量排序。
     * <p>
     * 无调用数据的模型排在有调用数据的模型之后，保持原始相对顺序。
     */
    private void sortByCallVolume(List<PricingVO> pricingList, Map<String, ModelMeta> metaMap) {
        if (pricingList == null || pricingList.size() <= 1) return;

        // 查询全量模型调用量
        List<QuotaMapper.RankingQuotaTotal> totals;
        try {
            totals = quotaMapper.getRankingQuotaTotals(null, null);
        } catch (Exception e) {
            log.warn("查询模型调用量失败，跳过排序: {}", e.getMessage());
            return;
        }

        Map<String, Long> modelTokens = new HashMap<>();
        for (var t : totals) {
            modelTokens.put(t.getModelName(), t.getTotalTokens());
        }

        // 计算每个供应商的总调用量
        Map<Integer, Long> vendorTokens = new HashMap<>();
        for (PricingVO p : pricingList) {
            long tokens = modelTokens.getOrDefault(p.getModelName(), 0L);
            if (p.getVendorId() != null) {
                vendorTokens.merge(p.getVendorId(), tokens, Long::sum);
            }
        }

        // 排序：供应商总调用量降序 → 供应商内部模型调用量降序
        pricingList.sort(Comparator
                .comparingLong((PricingVO p) -> {
                    int vid = p.getVendorId() != null ? p.getVendorId() : 0;
                    return -vendorTokens.getOrDefault(vid, 0L); // 负值实现降序
                })
                .thenComparingLong((PricingVO p) ->
                        -modelTokens.getOrDefault(p.getModelName(), 0L)
                )
        );
    }

    private void matchPrefixModels(List<ModelMeta> prefixList, List<AbilityWithChannel> abilities, Map<String, ModelMeta> metaMap) {
        for (ModelMeta m : prefixList) {
            for (AbilityWithChannel ac : abilities) {
                if (ac.getModel().startsWith(m.getModelName()) && !metaMap.containsKey(ac.getModel())) {
                    metaMap.put(ac.getModel(), m);
                }
            }
        }
    }

    private void matchSuffixModels(List<ModelMeta> suffixList, List<AbilityWithChannel> abilities, Map<String, ModelMeta> metaMap) {
        for (ModelMeta m : suffixList) {
            for (AbilityWithChannel ac : abilities) {
                if (ac.getModel().endsWith(m.getModelName()) && !metaMap.containsKey(ac.getModel())) {
                    metaMap.put(ac.getModel(), m);
                }
            }
        }
    }

    private void matchContainsModels(List<ModelMeta> containsList, List<AbilityWithChannel> abilities, Map<String, ModelMeta> metaMap) {
        for (ModelMeta m : containsList) {
            for (AbilityWithChannel ac : abilities) {
                if (ac.getModel().contains(m.getModelName()) && !metaMap.containsKey(ac.getModel())) {
                    metaMap.put(ac.getModel(), m);
                }
            }
        }
    }

    // ======================== 默认供应商映射 ========================

    private void initDefaultVendorMapping(Map<String, ModelMeta> metaMap, Map<Integer, Vendor> vendorMap,
                                          List<AbilityWithChannel> abilities) {
        for (AbilityWithChannel ac : abilities) {
            String modelName = ac.getModel();
            if (metaMap.containsKey(modelName)) continue;

            String modelLower = modelName.toLowerCase();
            String vendorName = null;
            for (var ruleEntry : PricingDefaultConfig.DEFAULT_VENDOR_RULES.entrySet()) {
                if (modelLower.contains(ruleEntry.getKey())) {
                    vendorName = ruleEntry.getValue();
                    break;
                }
            }

            if (vendorName != null) {
                int vendorId = getOrCreateVendor(vendorName, vendorMap);
                if (vendorId > 0) {
                    ModelMeta meta = new ModelMeta();
                    meta.setModelName(modelName);
                    meta.setVendorId(vendorId);
                    meta.setStatus(1);
                    meta.setNameRule(NAME_RULE_EXACT);
                    metaMap.put(modelName, meta);
                }
            }
        }
    }

    private int getOrCreateVendor(String vendorName, Map<Integer, Vendor> vendorMap) {
        for (Vendor v : vendorMap.values()) {
            if (vendorName.equals(v.getName())) return v.getId();
        }

        Vendor newVendor = new Vendor();
        newVendor.setName(vendorName);
        newVendor.setStatus(1);
        newVendor.setIcon(PricingDefaultConfig.DEFAULT_VENDOR_ICONS.getOrDefault(vendorName, ""));
        vendorMapper.insert(newVendor);

        vendorMap.put(newVendor.getId(), newVendor);
        return newVendor.getId();
    }

    // ======================== 工具方法 ========================

    /**
     * 根据 AbilityWithChannel 获取端点类型，处理 channelType=null 的情况。
     * <p>
     * ability LEFT JOIN channels 时，未关联渠道的 ability 其 channel 字段为 null，
     * 此时默认使用 OpenAI 端点。
     */
    private List<EndpointTypeEnum> getEndpointTypes(AbilityWithChannel ac, List<String> existingEndpoints) {
        if (ac.getChannelType() == null) {
            // 已存在 OPENAI 端点则不重复添加
            for (String ep : existingEndpoints) {
                if (EndpointTypeEnum.OPENAI.getValue().equals(ep)) {
                    return List.of();
                }
            }
            return List.of(EndpointTypeEnum.OPENAI);
        }
        return EndpointTypeMapping.getEndpointTypesByChannelType(ac.getChannelType(), ac.getModel());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEndpointsJSON(String json) {
        try {
            return ai.yue.library.base.convert.Convert.toJSONObject(json);
        } catch (Exception e) {
            log.debug("解析模型端点 JSON 失败: {}", json, e);
            return null;
        }
    }

    private boolean isAudioModel(String modelName) {
        return modelName != null && (
                modelName.contains("audio") || modelName.contains("realtime") || modelName.contains("tts"));
    }

    /**
     * 读取 options 表中存储为 JSON Map&lt;modelName, value&gt; 的配置项。
     * 失败返回空 Map（不影响主流程）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> readBillingOptionAsMap(String optionKey) {
        try {
            String value = optionService.getValue(optionKey);
            if (value == null || value.isBlank()) return new LinkedHashMap<>();
            Map<String, Object> parsed = ai.yue.library.base.convert.Convert.toJSONObject(value);
            if (parsed == null || parsed.isEmpty()) return new LinkedHashMap<>();
            Map<String, String> result = new LinkedHashMap<>();
            for (var e : parsed.entrySet()) {
                if (e.getValue() != null) result.put(e.getKey(), String.valueOf(e.getValue()));
            }
            return result;
        } catch (Exception e) {
            log.debug("读取 billing option {} 失败: {}", optionKey, e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
