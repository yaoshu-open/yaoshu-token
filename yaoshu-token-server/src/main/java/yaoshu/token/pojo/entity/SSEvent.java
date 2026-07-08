package yaoshu.token.pojo.entity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSE（Server-Sent Events）事件  * <p>
 * 基于 W3C Working Draft 29 October 2009
 */
@Data
public class SSEvent {

    private String event;
    private String id;
    private int retry;
    private String data;

    private final ReentrantLock mutex = new ReentrantLock();

    private static final String CONTENT_TYPE = "text/event-stream";
    private static final String NO_CACHE = "no-cache";

    /**
     * 写入 SSE 响应到 HttpServletResponse
     */
    public void render(HttpServletResponse response) throws IOException {
        writeContentType(response);
        encode(response);
    }

    /**
     * 设置 Content-Type 与 Cache-Control 响应头（线程安全）
     */
    public void writeContentType(HttpServletResponse response) {
        mutex.lock();
        try {
            response.setContentType(CONTENT_TYPE);
            if (!response.containsHeader("Cache-Control")) {
                response.setHeader("Cache-Control", NO_CACHE);
            }
        } finally {
            mutex.unlock();
        }
    }

    private void encode(HttpServletResponse response) throws IOException {
        writeData(response);
    }

    private void writeData(HttpServletResponse response) throws IOException {
        String content = data;
        if (content == null) {
            return;
        }

        // Go 的 dataReplacer：\n → \ndata: ，\r → \\r
        // 注意：SSE 格式要求每行以 "data: " 开头
        boolean isDataEvent = content.startsWith("data:");

        if (isDataEvent) {
            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().write("\n\n".getBytes(StandardCharsets.UTF_8));
        } else {
            String escaped = content
                    .replace("\n", "\ndata: ")
                    .replace("\r", "\\r");
            response.getOutputStream().write(("data: " + escaped + "\n\n").getBytes(StandardCharsets.UTF_8));
        }
        response.getOutputStream().flush();
    }
}
