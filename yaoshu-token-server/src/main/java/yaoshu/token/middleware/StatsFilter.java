package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 访问统计中间件  * <p>
 * 统计活跃连接数，供监控使用。
 */
@Slf4j
public class StatsFilter extends OncePerRequestFilter {

    /** 活跃连接数*/
    private static final AtomicLong activeConnections = new AtomicLong(0);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        activeConnections.incrementAndGet();
        try {
            chain.doFilter(request, response);
        } finally {
            activeConnections.decrementAndGet();
        }
    }

    /** 获取统计信息 */
    public static long getActiveConnections() {
        return activeConnections.get();
    }
}
