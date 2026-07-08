package yaoshu.token.relay.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import yaoshu.token.relay.common.RelayInfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * StreamScanner 单元测试 —— 模拟上游 SSE InputStream，验证 scan() 解析链路。
 * <p>
 * 纯单元测试，不依赖 Spring 上下文。覆盖：
 * <ol>
 * <li>正常多事件 SSE 流</li>
 * <li>空流 / 单字节流（对应工单"响应体 1 字节"场景）</li>
 * <li>[DONE] 终止</li>
 * <li>dataHandler 返回 false 提前终止</li>
 * </ol>
 */
@DisplayName("StreamScanner — SSE 流解析单元测试")
class StreamScannerTest {

    /**
     * 正常 SSE 流：3 个 data 事件 + [DONE]
     */
    @Test
    @DisplayName("正常 SSE 流：多个 data 事件")
    void scanNormalSseStream() throws Exception {
        String sse = """
                data: {"id":"1","choices":[{"delta":{"content":"Hello"}}]}

                data: {"id":"2","choices":[{"delta":{"content":" World"}}]}

                data: [DONE]

                """;
        List<String> received = scan(sse);

        // [DONE] 在 data: 行内也会被作为普通数据回调（共 3 条：2 内容 + 1 [DONE]）
        assertThat(received).hasSize(3);
        assertThat(received.get(0)).contains("\"content\":\"Hello\"");
        assertThat(received.get(1)).contains("\"content\":\" World\"");
        assertThat(received.get(2)).isEqualTo("[DONE]");
    }

    /**
     * SSE 流带 event: 和 id: 元数据行
     */
    @Test
    @DisplayName("SSE 流含 event/id 元数据")
    void scanSseWithEventAndId() throws Exception {
        String sse = """
                event: completion
                id: evt-001
                data: {"id":"1","choices":[{"delta":{"content":"Hi"}}]}

                """;
        List<String> received = scan(sse);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).contains("\"content\":\"Hi\"");
    }

    /**
     * 空 InputStream — 工单核心场景：上游返回空流时 StreamScanner 的行为。
     */
    @Test
    @DisplayName("空 InputStream：上游返回空流")
    void scanEmptyStream() throws Exception {
        List<String> received = scan("");

        // 空流不应触发任何回调
        assertThat(received).isEmpty();
    }

    /**
     * 单字节换行符 — 工单描述的 `\n` 场景。
     */
    @Test
    @DisplayName("单字节换行符：上游返回仅 \\n")
    void scanSingleNewline() throws Exception {
        List<String> received = scan("\n");

        // 换行符 = 空行 → dataBuffer.length()==0 → 不触发回调
        assertThat(received).isEmpty();
    }

    /**
     * 只有 [DONE] 标记的流（data: 行内）
     */
    @Test
    @DisplayName("仅含 [DONE] 的 data 行")
    void scanDoneOnly() throws Exception {
        String sse = """
                data: [DONE]

                """;
        List<String> received = scan(sse);

        // [DONE] 在 data: 行内，会被累积到 dataBuffer 并回调
        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo("[DONE]");
    }

    /**
     * dataHandler 返回 false，scan 提前终止。
     */
    @Test
    @DisplayName("dataHandler 返回 false 提前终止")
    void scanHandlerReturnsFalse() throws Exception {
        String sse = """
                data: {"id":"1","choices":[{"delta":{"content":"A"}}]}

                data: {"id":"2","choices":[{"delta":{"content":"B"}}]}

                """;

        List<String> received = new ArrayList<>();
        RelayInfo info = new RelayInfo();
        InputStream is = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        StreamScanner.scan(is, info, data -> {
            received.add(data);
            return false; // 首条后即终止
        }, null);

        assertThat(received).hasSize(1);
    }

    /**
     * 多个连续 data 行（无空行间隔）— 多行累积为一个事件。
     */
    @Test
    @DisplayName("多行 data（无空行间隔）累积为单事件")
    void scanMultiLineDataAccumulation() throws Exception {
        String sse = """
                data: {"id":"1",
                data: "content":"test"}
                
                """;
        List<String> received = scan(sse);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).contains("\"id\":\"1\"");
        assertThat(received.get(0)).contains("\"content\":\"test\"");
    }

    /**
     * null InputStream → 直接返回，不抛异常。
     */
    @Test
    @DisplayName("null InputStream：安全返回")
    void scanNullInputStream() throws Exception {
        List<String> received = new ArrayList<>();
        RelayInfo info = new RelayInfo();

        // 不应抛异常
        StreamScanner.scan(null, info, data -> {
            received.add(data);
            return true;
        }, null);

        assertThat(received).isEmpty();
    }

    /**
     * [DONE] 在非 data: 行出现 → 停止扫描。
     */
    @Test
    @DisplayName("[DONE] 在非 data 行中出现")
    void scanDoneInNonDataLine() throws Exception {
        String sse = """
                data: {"id":"1","choices":[{"delta":{"content":"X"}}]}

                [DONE]
                data: {"id":"2","choices":[{"delta":{"content":"Y"}}]}

                """;
        List<String> received = scan(sse);

        // [DONE] 非 data: 行 → StreamScanner 直接 return，不会处理后续 data
        assertThat(received).hasSize(1);
    }

    // ======================== 辅助方法 ========================

    /**
     * 用给定 SSE 文本创建 InputStream，执行 scan，返回收到的所有 data 字符串。
     */
    private static List<String> scan(String sseText) throws Exception {
        List<String> received = new ArrayList<>();
        RelayInfo info = new RelayInfo();
        InputStream is = new ByteArrayInputStream(sseText.getBytes(StandardCharsets.UTF_8));

        StreamScanner.scan(is, info, data -> {
            received.add(data);
            return true;
        }, null);

        return received;
    }
}
