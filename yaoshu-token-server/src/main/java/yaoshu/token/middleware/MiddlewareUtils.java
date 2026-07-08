package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 中间件工具方法  * <p>
 * 提供 Filter 通用的工具方法（如 abortWithOpenAiMessage）。
 */
@Slf4j
public final class MiddlewareUtils {

    private MiddlewareUtils() {}

    /**
     * 以 OpenAI 兼容格式中断请求      */
    public static void abortWithOpenAiMessage(HttpServletResponse response, int status,
                                               String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":{\"message\":\"%s\",\"type\":\"invalid_request_error\",\"code\":null}}",
                escapeJson(message)));
        response.getWriter().flush();
    }

    /**
     * 以 OpenAI 兼容格式中断请求（带错误码）      */
    public static void abortWithOpenAiMessage(HttpServletResponse response, int status,
                                               String message, String errorCode) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":{\"message\":\"%s\",\"type\":\"%s\",\"code\":\"%s\"}}",
                escapeJson(message), escapeJson(errorCode), escapeJson(errorCode)));
        response.getWriter().flush();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
