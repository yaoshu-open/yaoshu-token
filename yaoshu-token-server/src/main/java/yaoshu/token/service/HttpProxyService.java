package yaoshu.token.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * HTTP 代理转发工具服务  * <p>
 * 提供上游响应处理工具方法：响应体关闭、头过滤、字节拷贝。
 * 由 Relay 核心链路调用，不依赖 Spring 容器——静态工具方法。
 */
@Slf4j
public final class HttpProxyService {

    private HttpProxyService() {
    }

    /**
     * 优雅关闭上游响应体      */
    public static void closeResponseBodyGracefully(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException e) {
            SysLogService.sysError("failed to close response body: " + e.getMessage());
        }
    }

    /**
     * 判断上游响应头是否应拷贝到客户端响应      * <p>
     * Content-Length 由后续逻辑单独设置，X-Oneapi-Request-Id 保留本地实例 ID。
     * 上游的 X-Oneapi-Request-Id 值会存入 request attribute 供日志使用。
     */
    public static boolean shouldCopyUpstreamHeader(HttpServletRequest request, String headerName, String headerValue) {
        if ("Content-Length".equalsIgnoreCase(headerName)) {
            return false;
        }
        if (request != null && "X-Oneapi-Request-Id".equalsIgnoreCase(headerName) && headerValue != null) {
            request.setAttribute("X-Upstream-Request-Id", headerValue);
            return false;
        }
        return true;
    }

    /**
     * 将响应字节数据写入客户端响应      * <p>
     * 翻译说明：Go 使用 io.Copy + gin.Context.Writer。Java 使用 HttpServletResponse.getOutputStream()。
     * 先设置响应头（从 upstreamHeaders 拷贝），再设置 Content-Length，最后写入 body。
     * 顺序与 Go 保持一致——Header 必须在 writeHeader 之前设置，避免 httpClient 解析混乱。
     *
     * @param response       HttpServletResponse
     * @param upstreamHeaders 上游响应头（可为 null）
     * @param statusCode     上游 HTTP 状态码（0 或负数时默认 200）
     * @param data           响应 body 字节
     */
    public static void ioCopyBytesGracefully(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, String> upstreamHeaders,
            int statusCode,
            byte[] data) throws IOException {
        if (response == null) {
            return;
        }

        // 拷贝上游响应头（在 writeHeader 之前设置）
        if (upstreamHeaders != null) {
            for (Map.Entry<String, String> entry : upstreamHeaders.entrySet()) {
                if (!shouldCopyUpstreamHeader(request, entry.getKey(), entry.getValue())) {
                    continue;
                }
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // 设置 Content-Length（必须在 writeHeader 之前）
        response.setContentLength(data != null ? data.length : 0);

        // 写入状态码（此时发送 headers）
        if (statusCode > 0) {
            response.setStatus(statusCode);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        // 写入 body
        if (data != null && data.length > 0) {
            try (InputStream body = new ByteArrayInputStream(data);
                 OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = body.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
        }
    }
}
