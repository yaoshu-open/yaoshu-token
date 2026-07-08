package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 导航头中间件  * <p>
 * 设置前端导航所需的响应头。Go 版本还包含 HeaderNavModuleAuth / HeaderNavModulePublicOrUserAuth
 * 两个路由级中间件（非全局 Filter），用于按模块控制导航功能的访问权限。
 * <p>
 * 全局 Filter 部分仅设置 Auth-Version 头。模块级权限控制由对应 Controller 内联处理。
 */
public class HeaderNavFilter extends OncePerRequestFilter {

    /** Auth 版本号*/
    private static final String AUTH_VERSION = "864b7076dbcd0a3c01b5520316720ebf";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // 设置 Auth-Version 头（与 Go authHelper 中的 c.Header("Auth-Version", ...) 一致）
        response.setHeader("Auth-Version", AUTH_VERSION);

        chain.doFilter(request, response);
    }

    /**
     * 获取模块导航访问权限      * <p>
     * 从 options 表的 "HeaderNavModules" 配置中读取指定模块的访问控制。
     * 配置格式为 JSON：{"module": {"enabled": true/false, "requireAuth": true/false}}
     * 未配置时默认 enabled=true, requireAuth=false。
     *
     * @param optionMap 选项配置缓存（CommonConstants.optionMap）
     * @param module    模块名
     * @return [enabled, requireAuth]
     */
    public static boolean[] getHeaderNavAccess(java.util.Map<String, String> optionMap, String module) {
        boolean enabled = true;
        boolean requireAuth = false;

        if (optionMap == null) {
            return new boolean[]{enabled, requireAuth};
        }

        String raw = optionMap.get("HeaderNavModules");
        if (raw == null || raw.trim().isEmpty()) {
            return new boolean[]{enabled, requireAuth};
        }

        try {
            com.alibaba.fastjson2.JSONObject parsed =
                    ai.yue.library.base.convert.Convert.toJSONObject(raw);
            Object moduleConfig = parsed.get(module);
            if (moduleConfig == null) {
                return new boolean[]{enabled, requireAuth};
            }

            // 支持多种配置格式
            if (moduleConfig instanceof Boolean b) {
                enabled = b;
            } else if (moduleConfig instanceof com.alibaba.fastjson2.JSONObject obj) {
                if (obj.containsKey("enabled")) {
                    enabled = parseBool(obj.get("enabled"), enabled);
                }
                if (obj.containsKey("requireAuth")) {
                    requireAuth = parseBool(obj.get("requireAuth"), requireAuth);
                }
            }
        } catch (Exception e) {
            // 解析失败，返回默认值
        }

        return new boolean[]{enabled, requireAuth};
    }

    /**
     * 解析布尔值      */
    private static boolean parseBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return switch (s.toLowerCase().trim()) {
                case "true", "1" -> true;
                case "false", "0" -> false;
                default -> fallback;
            };
        }
        if (value instanceof Number n) {
            return n.intValue() == 1;
        }
        return fallback;
    }
}
