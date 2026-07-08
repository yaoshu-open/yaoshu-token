package yaoshu.token.controller;

import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户侧模型控制器  * <p>
 * 返回 OpenAI 兼容格式的模型列表：包含 id/object/created/owned_by/supported_endpoint_types。
 * 复杂度内聚到 {@link ModelService#listUserOpenAIModels}：
 * 解析用户分组 → 构建候选模型 → 过滤未配置计费 → 推断 owner → 补端点类型。
 */
@RestController
@RequiredArgsConstructor
public class UserModelController {

    private final ModelService modelService;
    private final UserService userService;
    private final OptionService optionService;

    /** 用户模型列表*/
    @GetMapping("/api/user/models")
    public Result<?> getUserModels(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");

        // 1. 用户分组：UserAuth 中间件未注入 USER_GROUP，此处主动拉取
        String userGroup = null;
        if (userId != null && userId > 0) {
            User user = userService.getById(userId, false);
            if (user != null && user.getGroup() != null && !user.getGroup().isEmpty()) {
                userGroup = user.getGroup();
            }
        }
        if (userGroup == null) userGroup = "default";

        // 2. token 上下文（仅当走 TokenAuthFilter 时存在；UserAuth 路径下通常为 null）
        String tokenGroup = (String) request.getAttribute(ContextKeyConstants.TOKEN_GROUP);
        Boolean modelLimitEnabledAttr = (Boolean) request.getAttribute(ContextKeyConstants.TOKEN_MODEL_LIMIT_ENABLED);
        boolean modelLimitEnabled = Boolean.TRUE.equals(modelLimitEnabledAttr);
        @SuppressWarnings("unchecked")
        Map<String, Boolean> tokenModelLimit = (Map<String, Boolean>) request.getAttribute(ContextKeyConstants.TOKEN_MODEL_LIMIT);

        // 3. acceptUnsetRatio：全局 SelfUseModeEnabled 优先；其次用户设置 AcceptUnsetRatioModel
        boolean acceptUnsetRatio = parseBool(optionService.getValue("SelfUseModeEnabled"));
        if (!acceptUnsetRatio && userId != null && userId > 0) {
            User user = userService.getById(userId, false);
            if (user != null && user.getSetting() != null) {
                acceptUnsetRatio = userSettingAcceptUnsetRatio(user.getSetting());
            }
        }

        // 4. 调用 Service 构建模型列表
        List<Map<String, Object>> models = modelService.listUserOpenAIModels(
                userGroup, tokenGroup, tokenModelLimit, modelLimitEnabled, acceptUnsetRatio);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("object", "list");
        payload.put("data", models);
        return R.success(payload);
    }

    private static boolean parseBool(String s) {
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    /**
     * 从 user.setting JSON 解析 accept_unset_ratio_model 字段      */
    private static boolean userSettingAcceptUnsetRatio(String settingJson) {
        try {
            Map<String, Object> setting = ai.yue.library.base.convert.Convert.toJSONObject(settingJson);
            if (setting == null) return false;
            Object v = setting.get("accept_unset_ratio_model");
            if (v == null) v = setting.get("acceptUnsetRatioModel");
            return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        } catch (Exception e) {
            return false;
        }
    }
}
