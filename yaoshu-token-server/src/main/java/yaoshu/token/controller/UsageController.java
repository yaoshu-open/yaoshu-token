package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.service.TokenService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用量与数据控制器  * <p>
 * 认证：混合（Usage getTokenUsage 用 TokenAuthReadOnly，Data getAll/admin 用 AdminAuth，getSelf 用 UserAuth）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UsageController {

    private final TokenService tokenService;

    /**
     * 获取 Token 用量      * <p>
     * 从 Authorization Header 提取 Bearer token，查询 token 用量信息
     */
    @GetMapping("/usage/token/")
    public Result<?> getTokenUsage(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isEmpty()) {
            throw new ResultException(R.errorPrompt("No Authorization header"));
        }

        // 解析 Bearer token
        String tokenKey;
        if (authHeader.startsWith("Bearer ") || authHeader.startsWith("bearer ")) {
            tokenKey = authHeader.substring(7).trim();
        } else {
            tokenKey = authHeader.trim();
        }

        // 查询 token（key 含 sk- 前缀作为整体存储与查询）
        Token token = tokenService.getByKey(tokenKey);
        if (token == null) {
            throw new ResultException(R.errorPrompt("Invalid token"));
        }

        // 构建响应（Go 格式兼容）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("object", "token_usage");
        data.put("name", token.getName() != null ? token.getName() : "");
        
        // 配额计算
        long remainQuota = token.getRemainQuota() != null ? token.getRemainQuota() : 0;
        long usedQuota = token.getUsedQuota() != null ? token.getUsedQuota() : 0;
        long totalGranted = remainQuota + usedQuota;
        
        data.put("total_granted", totalGranted);
        data.put("total_used", usedQuota);
        data.put("total_available", remainQuota);
        data.put("unlimited_quota", token.getUnlimitedQuota() != null && token.getUnlimitedQuota());
        
        // 模型限制
        data.put("model_limits", new LinkedHashMap<>());
        data.put("model_limits_enabled", token.getModelLimitsEnabled() != null && token.getModelLimitsEnabled());
        
        // 过期时间
        long expiredAt = token.getExpiredTime() != null ? token.getExpiredTime() : -1;
        if (expiredAt == -1) {
            expiredAt = 0;
        }
        data.put("expires_at", expiredAt);

        return R.success(data);
    }
}