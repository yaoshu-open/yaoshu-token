package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Option;
import yaoshu.token.pojo.ipo.OptionIPO;
import yaoshu.token.relay.helper.PriceHelper;
import yaoshu.token.service.ChannelAffinityService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakePairException;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalog;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalogStore;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;

import java.util.*;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 系统设置控制器
 * <p>
 * 认证：RootAuth（全部）
 */
@Slf4j
@RestController
@SaCheckRole("root")
@RequestMapping("/api/option")
@RequiredArgsConstructor
public class OptionController {

    private final OptionService optionService;
    private final yaoshu.token.service.WaffoPancakeService waffoPancakeService;

    /** Go 中用于构建 CompletionRatioMeta 的 option key 列表 */
    private static final Set<String> COMPLETION_RATIO_META_OPTION_KEYS = Set.of(
            "ModelPrice", "ModelRatio", "CompletionRatio",
            "CacheRatio", "CreateCacheRatio", "ImageRatio",
            "AudioRatio", "AudioCompletionRatio"
    );

    /** 敏感 key 后缀：Token、Secret、Key、secret、api_key */
    private static final String[] SENSITIVE_SUFFIXES = {"Token", "Secret", "Key", "secret", "api_key"};

    /**
     * 获取所有系统设置（排除敏感 key）
     */
    @GetMapping("/")
    public Result<?> getAll() {
        List<Option> allOptions = optionService.getAll();
        Map<String, String> optionValues = new LinkedHashMap<>();
        List<Map<String, String>> filteredOptions = new ArrayList<>();

        for (Option opt : allOptions) {
            // 过滤敏感 key
            if (isSensitiveKey(opt.getKey())) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", opt.getKey());
            item.put("value", opt.getValue());
            filteredOptions.add(item);

            // 收集用于构建 CompletionRatioMeta 的 key
            if (COMPLETION_RATIO_META_OPTION_KEYS.contains(opt.getKey())) {
                optionValues.put(opt.getKey(), opt.getValue());
            }
        }

        // 追加 CompletionRatioMeta
        Map<String, String> metaItem = new LinkedHashMap<>();
        metaItem.put("key", "CompletionRatioMeta");
        metaItem.put("value", buildCompletionRatioMetaValue(optionValues));
        filteredOptions.add(metaItem);

        return R.success(filteredOptions);
    }

    /**
     * 更新系统设置
     */
    @PutMapping("/")
    public Result<?> update(@Valid @RequestBody OptionIPO.Update ipo) {
        String key = ipo.getKey();
        Object value = ipo.getValue();

        // Go: 值统一转为字符串存储
        String strValue = String.valueOf(value);

        // 保存到数据库 + 更新缓存
        optionService.saveOrUpdate(key, strValue);

        return R.success();
    }

