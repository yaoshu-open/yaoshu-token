package yaoshu.token.relay.common;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式处理状态追踪  * <p>
 * 线程安全：使用 AtomicReference + synchronized 保护关键字段。
 */
public class StreamStatus {

    // ======================== 流结束原因枚举 ========================

    public enum EndReason {
        NONE(""),
        DONE("done"),
        TIMEOUT("timeout"),
        CLIENT_GONE("client_gone"),
        SCANNER_ERROR("scanner_error"),
        HANDLER_STOP("handler_stop"),
        EOF("eof"),
        PANIC("panic"),
        PING_FAIL("ping_fail");

        private final String value;

        EndReason(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // ======================== 错误记录 ========================

    public static class StreamErrorEntry {
        private final String message;
        private final LocalDateTime timestamp;

        public StreamErrorEntry(String message) {
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    // ======================== 字段 ========================

    private static final int MAX_STREAM_ERROR_ENTRIES = 20;

    private final AtomicReference<EndReason> endReason = new AtomicReference<>(EndReason.NONE);
    private volatile Throwable endError;
    private volatile boolean ended;

    private final List<StreamErrorEntry> errors = new ArrayList<>();
    private final AtomicInteger errorCount = new AtomicInteger(0);

    // ======================== 公共方法 ========================

    /**
     * 设置流结束原因（仅首次调用生效）      */
    public synchronized void setEndReason(EndReason reason, Throwable err) {
        if (!ended) {
            ended = true;
            endReason.set(reason);
            endError = err;
        }
    }

    /**
     * 记录软错误      */
    public synchronized void recordError(String msg) {
        errorCount.incrementAndGet();
        if (errors.size() < MAX_STREAM_ERROR_ENTRIES) {
            errors.add(new StreamErrorEntry(msg));
        }
    }

    /**
     * 是否有错误      */
    public boolean hasErrors() {
        return errorCount.get() > 0;
    }

    /**
     * 总错误数      */
    public int totalErrorCount() {
        return errorCount.get();
    }

    /**
     * 是否正常结束      */
    public boolean isNormalEnd() {
        EndReason reason = endReason.get();
        return reason == EndReason.DONE
                || reason == EndReason.EOF
                || reason == EndReason.HANDLER_STOP;
    }

    /**
     * 状态摘要      */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("reason=").append(endReason.get().getValue());
        if (endError != null) {
            sb.append(" end_error=\"").append(endError.getMessage()).append("\"");
        }
        int count = errorCount.get();
        if (count > 0) {
            sb.append(" soft_errors=").append(count);
        }
        return sb.toString();
    }

    // ======================== Getter ========================

    public EndReason getEndReason() { return endReason.get(); }
    public Throwable getEndError() { return endError; }
}
