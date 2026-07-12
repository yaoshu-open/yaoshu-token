package yaoshu.token.service;

import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.ModelRatioConstants;
import yaoshu.token.mapper.AbilityMapper;
import yaoshu.token.pojo.dto.AbilityWithChannel;
import yaoshu.token.pojo.entity.Ability;
import yaoshu.token.pojo.entity.ModelMeta;
import yaoshu.token.pojo.vo.PricingVendorVO;
import yaoshu.token.pojo.vo.PricingVO;
import yaoshu.token.spi.ModelListFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型元数据服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final yaoshu.token.mapper.ModelMetaMapper modelMetaMapper;
    private final AbilityMapper abilityMapper;
    private final PricingService pricingService;
    private final ModelListFilter modelListFilter;

    public ModelMeta getById(Integer id) {
        return modelMetaMapper.selectById(id);
    }

    /**
     * 获取所有模型（分页）
     */
    public List<ModelMeta> getAll() {
        PageHelper.startPage(ServletUtils.getRequest());
        return modelMetaMapper.selectList(
                new LambdaQueryWrapper<ModelMeta>()
                        .orderByDesc(ModelMeta::getId)
        );
    }

    /**
     * 搜索模型
     */
    public List<ModelMeta> search(String keyword) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<ModelMeta> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(ModelMeta::getModelName, keyword);
        }
        qw.orderByDesc(ModelMeta::getId);
        return modelMetaMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelMeta create(ModelMeta model) {
        long now = System.currentTimeMillis() / 1000;
        model.setCreatedTime(now);
        model.setUpdatedTime(now);
        modelMetaMapper.insert(model);
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean update(ModelMeta model) {
        model.setUpdatedTime(System.currentTimeMillis() / 1000);
        return modelMetaMapper.updateById(model) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Integer id) {
        return modelMetaMapper.deleteById(id) > 0;
    }

    /**
     * 获取分组已启用的模型名列表      */
    public List<String> getEnabledModelNamesByGroup(String group) {
        return abilityMapper.selectList(
                        new LambdaQueryWrapper<Ability>()
                                .eq(Ability::getGroup, group)
                                .eq(Ability::getEnabled, true)
                                .select(Ability::getModel)
                ).stream()
                .map(Ability::getModel)
                .distinct()
                .toList();
    }

    /**
     * 获取所有已启用的模型名（去重）      * <p>
     * SQL 等价：SELECT DISTINCT model FROM abilities WHERE enabled = true
     */
    public List<String> getEnabledModelNames() {
        return abilityMapper.selectList(
                        new LambdaQueryWrapper<Ability>()
                                .eq(Ability::getEnabled, true)
                                .select(Ability::getModel)
                ).stream()
                .map(Ability::getModel)
                .distinct()
                .toList();
    }

    /**
     * 获取 OpenAI 兼容格式的全量模型清单      * <p>
     * Java 端基于 PricingService 快照 + Vendor 表生成。每个模型条目结构：
     * <pre>{id, object:"model", created:1626777600, owned_by:&lt;vendorName 或 "custom"&gt;, supported_endpoint_types:[...]}</pre>
     * created 1626777600 是 OpenAI 协议占位常量（与 Go 原版一致）。
     */
    public List<Map<String, Object>> listAllOpenAIModels() {
        List<PricingVO> pricing = pricingService.getPricing();
        List<PricingVendorVO> vendors = pricingService.getVendors();

        // 构建 vendorId → vendorName 映射
        Map<Integer, String> vendorNames = new LinkedHashMap<>();
        for (PricingVendorVO v : vendors) {
            vendorNames.put(v.getId(), v.getName());
        }

        List<Map<String, Object>> models = new ArrayList<>();
        for (PricingVO p : pricing) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", p.getModelName());
            entry.put("object", "model");
            entry.put("created", 1626777600);
            String owner = p.getVendorId() != null
                    ? vendorNames.getOrDefault(p.getVendorId(), "custom")
                    : "custom";
            entry.put("owned_by", owner);
            entry.put("supported_endpoint_types", p.getSupportedEndpointTypes());
            models.add(entry);
        }
        return modelListFilter.filter(models);
    }

    /**
     * Gemini 格式模型列表（/v1beta/models）
     * <p>
     * 构建 {@code {name, displayName}} 列表，末尾经过 ModelListFilter 精选白名单过滤。
     */
    public List<Map<String, Object>> listAllGeminiModels() {
        List<PricingVO> pricing = pricingService.getPricing();
        List<Map<String, Object>> models = new ArrayList<>();
        for (PricingVO p : pricing) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", p.getModelName());
            entry.put("name", p.getModelName());
            entry.put("displayName", p.getModelName());
            models.add(entry);
        }
        return modelListFilter.filter(models);
    }

    /**
     * 按 channelType 聚合的模型清单      * <p>
     * 用于 `/api/models` UserAuth 路径（DashboardListModels）：所有用户看到一致的全局静态映射，
     * 反映"每种 channelType 当前有哪些已注册的模型名"。
     * <p>
     * 实现策略：通过 Pricing 快照逐条遍历——每个 PricingVO 至少有一个绑定渠道，按 channelType 聚合该模型名。
     * 该映射在 Java 端无静态注册表，故使用 ability+channel JOIN 数据动态聚合。
     */
    public Map<Integer, List<String>> getChannelId2Models() {
        // 构建 channelType → modelName Set 的聚合（去重）
        Map<Integer, Set<String>> aggregator = new LinkedHashMap<>();
        List<AbilityWithChannel> abilities = abilityMapper.getAllEnableAbilityWithChannels();
        for (AbilityWithChannel ac : abilities) {
            Integer channelType = ac.getChannelType();
            String model = ac.getModel();
            if (channelType == null || model == null) continue;
            aggregator.computeIfAbsent(channelType, k -> new LinkedHashSet<>()).add(model);
        }

        // 精选白名单过滤：将所有模型名构建为临时列表交由 ModelListFilter 过滤
        Set<String> allModels = new LinkedHashSet<>();
        for (Set<String> set : aggregator.values()) {
            allModels.addAll(set);
        }
        Set<String> whitelist = new LinkedHashSet<>();
        if (!allModels.isEmpty()) {
            List<Map<String, Object>> tempModels = new ArrayList<>();
            for (String name : allModels) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", name);
                tempModels.add(entry);
            }
            List<Map<String, Object>> filtered = modelListFilter.filter(tempModels);
            for (Map<String, Object> m : filtered) {
                Object id = m.get("id");
                if (id != null) whitelist.add(id.toString());
            }
        }

        // 转为有序 List 输出，仅保留白名单中的模型
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<String>> e : aggregator.entrySet()) {
            List<String> filteredList = new ArrayList<>();
            for (String model : e.getValue()) {
                if (whitelist.contains(model)) {
                    filteredList.add(model);
                }
            }
            if (!filteredList.isEmpty()) {
                result.put(e.getKey(), filteredList);
            }
        }
        return result;
    }

    /**
     * 按 vendor_id 聚合的模型计数。      * <p>
     * 用于 `/api/models/` AdminAuth 路径（GetAllModelsMeta）的响应中 vendor_counts 字段。
     */
    public Map<Integer, Long> getVendorModelCounts() {
        List<ModelMeta> all = modelMetaMapper.selectList(new LambdaQueryWrapper<ModelMeta>()
                .select(ModelMeta::getVendorId)
                .isNotNull(ModelMeta::getVendorId));
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (ModelMeta m : all) {
            counts.merge(m.getVendorId(), 1L, Long::sum);
        }
        return counts;
    }

    // ======================== UserModels 业务支撑 ========================

    /**
     * 判定模型是否存在计费配置      * <p>
     * 等价语义：模型在 PricingService 快照中（说明已配置 ratio 或 price），
     * 或在默认 ModelRatioConstants 内置表中。计费表达式（tiered_expr）模式由 Pricing 快照覆盖。
     */
    public boolean hasModelBillingConfig(String modelName) {
        if (modelName == null || modelName.isEmpty()) return false;
        // 1. PricingService 快照内 → 已配置 ratio/price
        Integer quotaType = pricingService.getPricingSnapshot().quotaTypes().get(modelName);
        if (quotaType != null) return true;
        // 2. 内置默认 ratio 兜底（精确 + 通配 *）
        if (ModelRatioConstants.DEFAULT_MODEL_RATIO.containsKey(modelName)) return true;
        for (String key : ModelRatioConstants.DEFAULT_MODEL_RATIO.keySet()) {
            if (key.endsWith("/*") && modelName.startsWith(key.substring(0, key.length() - 2))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取模型支持的端点类型列表      * <p>
     * 委托 PricingService 的快照（已构建 model→endpoints 索引）。
     */
    public List<String> getModelSupportEndpointTypes(String modelName) {
        if (modelName == null) return List.of();
        for (PricingVO p : pricingService.getPricing()) {
            if (modelName.equals(p.getModelName())) {
                return p.getSupportedEndpointTypes() == null ? List.of() : p.getSupportedEndpointTypes();
            }
        }
        return List.of();
    }

    /**
     * 推断每个模型的首选 owner      * <p>
     * 实现策略：
     * <ol>
     *   <li>查询 ability+channel JOIN，过滤 group ∈ groups 且模型在 modelNames 中</li>
     *   <li>按 (priority, weight) 排序，每个模型取首条记录的 channelType</li>
     *   <li>channelType → ChannelConstants.getChannelTypeName 转小写作为 owner</li>
     *   <li>同时优先使用 PricingService.vendor 名（若该模型有配置 vendor）</li>
     * </ol>
     */
    public Map<String, String> getPreferredModelOwners(List<String> modelNames, List<String> groups) {
        return getPreferredModelOwners(modelNames, groups, pricingService.getPricingSnapshot());
    }

    /**
     * 推断每个模型的首选 owner（传入 PricingSnapshot 避免重复 JetCache 代理调用）
     */
    public Map<String, String> getPreferredModelOwners(List<String> modelNames, List<String> groups,
                                                        PricingService.PricingSnapshot snapshot) {
        Map<String, String> result = new LinkedHashMap<>();
        if (modelNames == null || modelNames.isEmpty() || groups == null || groups.isEmpty()) {
            return result;
        }
        Set<String> nameSet = new HashSet<>(modelNames);
        Set<String> groupSet = new HashSet<>(groups);

        // 1. 优先：PricingService vendor → owner（从传入的 snapshot 直接取，避免重复 JetCache 调用）
        Map<Integer, String> vendorIdToName = new LinkedHashMap<>();
        for (PricingVendorVO v : snapshot.vendorsList()) {
            vendorIdToName.put(v.getId(), v.getName());
        }
        for (PricingVO p : snapshot.pricingList()) {
            if (!nameSet.contains(p.getModelName())) continue;
            if (p.getVendorId() != null) {
                String name = vendorIdToName.get(p.getVendorId());
                if (name != null && !name.isBlank()) {
                    result.put(p.getModelName(), name);
                }
            }
        }

        // 2. 兜底：ability+channel JOIN 取 channelType → ChannelConstants.getChannelTypeName
        if (result.size() < modelNames.size()) {
            List<AbilityWithChannel> abilities = pricingService.getCachedAllEnableAbilityWithChannels();
            // 按 (priority desc, weight desc) 排序
            abilities.sort((a, b) -> {
                long pa = a.getPriority() == null ? 0 : a.getPriority();
                long pb = b.getPriority() == null ? 0 : b.getPriority();
                if (pa != pb) return Long.compare(pb, pa);
                int wa = a.getWeight() == null ? 0 : a.getWeight();
                int wb = b.getWeight() == null ? 0 : b.getWeight();
                return Integer.compare(wb, wa);
            });
            for (AbilityWithChannel ac : abilities) {
                if (!nameSet.contains(ac.getModel())) continue;
                if (!groupSet.contains(ac.getGroup())) continue;
                if (result.containsKey(ac.getModel())) continue;
                if (ac.getChannelType() == null) continue;
                String typeName = ChannelConstants.getChannelTypeName(ac.getChannelType());
                if (typeName != null && !typeName.isBlank() && !"Unknown".equals(typeName)) {
                    result.put(ac.getModel(), typeName.toLowerCase());
                }
            }
        }
        return result;
    }

    /**
     * 构建用户视角的 OpenAI 兼容模型列表      * <p>
     * 流程：
     * <ol>
     *   <li>解析用户分组（user_group / token_group / auto 模式遍历）</li>
     *   <li>按 token 模型限制或分组启用模型构建候选模型名单</li>
     *   <li>过滤未配置计费的模型（除非 acceptUnsetRatioModel = true）</li>
     *   <li>每个模型补 owned_by + supported_endpoint_types</li>
     * </ol>
     *
     * @param userGroup            用户分组（可空，空时回退 default）
     * @param tokenGroup           token 携带的分组（auto 表示自动遍历）
     * @param tokenModelLimit      token 模型限制白名单（key=模型名）；null/空表示不限制
     * @param modelLimitEnabled    token 是否启用模型限制
     * @param acceptUnsetRatio     是否接受未配置 ratio 的模型（SelfUseMode 或用户设置 AcceptUnsetRatioModel）
     */
    public List<Map<String, Object>> listUserOpenAIModels(String userGroup, String tokenGroup,
                                                           Map<String, Boolean> tokenModelLimit,
                                                           boolean modelLimitEnabled,
                                                           boolean acceptUnsetRatio) {
        // 1. 解析 ownerGroups（用于查询 ability 表）
        List<String> ownerGroups;
        if ("auto".equals(tokenGroup)) {
            ownerGroups = GroupService.getUserAutoGroup(userGroup);
            if (ownerGroups == null || ownerGroups.isEmpty()) {
                ownerGroups = List.of(userGroup == null || userGroup.isEmpty() ? "default" : userGroup);
            }
        } else {
            String group = (tokenGroup != null && !tokenGroup.isEmpty()) ? tokenGroup
                    : (userGroup == null || userGroup.isEmpty() ? "default" : userGroup);
            ownerGroups = List.of(group);
        }

        // 2. 获取 PricingSnapshot 一次（60s JetCache 缓存），复用聚合数据避免重复 DB 查询
        PricingService.PricingSnapshot snapshot = pricingService.getPricingSnapshot();

        // 3. 从 snapshot.pricingList 一次性构建 model→endpoints 索引（消除 O(N×M) 遍历）
        Map<String, List<String>> endpointsByName = new HashMap<>(snapshot.pricingList().size());
        for (PricingVO p : snapshot.pricingList()) {
            endpointsByName.put(p.getModelName(),
                    p.getSupportedEndpointTypes() == null ? List.of() : p.getSupportedEndpointTypes());
        }

        // 4. 构建候选模型名单
        Set<String> userModelNames = new LinkedHashSet<>();
        if (modelLimitEnabled) {
            if (tokenModelLimit != null) {
                for (String name : tokenModelLimit.keySet()) {
                    if (acceptUnsetRatio || hasModelBillingConfig(name)) {
                        userModelNames.add(name);
                    }
                }
            }
        } else {
            // 从 snapshot.enableGroups 内存过滤（替代 getEnabledModelNamesByGroup DB 查询）
            Set<String> groupSet = new HashSet<>(ownerGroups);
            for (Map.Entry<String, List<String>> entry : snapshot.enableGroups().entrySet()) {
                String model = entry.getKey();
                for (String g : entry.getValue()) {
                    if (groupSet.contains(g)) {
                        if (acceptUnsetRatio || hasModelBillingConfig(model)) {
                            userModelNames.add(model);
                        }
                        break;
                    }
                }
            }
        }

        // 5. 推断 owner（传入 snapshot 避免重复 JetCache 代理调用）
        Map<String, String> ownerByModel = getPreferredModelOwners(new ArrayList<>(userModelNames), ownerGroups, snapshot);

        // 6. 批量查询模型 max_context（按 model_name 映射，未配置的 get 返回 null）
        Map<String, Integer> maxContextByModel = getMaxContextByName(userModelNames);

        // 7. 构建 OpenAI 模型条目（endpoints 从索引 O(1) 查询，替代遍历）
        List<Map<String, Object>> models = new ArrayList<>(userModelNames.size());
        for (String name : userModelNames) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", name);
            entry.put("object", "model");
            entry.put("created", 1626777600);
            entry.put("owned_by", ownerByModel.getOrDefault(name, "custom"));
            entry.put("supported_endpoint_types", endpointsByName.getOrDefault(name, List.of()));
            entry.put("max_context", maxContextByModel.get(name));
            models.add(entry);
        }
        return modelListFilter.filter(models);
    }

    /**
     * 批量查询模型 max_context（按 model_name 映射）
     * <p>只返回已配置 max_context 的模型，未配置的不在返回 Map 中（get 返回 null）。
     */
    private Map<String, Integer> getMaxContextByName(Set<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            return Map.of();
        }
        List<ModelMeta> list = modelMetaMapper.selectList(new LambdaQueryWrapper<ModelMeta>()
                .select(ModelMeta::getModelName, ModelMeta::getMaxContext)
                .in(ModelMeta::getModelName, modelNames)
                .isNotNull(ModelMeta::getMaxContext));
        Map<String, Integer> result = new HashMap<>(list.size());
        for (ModelMeta m : list) {
            if (m.getMaxContext() != null) {
                result.put(m.getModelName(), m.getMaxContext());
            }
        }
        return result;
    }
}
