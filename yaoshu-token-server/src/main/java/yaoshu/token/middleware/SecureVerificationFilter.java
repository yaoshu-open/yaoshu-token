package yaoshu.token.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * 安全验证中间件  * <p>
 * 对敏感操作（如查看渠道 Key）要求额外的安全验证（密码/2FA）。
 * 检查 session 中的 secure_verified_at 时间戳，验证有效期为 5 分钟。
 */
public class SecureVerificationFilter implements Filter {

    /** 安全验证 session key */
    public static final String SECURE_VERIFIED_AT = "secure_verified_at";
    /** 安全验证方式 session key */
    public static final String SECURE_VERIFIED_METHOD = "secure_verified_method";
    /** 验证有效期（秒）*/
    private static final long VERIFICATION_TIMEOUT = 300;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. 检查用户是否已登录
        Object userIdAttr = request.getAttribute("id");
        if (userIdAttr == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"未登录\"}");
            return;
        }

        // 2. 检查 session 中的验证时间戳
        HttpSession session = request.getSession(false);
        if (session == null) {
            sendVerificationRequired(response);
            return;
        }

        Object verifiedAtRaw = session.getAttribute(SECURE_VERIFIED_AT);
        if (verifiedAtRaw == null) {
            sendVerificationRequired(response);
            return;
        }

        long verifiedAt;
        if (verifiedAtRaw instanceof Long l) {
            verifiedAt = l;
        } else if (verifiedAtRaw instanceof Integer i) {
            verifiedAt = i.longValue();
        } else {
            // session 数据格式错误，清除
            clearSecureVerificationSession(session);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"验证状态异常，请重新验证\",\"code\":\"VERIFICATION_INVALID\"}");
            return;
        }

        // 3. 检查验证是否过期
        long elapsed = System.currentTimeMillis() / 1000 - verifiedAt;
        if (elapsed >= VERIFICATION_TIMEOUT) {
            clearSecureVerificationSession(session);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"验证已过期，请重新验证\",\"code\":\"VERIFICATION_EXPIRED\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendVerificationRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"需要安全验证\",\"code\":\"VERIFICATION_REQUIRED\"}");
    }

    /**
     * 清除安全验证 session      */
    private void clearSecureVerificationSession(HttpSession session) {
        session.removeAttribute(SECURE_VERIFIED_AT);
        session.removeAttribute(SECURE_VERIFIED_METHOD);
    }
}
