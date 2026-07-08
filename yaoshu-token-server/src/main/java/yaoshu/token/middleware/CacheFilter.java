package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 静态资源缓存中间件  * <p>
 * 为静态资源响应设置 Cache-Control 头。
 * 根路径（/）不缓存，其他路径缓存一周。
 */
public class CacheFilter extends OncePerRequestFilter {

    /** Cache-Version*/
    private static final String CACHE_VERSION = "b688f2fb5be447c25e5aa3bd063087a83db32a288bf6a4f35f2d8db310e40b14";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if ("/".equals(uri)) {
            // 根路径不缓存
            response.setHeader("Cache-Control", "no-cache");
        } else {
            // 其他静态资源缓存一周
            response.setHeader("Cache-Control", "max-age=604800");
        }
        response.setHeader("Cache-Version", CACHE_VERSION);

        chain.doFilter(request, response);
    }
}
