package yaoshu.token.relay.helper;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.ChatCompletionsStreamResponse;
import yaoshu.token.pojo.dto.Usage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSE / WebSocket 响应辅助  * <p>
 * 提供 SSE 事件流头设置、FlushWriter、PingData、StringData、ObjectData、
 * ClaudeChunkData、Done 等流式输出基础能力。
 */
@Slf4j
public final class RelayCommonHelper {

    private RelayCommonHelper() {
    }

    // ======================== 常量 ========================

    public static final int INITIAL_SCANNER_BUFFER_SIZE = 64 << 10;   // 64KB
    public static final int DEFAULT_MAX_SCANNER_BUFFER_SIZE = 128 << 20; // 64MB
    public static final long DEFAULT_PING_INTERVAL_SECONDS = 10;

    // ======================== SSE 头设置 ========================

    /**
     * 设置 SSE 事件流响应头      */
    public static void setEventStreamHeaders(HttpServletResponse response) {
        if (response == null) return;
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    // ======================== FlushWriter ========================

    /**
     * Flush HttpServletResponse 输出流      */
    public static void flushWriter(HttpServletResponse response) throws IOException {
        if (response == null) return;
        response.getOutputStream().flush();
    }

    // ======================== Ping / String / Object 数据 ========================

    /**
     * 发送 SSE Ping 数据      */
    public static void pingData(HttpServletResponse response) throws IOException {
        if (response == null) return;
        response.getOutputStream().write(": PING\n\n".getBytes(StandardCharsets.UTF_8));
        flushWriter(response);
    }

    /**
     * 发送 SSE 字符串数据（data: xxx\n\n）      */
    public static void stringData(HttpServletResponse response, String str) throws IOException {
        if (response == null || str == null) return;
        OutputStream os = response.getOutputStream();
        os.write("data: ".getBytes(StandardCharsets.UTF_8));
        os.write(str.getBytes(StandardCharsets.UTF_8));
        os.write("\n\n".getBytes(StandardCharsets.UTF_8));
        flushWriter(response);
    }

    /**
     * 发送 JSON 对象数据（序列化后调用 stringData）      */
    public static void objectData(HttpServletResponse response, Object obj) throws IOException {
        if (response == null || obj == null) return;
        String json = Convert.toJSONString(obj);
        stringData(response, json);
    }

    /**
     * 发送 SSE [DONE] 标记      */
    public static void done(HttpServletResponse response) throws IOException {
        stringData(response, "[DONE]");
    }

    // ======================== Claude 格式 ========================

    /**
     * 发送 Claude SSE 数据块      * <p>
     * 格式：event: {eventType}\ndata: {data}\n（已验证，末尾无 \n\n）
     */
    public static void claudeChunkData(HttpServletResponse response, String eventType, String data) throws IOException {
        if (response == null) return;
        OutputStream os = response.getOutputStream();
        if (eventType != null && !eventType.isEmpty()) {
            os.write(("event: " + eventType + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (data != null) {
            os.write(("data: " + data + "\n").getBytes(StandardCharsets.UTF_8));
        }
        flushWriter(response);
    }

    /**
     * 发送 Claude Response 完整数据块      */
    public static void claudeData(HttpServletResponse response, Object claudeResponse) throws IOException {
        if (response == null || claudeResponse == null) return;
        String json = Convert.toJSONString(claudeResponse);
        // Go 原实现通过反射获取 resp.Type 字段，此处简化为 event + data 格式
        stringData(response, json);
    }

    // ======================== 响应 ID 生成 ========================

    /**
     * 生成 stream 响应 ID      */
    public static String getResponseID(String logID) {
        return "chatcmpl-" + logID;
    }

    /**
     * 生成 Realtime 事件 ID      */
    public static String getLocalRealtimeID(String logID) {
        return "evt_" + logID;
    }

    // ======================== 生成辅助响应 ========================

    /**
     * 生成空起始 chunk 响应      */
    public static ChatCompletionsStreamResponse generateStartEmptyResponse(
            String id, long createdAt, String model, String systemFingerprint) {
        ChatCompletionsStreamResponse resp = new ChatCompletionsStreamResponse();
        resp.setId(id);
        resp.setObject("chat.completion.chunk");
        resp.setCreated(createdAt);
        resp.setModel(model);
        resp.setSystemFingerprint(systemFingerprint);
        // 需要一个空 delta 告知客户端开始
        return resp;
    }

    /**
     * 生成 stop chunk 响应      */
    public static ChatCompletionsStreamResponse generateStopResponse(
            String id, long createdAt, String model, String finishReason) {
        ChatCompletionsStreamResponse resp = new ChatCompletionsStreamResponse();
        resp.setId(id);
        resp.setObject("chat.completion.chunk");
        resp.setCreated(createdAt);
        resp.setModel(model);
        // finish_reason 通过 choices 传递
        return resp;
    }

    /**
     * 生成最终 usage chunk 响应      */
    public static ChatCompletionsStreamResponse generateFinalUsageResponse(
            String id, long createdAt, String model, Usage usage) {
        ChatCompletionsStreamResponse resp = new ChatCompletionsStreamResponse();
        resp.setId(id);
        resp.setObject("chat.completion.chunk");
        resp.setCreated(createdAt);
        resp.setModel(model);
        resp.setUsage(usage);
        return resp;
    }
}