    /** 判断 key 是否为敏感 key（Go: 后缀匹配） */
    private boolean isSensitiveKey(String key) {
        for (String suffix : SENSITIVE_SUFFIXES) {
            if (key.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 CompletionRatioMeta 值。      * <p>
     * 从各倍率 option 值（JSON）中收集所有模型名，逐个查询完成倍率信息，
     * 聚合为 {modelName: {ratio, locked}} 结构的 JSON 字符串。
     */
    private String buildCompletionRatioMetaValue(Map<String, String> optionValues) {
        // 步骤1：从各倍率 option 的 JSON 中提取所有模型名（去重）
        Set<String> modelNames = new LinkedHashSet<>();
        for (String key : COMPLETION_RATIO_META_OPTION_KEYS) {
            String raw = optionValues.get(key);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            // 各倍率配置值为 {modelName: ratio} 形式的 JSON，解析后收集 key
            com.alibaba.fastjson2.JSONObject parsed =
                    ai.yue.library.base.convert.Convert.toJSONObject(raw);
            if (parsed != null) {
                modelNames.addAll(parsed.keySet());
            }
        }

        // 步骤2：逐个模型查询完成倍率信息（ratio + locked）
        Map<String, Map<String, Object>> meta = new LinkedHashMap<>();
        for (String modelName : modelNames) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("ratio", PriceHelper.getCompletionRatio(modelName));
            info.put("locked", false);
            meta.put(modelName, info);
        }

        return ai.yue.library.base.convert.Convert.toJSONString(meta);
    }

    @PostMapping("/payment_compliance")
    public Result<?> confirmPaymentCompliance(HttpServletRequest request,
                                                         @Valid @RequestBody OptionIPO.PaymentCompliance ipo) {
        Boolean confirmed = ipo.getConfirmed();
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new ResultException(R.errorPrompt("请确认合规声明"));
        }
        long now = System.currentTimeMillis() / 1000;
        Integer userId = (Integer) request.getAttribute("id");

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("payment_setting.compliance_confirmed", "true");
        updates.put("payment_setting.compliance_terms_version", "v1");
        updates.put("payment_setting.compliance_confirmed_at", String.valueOf(now));
        updates.put("payment_setting.compliance_confirmed_by", String.valueOf(userId == null ? 1 : userId));
        updates.put("payment_setting.compliance_confirmed_ip", request.getRemoteAddr());
        // 专用合规端点直接写库，绕过通用 UpdateOption 的合规字段拦截
        for (var entry : updates.entrySet()) {
            optionService.saveOrUpdateRaw(entry.getKey(), entry.getValue());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("confirmed", true);
        data.put("terms_version", "v1");
        data.put("confirmed_at", now);
        data.put("confirmed_by", userId);
        return R.success(data);
    }

    @GetMapping("/channel_affinity_cache")
    public Result<?> getChannelAffinityCache() {
        ChannelAffinityService.AffinityCacheStats stats = ChannelAffinityService.getAffinityCacheStats();
        return R.success(stats);
    }

    @DeleteMapping("/channel_affinity_cache")
    public Result<?> clearChannelAffinityCache(
            @RequestParam(required = false) String all,
            @RequestParam(required = false) String rule_name) {
        if ("true".equalsIgnoreCase(all)) {
            int deleted = ChannelAffinityService.clearAllAffinityCache();
            return R.success(Map.of("deleted", deleted));
        }

        if (rule_name == null || rule_name.isBlank()) {
            throw new ResultException(R.errorPrompt("缺少参数：rule_name，或使用 all=true 清空全部"));
        }

        ChannelAffinityService.ChannelAffinitySetting setting = ChannelAffinityService.getGlobalSetting();
        if (setting == null) {
            throw new ResultException(R.errorPrompt("channel_affinity_setting 未初始化"));
        }

        int deleted = ChannelAffinityService.clearAffinityCacheByRuleName(rule_name.trim(), setting);
        if (deleted <= 0) {
            // Go 中 clearAffinityCacheByRuleName 可能返回 error，此处通过 deleted=0 区分
            throw new ResultException(R.errorPrompt("未找到匹配的规则或该规则未启用 include_rule_name"));
        }
        return R.success(Map.of("deleted", deleted));
    }

    @PostMapping("/rest_model_ratio")
    public Result<?> resetModelRatio() {
        // 默认 ModelRatio 为空的 JSON 对象
        String defaultRatio = "{}";
        optionService.saveOrUpdate("ModelRatio", defaultRatio);
        return R.success(Map.of("message", "模型倍率已重置为默认值"));
    }

    @PostMapping("/migrate_console_setting")
    public Result<?> migrateConsoleSetting() {
        // 读取 ApiInfo 迁移到 console_setting.api_info
        String apiInfo = optionService.getValue("ApiInfo");
        if (apiInfo != null && !apiInfo.isBlank()) {
            optionService.saveOrUpdate("console_setting.api_info", apiInfo);
        }
        // Announcements 迁移
        String announcements = optionService.getValue("Announcements");
        if (announcements != null && !announcements.isBlank()) {
            optionService.saveOrUpdate("console_setting.announcements", announcements);
        }
        // FAQ 迁移
        String faq = optionService.getValue("FAQ");
        if (faq != null && !faq.isBlank()) {
            optionService.saveOrUpdate("console_setting.faq", faq);
        }
        optionService.refreshCache();
        return R.success(Map.of("message", "migrated"));
    }

    @PostMapping("/waffo-pancake/catalog")
    public Result<?> listWaffoPancakeCatalog(@RequestBody(required = false) OptionIPO.WaffoCatalog ipo) {
        // body 凭证优先（typed-but-not-saved），空白时回退持久化凭证（避免管理员重复粘贴私钥）
        String bodyMerchantId = ipo == null ? null : ipo.getMerchantId();
        String bodyPrivateKey = ipo == null ? null : ipo.getPrivateKey();
        bodyMerchantId = bodyMerchantId == null ? "" : bodyMerchantId.trim();
        bodyPrivateKey = bodyPrivateKey == null ? "" : bodyPrivateKey.trim();

        String merchantId;
        String privateKey;
        if (bodyMerchantId.isEmpty() && bodyPrivateKey.isEmpty()) {
            merchantId = optionService.getValue("WaffoPancakeMerchantID");
            privateKey = optionService.getValue("WaffoPancakePrivateKey");
        } else {
            merchantId = bodyMerchantId;
            privateKey = bodyPrivateKey;
        }
        if (merchantId == null || merchantId.isBlank() || privateKey == null || privateKey.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 凭证未配置"));
        }
        try {
            return R.success(waffoPancakeService.listCatalog(merchantId, privateKey));
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt("拉取目录失败"));
        }
    }

    @PostMapping("/waffo-pancake/pair")
    public Result<?> createWaffoPancakePair(@RequestBody(required = false) OptionIPO.WaffoPair ipo) {
        // body 凭证优先（typed-but-not-saved），空白时回退持久化凭证（对齐 catalog 端点 + Go resolveWaffoPancakeAdminCreds）
        String bodyMerchantId = ipo == null ? null : ipo.getMerchantId();
        String bodyPrivateKey = ipo == null ? null : ipo.getPrivateKey();
        bodyMerchantId = bodyMerchantId == null ? "" : bodyMerchantId.trim();
        bodyPrivateKey = bodyPrivateKey == null ? "" : bodyPrivateKey.trim();
        String returnURL = ipo == null ? null : ipo.getReturnUrl();
        returnURL = returnURL == null ? "" : returnURL.trim();

        String merchantId;
        String privateKey;
        if (bodyMerchantId.isEmpty() && bodyPrivateKey.isEmpty()) {
            merchantId = optionService.getValue("WaffoPancakeMerchantID");
            privateKey = optionService.getValue("WaffoPancakePrivateKey");
        } else {
            merchantId = bodyMerchantId;
            privateKey = bodyPrivateKey;
        }
        if (merchantId == null || merchantId.isBlank() || privateKey == null || privateKey.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 凭证未配置"));
        }

        try {
            WaffoPancakePairResult result = waffoPancakeService.createPrimaryPair(merchantId, privateKey, returnURL);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("store_id", result.getStoreId());
            data.put("store_name", result.getStoreName());
            data.put("product_id", result.getProductId());
            data.put("product_name", result.getProductName());
            return R.success(data);
        } catch (WaffoPancakePairException e) {
            // OrphanStore 半失败：store 已创建但 product 失败，对齐 Go orphan 响应（message="error" + data 含孤儿 store 上下文供前端预选/重试）
            WaffoPancakePairResult partial = e.getPartialResult();
            log.error("Waffo Pancake 创建店铺与产品失败 orphan_store=true store_id={} error={}",
                    partial.getStoreId(), e.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error", e.getMessage());
            data.put("store_id", partial.getStoreId());
            data.put("store_name", partial.getStoreName());
            data.put("orphan_store", true);
            return R.errorPrompt("error", data);
        } catch (RuntimeException e) {
            log.error("Waffo Pancake 创建店铺与产品失败 error={}", e.getMessage());
            throw new ResultException(R.errorPrompt("创建店铺与产品失败"));
        }
    }

    @PostMapping("/waffo-pancake/save")
    public Result<?> saveWaffoPancake(@RequestBody(required = false) OptionIPO.WaffoSave ipo) {
        String merchantId = ipo == null ? null : ipo.getMerchantId();
        String privateKey = ipo == null ? null : ipo.getPrivateKey();
        String returnURL = ipo == null ? null : ipo.getReturnUrl();
        String storeId = ipo == null ? null : ipo.getStoreId();
        String productId = ipo == null ? null : ipo.getProductId();
        try {
            waffoPancakeService.saveConfig(merchantId, privateKey, returnURL, storeId, productId);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("product_id", optionService.getValue("WaffoPancakeProductID"));
            data.put("store_id", optionService.getValue("WaffoPancakeStoreID"));
            return R.success(data);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt("保存配置失败"));
        }
    }

    @PostMapping("/waffo-pancake/subscription-product")
    public Result<?> createWaffoPancakeSubProduct(@RequestBody(required = false) OptionIPO.WaffoSubProduct ipo) {
        String name = ipo == null ? null : ipo.getName();
        String amount = ipo == null ? null : ipo.getAmount();
        name = name == null ? "" : name.trim();
        amount = amount == null ? "" : amount.trim();
        if (name.isEmpty()) {
            throw new ResultException(R.errorPrompt("套餐名称不能为空"));
        }
        if (amount.isEmpty()) {
            throw new ResultException(R.errorPrompt("套餐价格不能为空"));
        }
        // 对齐 Go CreateWaffoPancakeSubscriptionProduct：用持久化凭证 + 持久化 storeID/returnURL（resolveWaffoPancakeAdminCreds("", "")）
        String merchantId = optionService.getValue("WaffoPancakeMerchantID");
        String privateKey = optionService.getValue("WaffoPancakePrivateKey");
        String storeId = optionService.getValue("WaffoPancakeStoreID");
        if (merchantId == null || merchantId.isBlank() || privateKey == null || privateKey.isBlank()
                || storeId == null || storeId.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 未完成配置，请先在支付设置中完成网关绑定"));
        }
        String returnURL = optionService.getValue("WaffoPancakeReturnURL");
        try {
            String productId = waffoPancakeService.createProductForPlan(
                    merchantId, privateKey, storeId, name, amount, returnURL);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("product_id", productId);
            data.put("product_name", name);
            data.put("store_id", storeId);
            return R.success(data);
        } catch (RuntimeException e) {
            log.error("Waffo Pancake 创建套餐产品失败 store_id={} name={} amount={} error={}",
                    storeId, name, amount, e.getMessage());
            throw new ResultException(R.errorPrompt("创建套餐产品失败"));
        }
    }

    @PostMapping("/waffo-pancake/subscription-product-options")
    public Result<?> listWaffoPancakeSubProductOptions() {
        // 对齐 Go ListWaffoPancakeSubscriptionProductOptions：持久化凭证 + 持久化 storeID → catalog 过滤
        String merchantId = optionService.getValue("WaffoPancakeMerchantID");
        String privateKey = optionService.getValue("WaffoPancakePrivateKey");
        String storeId = optionService.getValue("WaffoPancakeStoreID");
        if (merchantId == null || merchantId.isBlank() || privateKey == null || privateKey.isBlank()
                || storeId == null || storeId.isBlank()) {
            throw new ResultException(R.errorPrompt("Waffo Pancake 未完成配置，请先在支付设置中完成网关绑定"));
        }
        try {
            WaffoPancakeCatalog catalog = waffoPancakeService.listCatalog(merchantId, privateKey);
            // 过滤出指定 storeID 的 OnetimeProducts（名称含 plan 概念，底层仍 OnetimeProducts）
            List<WaffoPancakeCatalogStore> stores = catalog.getStores();
            List<?> products = new ArrayList<>();
            if (stores != null) {
                for (WaffoPancakeCatalogStore store : stores) {
                    if (storeId.equals(store.getId())) {
                        products = store.getOnetimeProducts();
                        break;
                    }
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("store_id", storeId);
            data.put("products", products);
            return R.success(data);
        } catch (RuntimeException e) {
            log.error("Waffo Pancake 拉取订阅产品列表失败 store_id={} error={}", storeId, e.getMessage());
            throw new ResultException(R.errorPrompt("拉取产品列表失败"));
        }
    }
}
