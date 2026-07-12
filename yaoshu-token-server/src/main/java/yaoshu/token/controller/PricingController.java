package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.pojo.vo.PricingVO;
import yaoshu.token.service.GroupService;
import yaoshu.token.service.PricingService;
import yaoshu.token.service.UserService;
import yaoshu.token.spi.ModelListFilter;

/**
 * 定价控制器  * <p>
 * 认证：HeaderNavModuleAuth("pricing")（Go）→ 需在 Filter 中处理
 */
@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;
    private final UserService userService;
    @Autowired(required = false)
    private ModelListFilter modelListFilter;

    /**
     * 获取定价信息      */
    @GetMapping
    public Result<?> getPricing(HttpServletRequest request) {
        List<PricingVO> pricing = pricingService.getPricing();

        // 步骤1：确定用户分组（登录用户取其 group，未登录为空）
        Integer userId = (Integer) request.getAttribute("id");
        String userGroup = "";
        Map<String, Double> groupRatio = GroupRatioConfig.getGroupRatioCopy();
        if (userId != null) {
            userGroup = userService.getUserGroup(userId);
            // 用户分组对各分组有特定合并倍率时覆盖
            for (String g : new ArrayList<>(groupRatio.keySet())) {
                double r = GroupRatioConfig.getGroupGroupRatio(userGroup, g);
                if (r >= 0) {
                    groupRatio.put(g, r);
                }
            }
        }

        // 步骤2：获取用户可用分组，过滤定价数据
        Map<String, String> usableGroup = GroupService.getUserUsableGroups(userGroup);
        pricing = filterPricingByUsableGroups(pricing, usableGroup);

        // 步骤2b：商业版精选模型白名单过滤（ModelListFilter SPI）
        if (modelListFilter != null && !pricing.isEmpty()) {
            List<Map<String, Object>> modelMaps = new ArrayList<>();
            for (PricingVO p : pricing) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getModelName());
                modelMaps.add(m);
            }
            List<Map<String, Object>> filtered = modelListFilter.filter(modelMaps);
            if (filtered.size() < modelMaps.size()) {
                // SPI 过滤掉了部分模型——重建 pricingList
                Set<String> visibleNames = filtered.stream()
                        .map(m -> (String) m.get("id"))
                        .collect(Collectors.toSet());
                pricing = pricing.stream()
                        .filter(p -> visibleNames.contains(p.getModelName()))
                        .collect(Collectors.toList());
            }
        }

        // 步骤3：剔除不在可用分组中的 groupRatio 条目
        groupRatio.keySet().removeIf(group -> !usableGroup.containsKey(group));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pricing", pricing);
        data.put("vendors", pricingService.getVendors());
        data.put("group_ratio", groupRatio);
        data.put("usable_group", usableGroup);
        data.put("supported_endpoint", pricingService.getSupportedEndpointMap());
        data.put("auto_groups", GroupService.getUserAutoGroup(userGroup));
        data.put("pricing_version", "a42d372ccf0b5dd13ecf71203521f9d2");
        return R.success(data);
    }

    /**
     * 按用户可用分组过滤定价数据      * <p>
     * 规则：可用分组为空 → 返回空列表；模型 enableGroup 含 "all" 或与可用分组有交集 → 保留。
     */
    private List<PricingVO> filterPricingByUsableGroups(List<PricingVO> pricing, Map<String, String> usableGroup) {
        if (pricing == null || pricing.isEmpty()) {
            return pricing;
        }
        if (usableGroup.isEmpty()) {
            return new ArrayList<>();
        }
        List<PricingVO> filtered = new ArrayList<>(pricing.size());
        for (PricingVO item : pricing) {
            List<String> enableGroup = item.getEnableGroup();
            if (enableGroup == null) {
                continue;
            }
            if (enableGroup.contains("all")) {
                filtered.add(item);
                continue;
            }
            for (String group : enableGroup) {
                if (usableGroup.containsKey(group)) {
                    filtered.add(item);
                    break;
                }
            }
        }
        return filtered;
    }

    /**
     * 重置模型倍率为默认值      * <p>认证：RootAuth（仅 root 角色，契约标注）
     */
    @SaCheckRole("root")
    @PostMapping("/reset_model_ratio")
    public Result<?> resetModelRatio() {
        // Go: ratio_setting.DefaultModelRatio2JSONString() → UpdateOption → UpdateModelRatioByJSONString
        // 当前简化实现：清除定价缓存，下一次查询时会用 ModelRatioConstants.DEFAULT_MODEL_RATIO
        pricingService.invalidatePricingCache();

        return R.success("重置模型倍率成功");
    }
}
