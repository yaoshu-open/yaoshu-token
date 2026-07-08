package yaoshu.token.relay.helper;

import yaoshu.token.relay.common.StreamStatus;

/**
 * 流式处理结果记录器  * <p>
 * 在每次 dataHandler 回调中传递，用于记录软错误、发出停止信号或标记正常完成。
 * StreamScannerHandler 在每次回调后检查 isStopped()。
 */
public class StreamResult {

    private final StreamStatus status;
    private boolean stopped;

    public StreamResult(StreamStatus status) {
        this.status = status;
    }

    /**
     * 记录软错误，流继续处理      */
    public void error(Exception err) {
        if (err == null) return;
        status.recordError(err.getMessage());
    }

    /**
     * 记录致命错误并停止流      */
    public void stop(Exception err) {
        if (err != null) {
            status.recordError(err.getMessage());
        }
        status.setEndReason(StreamStatus.EndReason.HANDLER_STOP, err);
        stopped = true;
    }

    /**
     * 标记处理正常完成      */
    public void done() {
        status.setEndReason(StreamStatus.EndReason.DONE, null);
        stopped = true;
    }

    /**
     * 本次回调中是否调用了 stop() 或 done()      */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 重置单次回调的停止标记      */
    void reset() {
        stopped = false;
    }
}
