package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求日志中间件  * <p>
 * 记录请求方法、路径、状态码、耗时、IP 等信息（GIN logger 格式）。
 * <p>
 * 格式：[GIN] 2006/01/02 - 15:04:05 | {tag} | {requestId} | {status} | {latency} | {ip} | {method} {path}
 */
@Slf4j
public class LoggerFilter extends OncePerRequestFilter {

    public static final String ROUTE_TAG_KEY = "route_tag";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd - HH:mm:ss");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String routeTag = (String) request.getAttribute(ROUTE_TAG_KEY);
        if (routeTag == null) {
            routeTag = "web";
        }

        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);
            if (requestId == null) {
                requestId = "-";
            }
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String clientIP = ai.yue.library.web.util.ServletUtils.getClientIP(request);

            // GIN 格式日志
            log.info("[GIN] {} | {} | {} | {} | {}ms | {} | {} {}",
                    timestamp,
                    routeTag,
                    requestId,
                    response.getStatus(),
                    elapsed,
                    clientIP,
                    request.getMethod(),
                    request.getRequestURI());
        }
    }
}
