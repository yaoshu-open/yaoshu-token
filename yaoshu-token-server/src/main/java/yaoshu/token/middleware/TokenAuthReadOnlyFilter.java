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
 * 宽松 Token 认证中间件（只读模式）  * <p>
 * 只验证 Token Key 是否存在，不检查状态/过期/额度。即使 Token 已过期/耗尽/禁用也允许访问。
 * 仅检查用户是否被封禁。用于只读查询接口。
 */
@Slf4j
public class TokenAuthReadOnlyFilter implements jakarta.servlet.Filter {

    private final TokenService tokenService;
    private final UserService userService;

    public TokenAuthReadOnlyFilter(TokenService tokenService, UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                          jakarta.servlet.ServletResponse servletResponse,
                          FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"未提供令牌\"}");
            return;
        }

        // 提取 key
        String key = authHeader;
        if (key.startsWith("Bearer ") || key.startsWith("bearer ")) {
            key = key.substring(7).trim();
        }
        // 只读查询 Token（key 含 sk- 前缀作为整体存储与查询，不检查过期/额度/状态）
        Token token = tokenService.getByKey(key);
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"令牌无效\"}");
            return;
        }

        // 检查用户是否封禁（唯一的检查项）
        User user = userService.getById(token.getUserId(), false);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"用户已被封禁\"}");
            return;
        }

        // 注入上下文（与 Go 一致：id / token_id / token_key）
        request.setAttribute("id", token.getUserId());
        request.setAttribute("token_id", token.getId());
        request.setAttribute("token_key", key);

        chain.doFilter(request, response);
    }
}
