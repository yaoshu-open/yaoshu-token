package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分组倍率配置  */
public final class GroupRatioConfig {

    private GroupRatioConfig() {
    }

    // ======================== 默认分组倍率 ========================

    private static final Map<String, Double> DEFAULT_GROUP_RATIO = Map.of(
            "default", 1.0,
            "vip", 1.0,
            "svip", 1.0
    );

    /** 分组合并倍率（userGroup → usingGroup → ratio） */
    private static final ConcurrentHashMap<String, Map<String, Double>> GROUP_GROUP_RATIO = new ConcurrentHashMap<>();

    /** 分组特殊可用分组（userGroup → specialGroup → description） */
    private static final ConcurrentHashMap<String, Map<String, String>> GROUP_SPECIAL_USABLE_GROUP = new ConcurrentHashMap<>();

    static {
        GROUP_GROUP_RATIO.put("vip", Map.of("edit_this", 0.9));
        GROUP_SPECIAL_USABLE_GROUP.put("vip", Map.of(
                "append_1", "vip_special_group_1",
                "-:remove_1", "vip_removed_group_1"
        ));
    }

    // ======================== 线程安全的比率缓存 ========================

    private static final ConcurrentHashMap<String, Double> groupRatioMap = new ConcurrentHashMap<>(DEFAULT_GROUP_RATIO);
    private static final ConcurrentHashMap<String, Map<String, Double>> groupGroupRatioMap = new ConcurrentHashMap<>(GROUP_GROUP_RATIO);
    private static final ConcurrentHashMap<String, Map<String, String>> groupSpecialUsableGroupMap = new ConcurrentHashMap<>(GROUP_SPECIAL_USABLE_GROUP);

    // ======================== 公共 API ========================

    /** 获取分组倍率 */
    public static double getGroupRatio(String name) {
        return groupRatioMap.getOrDefault(name, 1.0);
    }

    /** 获取分组倍率副本 */
    public static Map<String, Double> getGroupRatioCopy() {
        return new ConcurrentHashMap<>(groupRatioMap);
    }

    /** 检查分组是否存在 */
    public static boolean containsGroupRatio(String name) {
        return groupRatioMap.containsKey(name);
    }

    /** 获取用户分组对特定分组的倍率 */
    public static double getGroupGroupRatio(String userGroup, String usingGroup) {
        Map<String, Double> gp = groupGroupRatioMap.get(userGroup);
        if (gp == null) return -1;
        return gp.getOrDefault(usingGroup, -1.0);
    }

    /** 获取分组特殊可用分组设置 */
    public static Map<String, Map<String, String>> getGroupSpecialUsableGroup() {
        return new ConcurrentHashMap<>(groupSpecialUsableGroupMap);
    }

    // ======================== JSON 同步 ========================

    @SuppressWarnings("unchecked")
    public static void updateFromMaps(Map<String, Double> ratios, Map<String, Map<String, Double>> groupRatios,
                                     Map<String, Map<String, String>> specialGroups) {
        if (ratios != null) {
            groupRatioMap.clear();
            groupRatioMap.putAll(ratios);
        }
        if (groupRatios != null) {
            groupGroupRatioMap.clear();
            groupGroupRatioMap.putAll(groupRatios);
        }
        if (specialGroups != null) {
            groupSpecialUsableGroupMap.clear();
            groupSpecialUsableGroupMap.putAll(specialGroups);
        }
    }
}
