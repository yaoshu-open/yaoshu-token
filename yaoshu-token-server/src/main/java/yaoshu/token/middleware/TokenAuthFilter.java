package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.TokenService;
import yaoshu.token.service.UserService;

import java.io.IOException;

/**
 * Token 认证中间件（API Key 模式）  * <p>
 * 适用于 /v1/* AI API 中转路由的 API Key 认证。支持多源 Key 提取：
 * <ul>
 * <li>Authorization: Bearer sk-xxx</li>
 * <li>Sec-WebSocket-Protocol: openai-insecure-api-key.sk-xxx（WebSocket）</li>
 * <li>x-api-key: xxx（Anthropic 兼容）</li>
 * <li>?key=xxx（Gemini API query 参数）</li>
 * <li>x-goog-api-key: xxx（Gemini API）</li>
 * <li>mj-api-secret: xxx（Midjourney）</li>
 * </ul>
 */
@Slf4j
public class TokenAuthFilter implements jakarta.servlet.Filter {

    private final TokenService tokenService;
    private final UserService userService;

    public TokenAuthFilter(TokenService tokenService, UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                          jakarta.servlet.ServletResponse servletResponse,
                          FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 多源 Key 提取
        extractKeyFromVariousSources(request);

        String key = resolveApiKey(request);
        if (key == null || key.isEmpty()) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "未提供令牌");
            return;
        }

        // 校验 Token（key 含 sk- 前缀作为整体存储与查询）
        Token token = tokenService.getByKey(key);
        if (token == null) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "令牌无效");
            return;
        }

        // 状态检查
        Integer tokenStatus = token.getStatus();
        if (tokenStatus == null || tokenStatus != 1) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "令牌已过期或已用尽");
            return;
        }

        // 过期检查
        Long expiredTime = token.getExpiredTime();
        if (expiredTime != null && expiredTime != -1 && expiredTime < System.currentTimeMillis() / 1000) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "令牌已过期");
            return;
        }

        // 配额检查（无限配额跳过）
        if (!Boolean.TRUE.equals(token.getUnlimitedQuota())) {
            Long remainQuota = token.getRemainQuota();
            if (remainQuota != null && remainQuota <= 0) {
                MiddlewareUtils.abortWithOpenAiMessage(response,
                        HttpServletResponse.SC_UNAUTHORIZED, "令牌配额已用尽");
                return;
            }
        }

        // 检查用户状态
        Integer userId = token.getUserId();
        User user = userService.getById(userId, false);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_UNAUTHORIZED, "用户已被封禁");
            return;
        }

        // 注入 Token 上下文到 request attributes
        request.setAttribute("id", userId);
        request.setAttribute("token_id", token.getId());
        request.setAttribute("token_key", key);
        request.setAttribute("token_name", token.getName());
        request.setAttribute("token_unlimited_quota", Boolean.TRUE.equals(token.getUnlimitedQuota()));
        request.setAttribute("token_group", token.getGroup());
        request.setAttribute("group", token.getGroup());
        request.setAttribute("user_group", user.getGroup());

        chain.doFilter(request, response);
    }

    /**
     * 从多种来源提取 API Key 并统一设置到 Authorization Header
     */
    private void extractKeyFromVariousSources(HttpServletRequest request) {
        String path = request.getRequestURI();

        // WebSocket 场景：从 Sec-WebSocket-Protocol 提取 openai-insecure-api-key
        String wsProtocol = request.getHeader("Sec-WebSocket-Protocol");
        if (wsProtocol != null) {
            String[] parts = wsProtocol.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("openai-insecure-api-key")) {
                    request.setAttribute("_ws_key", part.replaceFirst("^openai-insecure-api-key\\.", ""));
                    break;
                }
            }
        }

        // Anthropic 场景：x-api-key
        if (path.contains("/v1/messages") || path.contains("/v1/models")) {
            String anthropicKey = request.getHeader("x-api-key");
            if (anthropicKey != null && !anthropicKey.isEmpty()) {
                request.setAttribute("_anthropic_key", anthropicKey);
            }
        }

        // Gemini 场景：query key 或 x-goog-api-key
        if (path.startsWith("/v1beta/models/") || path.startsWith("/v1beta/openai/models/")
                || path.startsWith("/v1/models/")) {
            String queryKey = request.getParameter("key");
            if (queryKey != null && !queryKey.isEmpty()) {
                request.setAttribute("_gemini_key", queryKey);
            }
            String googKey = request.getHeader("x-goog-api-key");
            if (googKey != null && !googKey.isEmpty()) {
                request.setAttribute("_goog_key", googKey);
            }
        }

        // Midjourney 场景
        String mjSecret = request.getHeader("mj-api-secret");
        if (mjSecret != null && !mjSecret.isEmpty()) {
            request.setAttribute("_mj_secret", mjSecret);
        }
    }

    /**
     * 从多个来源解析最终的 API Key
     */
    private String resolveApiKey(HttpServletRequest request) {
        // 优先使用 WS Key
        String wsKey = (String) request.getAttribute("_ws_key");
        if (wsKey != null && !wsKey.isEmpty()) return wsKey;

        // Authorization Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            if (authHeader.startsWith("Bearer ") || authHeader.startsWith("bearer ")) {
                return authHeader.substring(7).trim();
            }
            return authHeader.trim();
        }

        // Anthropic Key
        String anthropicKey = (String) request.getAttribute("_anthropic_key");
        if (anthropicKey != null && !anthropicKey.isEmpty()) return anthropicKey;

        // Gemini Key
        String geminiKey = (String) request.getAttribute("_gemini_key");
        if (geminiKey != null && !geminiKey.isEmpty()) return geminiKey;

        String googKey = (String) request.getAttribute("_goog_key");
        if (googKey != null && !googKey.isEmpty()) return googKey;

        // Midjourney Secret
        String mjSecret = (String) request.getAttribute("_mj_secret");
        if (mjSecret != null && !mjSecret.isEmpty()) return mjSecret;

        return null;
    }
}
