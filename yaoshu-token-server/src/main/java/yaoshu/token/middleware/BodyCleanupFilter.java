package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求体存储清理中间件  * <p>
 * 请求处理完成后清理请求体磁盘/内存缓存和文件缓存。
 * <p>
 * Go 版本调用 common.CleanupBodyStorage(c) 清理 BodyStorage（内存或磁盘缓存），
 * 以及 service.CleanupFileSources(c) 清理下载的临时文件。
 * Java 版本通过 request attribute 查找并关闭注册的可清理资源。
 */
@Slf4j
public class BodyCleanupFilter extends OncePerRequestFilter {

    /** 请求体存储的 request attribute key */
    public static final String BODY_STORAGE_KEY = "_body_storage";
    /** 文件源列表的 request attribute key */
    public static final String FILE_SOURCES_KEY = "_file_sources";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            cleanupResources(request);
        }
    }

    /**
     * 清理请求关联的资源      */
    private void cleanupResources(HttpServletRequest request) {
        // 1. 清理请求体存储
        Object bodyStorage = request.getAttribute(BODY_STORAGE_KEY);
        if (bodyStorage instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("清理请求体存储失败: {}", e.getMessage());
            }
            request.removeAttribute(BODY_STORAGE_KEY);
        }

        // 2. 清理文件源列表
        Object fileSources = request.getAttribute(FILE_SOURCES_KEY);
        if (fileSources instanceof Iterable<?> sources) {
            for (Object source : sources) {
                if (source instanceof AutoCloseable closeable) {
                    try {
                        closeable.close();
                    } catch (Exception e) {
                        log.debug("清理文件源失败: {}", e.getMessage());
                    }
                }
            }
            request.removeAttribute(FILE_SOURCES_KEY);
        }
    }
}
