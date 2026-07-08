package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.service.GroupService;
import yaoshu.token.service.UserService;
import yaoshu.token.config.AutoGroupConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 用户分组控制器  * <p>
 * 认证：混合（GetGroups AdminAuth，GetUserGroups UserAuth）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;

    /**
     * 获取所有分组名称列表      * <p>
     * Go 实现是 `ratio_setting.GetGroupRatioCopy()` 的 keys（**所有 GroupRatio 配置的分组名**），
     * 不受 user_usable_groups 配置过滤。
     */
    @SaCheckRole("admin")
    @GetMapping("/group/")
    public Result<?> getAll() {
        // 从 GroupRatioConfig 获取所有已配置的分组名（与 Go GetGroupRatioCopy().keys 等价）
        java.util.Set<String> groupNames = new java.util.LinkedHashSet<>(
                yaoshu.token.config.ratio.GroupRatioConfig.getGroupRatioCopy().keySet());
        return R.success(groupNames);
    }

    /**
     * 获取当前登录用户分组（Self）      */
    @SaCheckLogin
    @GetMapping("/user/self/groups")
    public Result<?> getSelfGroups(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        String userGroup = "default";

        if (userId != null) {
            userGroup = userService.getUserGroup(userId);
        }

        return R.success(buildUsableGroupsResult(userGroup));
    }

    /**
     * 获取用户分组列表（未登录入口）      * <p>
     * Go 行为：userRoute 未挂 UserAuth，userId=c.GetInt("id") 缺失时为 0，model.GetUserGroup(0) 失败，userGroup 保留 ""。
     * GetUserUsableGroups("") 跳过特殊规则，直接返回全局分组副本。
     */
    @GetMapping("/user/groups")
    public Result<?> getUserGroups(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        String userGroup = "";
        if (userId != null) {
            userGroup = userService.getUserGroup(userId);
        }
        return R.success(buildUsableGroupsResult(userGroup));
    }

    /**
     * 共享构建逻辑：基于 userGroup 计算 {分组名: {ratio, desc}} 结果
     */
    private Map<String, Object> buildUsableGroupsResult(String userGroup) {
        // 获取用户可用分组列表
        Map<String, String> userUsableGroups = GroupService.getUserUsableGroups(userGroup);

        // 构建 {分组名: {ratio, desc}} 结构
        Map<String, Object> usableGroups = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : userUsableGroups.entrySet()) {
            String groupName = entry.getKey();
            Map<String, Object> groupInfo = new LinkedHashMap<>();
            groupInfo.put("ratio", GroupService.getUserGroupRatio(userGroup, groupName));
            groupInfo.put("desc", entry.getValue());
            usableGroups.put(groupName, groupInfo);
        }

        // 集成 AutoGroupConfig：用户可用分组含 auto 时追加自动分组条目
        if (userUsableGroups.containsKey("auto")) {
            Map<String, Object> autoInfo = new LinkedHashMap<>();
            autoInfo.put("ratio", "\u81ea\u52a8"); // I18nUtils.get("common.auto")
            autoInfo.put("desc", AutoGroupConfig.getAutoGroups().toString());
            usableGroups.put("auto", autoInfo);
        }

        return usableGroups;
    }

}
