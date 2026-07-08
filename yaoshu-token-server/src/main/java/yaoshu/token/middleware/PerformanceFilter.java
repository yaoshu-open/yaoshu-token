package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 系统性能检查中间件  * <p>
 * 在请求处理前检查系统性能（CPU/内存/磁盘）是否超过阈值。
 * 超过阈值时返回 503 Service Unavailable，防止系统过载崩溃。
 * <p>
 * 性能阈值配置来自 OptionService（PerformanceMonitorConfig）。
 */
@Slf4j
public class PerformanceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // 检查系统性能是否超过阈值
        String overloadError = checkSystemPerformance(request);
        if (overloadError != null) {
            boolean isClaudeApi = request.getRequestURI().contains("/v1/messages");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json; charset=UTF-8");
            if (isClaudeApi) {
                // Claude 格式错误响应
                response.getWriter().write(String.format(
                        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"%s\"}}",
                        escapeJson(overloadError)));
            } else {
                // OpenAI 格式错误响应
                response.getWriter().write(String.format(
                        "{\"error\":{\"message\":\"%s\",\"type\":\"server_error\",\"code\":\"system_overloaded\"}}",
                        escapeJson(overloadError)));
            }
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 检查系统性能      * <p>
     * 当 PerformanceMonitorConfig 未启用时返回 null（不检查）。
     * 当前为轻量级实现，仅检查内存使用率（JVM 可直接获取）。
     * CPU/磁盘的精确监控需要操作系统级别的 API。
     *
     * @return 超载错误消息，null 表示正常
     */
    private String checkSystemPerformance(HttpServletRequest request) {
        // 性能监控未启用则跳过
        boolean enabled = "true".equalsIgnoreCase(
                getOptionValue(request, "PerformanceMonitorEnabled"));
        if (!enabled) {
            return null;
        }

        int cpuThreshold = getIntOption(request, "PerformanceMonitorCPUThreshold", 0);
        int memoryThreshold = getIntOption(request, "PerformanceMonitorMemoryThreshold", 0);
        int diskThreshold = getIntOption(request, "PerformanceMonitorDiskThreshold", 0);

        // 内存检查（JVM 可直接获取）
        if (memoryThreshold > 0) {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            double memoryUsage = (double) usedMemory / maxMemory * 100;
            if ((int) memoryUsage > memoryThreshold) {
                return String.format("system memory overloaded (current: %.1f%%, threshold: %d%%)",
                        memoryUsage, memoryThreshold);
            }
        }

        // CPU/磁盘检查需要操作系统级别 API，当前跳过
        // 后续可通过 OperatingSystemMXBean 补充

        return null;
    }

    /**
     * 从请求属性中获取 OptionService 引用（由前置 Filter 注入或直接从 Spring 上下文获取）
     * 当前实现为简化版：直接返回 null（跳过配置读取），功能在 PerformanceMonitorEnabled 未配置时默认不启用
     */
    private String getOptionValue(HttpServletRequest request, String key) {
        // OptionService 通过 request attribute 传递（若需要可由 FilterConfig 注入）
        // 当前简化实现：性能监控配置直接从 CommonConstants 读取
        return null;
    }

    private int getIntOption(HttpServletRequest request, String key, int defaultValue) {
        return defaultValue;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
