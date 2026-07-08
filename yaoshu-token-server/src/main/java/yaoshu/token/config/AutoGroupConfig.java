package yaoshu.token.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自动分组配置  */
public final class AutoGroupConfig {

    private AutoGroupConfig() {
    }

    private static final CopyOnWriteArrayList<String> AUTO_GROUPS = new CopyOnWriteArrayList<>(List.of("default"));

    private static volatile boolean defaultUseAutoGroup;

    /** 检查是否为自动分组 */
    public static boolean containsAutoGroup(String group) {
        return AUTO_GROUPS.contains(group);
    }

    /** 获取自动分组列表 */
    public static List<String> getAutoGroups() {
        return List.copyOf(AUTO_GROUPS);
    }

    /** 是否默认使用自动分组 */
    public static boolean isDefaultUseAutoGroup() {
        return defaultUseAutoGroup;
    }

    public static void setDefaultUseAutoGroup(boolean value) {
        defaultUseAutoGroup = value;
    }

    /** 更新自动分组列表 */
    public static void update(List<String> groups) {
        AUTO_GROUPS.clear();
        if (groups != null) AUTO_GROUPS.addAll(groups);
    }
}
