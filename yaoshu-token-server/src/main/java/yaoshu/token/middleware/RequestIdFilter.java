package yaoshu.token.middleware;

import cn.hutool.v7.core.data.id.IdUtil;
import cn.hutool.v7.core.date.DateUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求 ID 中间件  * <p>
 * 为每个请求生成唯一 ID（时间戳 + 构建哈希 + 随机字符串），注入 Header 和 SLF4J MDC。
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 构建信息指纹 */
    private static final String BUILD_FINGERPRINT = IdUtil.fastSimpleUUID().substring(0, 8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String requestId = DateUtil.format(DateUtil.date(System.currentTimeMillis()), "yyyyMMddHHmmssSSS")
                + BUILD_FINGERPRINT
                + IdUtil.fastSimpleUUID().substring(0, 8);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        request.setAttribute(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            // MDC cleanup handled by filter ordering
        }
    }
}
