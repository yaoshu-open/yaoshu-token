package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.AutoGroupConfig;
import yaoshu.token.config.UserUsableGroupConfig;
import yaoshu.token.config.ratio.GroupRatioConfig;

import java.util.*;

/**
 * 用户分组管理服务  * <p>
 * 核心职责：
 * <ul>
 *   <li>获取用户可用分组列表（支持特殊分组规则 +:- 添加 / -: 移除）</li>
 *   <li>获取用户自动分组（用户可用分组 ∩ 全局自动分组）</li>
 *   <li>获取用户分组倍率</li>
 * </ul>
 */
@Slf4j
@Service
public class GroupService {

    /**
     * 获取用户可用分组列表      * <p>
     * 步骤：
     * <ol>
     *   <li>获取全局可用分组副本</li>
     *   <li>若 userGroup 非空，查找该分组的特殊可用分组规则</li>
     *   <li>解析特殊规则：前缀 {@code -:} 移除分组，前缀 {@code +:} 添加分组，无前缀直接添加</li>
     *   <li>若 userGroup 自身不在最终分组列表中，追加"用户分组"条目</li>
     * </ol>
     */
    public static Map<String, String> getUserUsableGroups(String userGroup) {
        // 步骤1：获取全局可用分组副本
        Map<String, String> groupsCopy = UserUsableGroupConfig.getUserUsableGroupsCopy();

        if (userGroup != null && !userGroup.isEmpty()) {
            // 步骤2：查找该分组的特殊可用分组规则
            Map<String, Map<String, String>> specialUsableGroup = GroupRatioConfig.getGroupSpecialUsableGroup();
            Map<String, String> specialSettings = specialUsableGroup.get(userGroup);

            if (specialSettings != null) {
                // 步骤3：解析特殊规则
                for (Map.Entry<String, String> entry : specialSettings.entrySet()) {
                    String specialGroup = entry.getKey();
                    String desc = entry.getValue();

                    if (specialGroup.startsWith("-:")) {
                        // 移除分组
                        String groupToRemove = specialGroup.substring(2);
                        groupsCopy.remove(groupToRemove);
                    } else if (specialGroup.startsWith("+:")) {
                        // 添加分组
                        String groupToAdd = specialGroup.substring(2);
                        groupsCopy.put(groupToAdd, desc);
                    } else {
                        // 直接添加分组
                        groupsCopy.put(specialGroup, desc);
                    }
                }
            }

            // 步骤4：若 userGroup 自身不在列表中，追加"用户分组"条目
            if (!groupsCopy.containsKey(userGroup)) {
                groupsCopy.put(userGroup, "\u7528\u6237\u5206\u7ec4"); // "用户分组"
            }
        }

        return groupsCopy;
    }

    /**
     * 检查分组是否在用户可用分组中      */
    public static boolean groupInUserUsableGroups(String userGroup, String groupName) {
        return getUserUsableGroups(userGroup).containsKey(groupName);
    }

    /**
     * 获取用户自动分组列表      * <p>
     * 用户可用分组 ∩ 全局自动分组
     */
    public static List<String> getUserAutoGroup(String userGroup) {
        Map<String, String> groups = getUserUsableGroups(userGroup);
        List<String> autoGroups = new ArrayList<>();
        for (String group : AutoGroupConfig.getAutoGroups()) {
            if (groups.containsKey(group)) {
                autoGroups.add(group);
            }
        }
        return autoGroups;
    }

    /**
     * 获取用户使用某分组的倍率      * <p>
     * 优先使用用户分组对目标分组的特定倍率，
     * 若无特定倍率则回退到全局分组倍率。
     *
     * @param userGroup 用户分组
     * @param group     需要获取倍率的分组
     * @return 倍率值（正数），无配置时返回 1.0
     */
    public static double getUserGroupRatio(String userGroup, String group) {
        double ratio = GroupRatioConfig.getGroupGroupRatio(userGroup, group);
        if (ratio >= 0) {
            return ratio;
        }
        return GroupRatioConfig.getGroupRatio(group);
    }
}
