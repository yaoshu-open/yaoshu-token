package yaoshu.token.relay.helper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.StreamStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SSE 流扫描器  * <p>
 * 核心流程：从上游 HttpResponse InputStream 逐行读取 SSE 数据 →
 * 解析 data:/event:/id: 协议 → 回调 dataHandler → 写入 HttpServletResponse。
 * <p>
 * 与 Go 实现的差异：Go 使用 3 个 goroutine（scanner + dataHandler + ping）并发处理；
 * Java 版简化为主线程单线读取+写入，Ping 由 ApiRequestExecutor 独立线程管理，
 * 避免复杂并发同步开销。
 */
@Slf4j
public final class StreamScanner {

    private StreamScanner() {
    }

    /**
     * SSE 行处理回调接口
     */
    @FunctionalInterface
    public interface DataHandler {
        /**
         * 处理一条 SSE data 行
         *
         * @param data 去除 "data: " 前缀后的数据内容
         * @return true 继续扫描，false 停止
         */
        boolean handle(String data) throws Exception;
    }

    /**
     * 启动 SSE 流扫描，阻塞直到流结束或超时      *
     * @param resp        上游 HTTP 响应 InputStream
     * @param info        Relay 上下文
     * @param dataHandler 数据行回调
     * @param response    HttpServletResponse（用于客户端断开检测）
     */
    public static void scan(
            InputStream resp,
            RelayInfo info,
            DataHandler dataHandler,
            HttpServletResponse response) throws Exception {

        if (resp == null || dataHandler == null) return;

        // 初始化流状态
        if (info.getStreamStatus() == null) {
            info.setStreamStatus(new StreamStatus());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp, StandardCharsets.UTF_8))) {

            String line;
            StringBuilder dataBuffer = new StringBuilder();
            String currentEvent = null;
            String currentId = null;

            while ((line = reader.readLine()) != null) {
                // 客户端断开检测
                if (response != null && response.isCommitted()) {
                    // response 已提交但客户端可能已断开，继续尝试读取
                }

                // 空行 → 事件边界
                if (line.isEmpty()) {
                    if (dataBuffer.length() > 0) {
                        String data = dataBuffer.toString();
                        dataBuffer.setLength(0);

                        info.setFirstResponseTime();
                        info.setReceivedResponseCount(info.getReceivedResponseCount() + 1);

                        if (!dataHandler.handle(data)) {
                            return;
                        }
                    }
                    continue;
                }

                // data: 行
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append('\n');
                        }
                        dataBuffer.append(data);
                    }
                    continue;
                }

                // event: 行
                if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                    continue;
                }

                // id: 行
                if (line.startsWith("id:")) {
                    currentId = line.substring(3).trim();
                    continue;
                }

                // : PING 响应（上游发回的心跳）
                if (line.equals(": PING") || line.startsWith(": ping")) {
                    continue;
                }

                // [DONE] 标记（在 data: 行内）
                if (line.contains("[DONE]")) {
                    log.debug("SSE stream received [DONE]");
                    return;
                }

                // 跳过不足 6 字符的行（Go 逻辑）
                if (line.length() < 6) {
                    continue;
                }
            }
        }
    }
}
