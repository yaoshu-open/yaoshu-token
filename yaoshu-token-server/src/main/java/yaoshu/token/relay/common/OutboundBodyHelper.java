package yaoshu.token.relay.common;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.service.BodyStorageService;
import yaoshu.token.service.BodyStorageService.BodyStorage;

import java.io.*;

/**
 * 上游请求体构建工具  * <p>
 * 将已序列化的 JSON 请求体包装为 BodyStorage，当启用磁盘缓存且负载超过阈值时，
 * 数据写入临时文件，释放堆内存等待上游响应（对大 base64 负载尤为重要）。
 */
@Slf4j
public final class OutboundBodyHelper {

    private OutboundBodyHelper() {
    }

    /**
     * <p>
     * 调用方必须在上游请求完成后 close() 返回的 closer（通常用 try-finally），
     * 以释放磁盘文件/内存计数。
     *
     * @param data 已序列化的上游请求体
     * @return 结果，包含 reader / size / closer
     */
    public static OutboundBodyResult createOutboundJSONBody(byte[] data) throws IOException {
        BodyStorage storage = BodyStorageService.createBodyStorage(data);

        // 将 BodyStorage 转换为 InputStream 适配器
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(storage.bytes());
        InputStream body = new ReaderOnlyInputStream(bis);
        long size = storage.size();
        Closeable closer = storage;

        return new OutboundBodyResult(body, size, closer);
    }

    /**
     * <p>
     * Go struct{ io.Reader }{r} 语义：仅暴露 Reader 接口，防止 HTTP transport 看到 Closer 后提前关闭。
     */
    static class ReaderOnlyInputStream extends InputStream {
        private final InputStream delegate;

        ReaderOnlyInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() {
            // 不透传 close()，防止 HTTP transport 过早关闭底层 BodyStorage
        }
    }

    /**
     * 返回结果 POJO      */
    public static class OutboundBodyResult {
        /** 上游请求体流（ReaderOnly 包装） */
        private final InputStream body;
        /** 请求体字节大小，用于设置 http.Request.ContentLength */
        private final long size;
        /** 释放资源（磁盘文件/内存计数），调用方必须 close */
        private final Closeable closer;

        OutboundBodyResult(InputStream body, long size, Closeable closer) {
            this.body = body;
            this.size = size;
            this.closer = closer;
        }

        public InputStream getBody() { return body; }
        public long getSize() { return size; }
        public Closeable getCloser() { return closer; }
    }
}
