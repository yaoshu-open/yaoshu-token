package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.DiskCacheConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 请求体存储服务（内存 + 磁盘）  * <p>
 * 🔴 Relay 核心链路：根据请求体大小自动选择内存或磁盘存储。
 */
@Slf4j
public final class BodyStorageService {

    private BodyStorageService() {
    }

    /** 请求体过大错误 */
    public static final String ERR_REQUEST_BODY_TOO_LARGE = "request body too large";

    /** BodyStorage 接口：可读、可定位、可关闭、可获取字节数据 */
    public interface BodyStorage extends Closeable {
        /** 读取数据 */
        int read(byte[] buf, int off, int len) throws IOException;
        /** 定位 */
        long seek(long offset, int whence) throws IOException;
        /** 获取全部字节 */
        byte[] bytes() throws IOException;
        /** 获取数据大小 */
        long size();
        /** 是否使用磁盘存储 */
        boolean isDisk();
    }

    /** 存储已关闭错误 */
    static class StorageClosedException extends IOException {
        StorageClosedException() { super("body storage is closed"); }
    }

    // ======================== 内存存储 ========================

    static class MemoryStorage implements BodyStorage {
        private final byte[] data;
        private int position;
        private final long size;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        MemoryStorage(byte[] data) {
            this.data = data;
            this.size = data.length;
            this.position = 0;
            DiskCacheConfig.incrementMemoryBuffers(this.size);
        }

        @Override
        public synchronized int read(byte[] buf, int off, int len) throws IOException {
            checkClosed();
            if (position >= size) return -1;
            int available = (int) Math.min(len, size - position);
            System.arraycopy(data, position, buf, off, available);
            position += available;
            return available;
        }

        @Override
        public synchronized long seek(long offset, int whence) throws IOException {
            checkClosed();
            long newPos;
            switch (whence) {
                case 0: newPos = offset; break;                    // SEEK_SET
                case 1: newPos = position + offset; break;          // SEEK_CUR
                case 2: newPos = size + offset; break;              // SEEK_END
                default: throw new IOException("invalid whence");
            }
            if (newPos < 0) newPos = 0;
            if (newPos > size) newPos = size;
            position = (int) newPos;
            return newPos;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                DiskCacheConfig.decrementMemoryBuffers(size);
            }
        }

        @Override
        public byte[] bytes() throws IOException {
            checkClosed();
            return data;
        }

        @Override
        public long size() { return size; }

        @Override
        public boolean isDisk() { return false; }

        private void checkClosed() throws IOException {
            if (closed.get()) throw new StorageClosedException();
        }
    }

    // ======================== 磁盘存储 ========================

    static class DiskStorage implements BodyStorage {
        private final RandomAccessFile file;
        private final Path filePath;
        private final long size;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        DiskStorage(byte[] data) throws IOException {
            // 使用磁盘缓存目录
            Path dir = Path.of(getCacheDir());
            Files.createDirectories(dir);
            this.filePath = dir.resolve("body-" + System.nanoTime() + ".tmp");
            this.file = new RandomAccessFile(filePath.toFile(), "rw");
            this.file.write(data);
            this.size = data.length;
            this.file.seek(0);
            DiskCacheConfig.incrementDiskFiles(this.size);
        }

        DiskStorage(InputStream reader, long maxBytes) throws IOException {
            Path dir = Path.of(getCacheDir());
            Files.createDirectories(dir);
            this.filePath = dir.resolve("body-" + System.nanoTime() + ".tmp");
            this.file = new RandomAccessFile(filePath.toFile(), "rw");

            byte[] buf = new byte[8192];
            long totalRead = 0;
            int n;
            while ((n = reader.read(buf)) != -1) {
                totalRead += n;
                if (totalRead > maxBytes) {
                    file.close();
                    Files.deleteIfExists(filePath);
                    throw new IOException(ERR_REQUEST_BODY_TOO_LARGE);
                }
                file.write(buf, 0, n);
            }
            this.size = totalRead;
            this.file.seek(0);
            DiskCacheConfig.incrementDiskFiles(this.size);
        }

        @Override
        public synchronized int read(byte[] buf, int off, int len) throws IOException {
            checkClosed();
            return file.read(buf, off, len);
        }

        @Override
        public synchronized long seek(long offset, int whence) throws IOException {
            checkClosed();
            long fileLen = file.length();
            long newPos;
            switch (whence) {
                case 0: newPos = offset; break;
                case 1: newPos = file.getFilePointer() + offset; break;
                case 2: newPos = fileLen + offset; break;
                default: throw new IOException("invalid whence");
            }
            if (newPos < 0) newPos = 0;
            file.seek(newPos);
            return newPos;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try { file.close(); } catch (IOException ignored) {}
                try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
                DiskCacheConfig.decrementDiskFiles(size);
            }
        }

        @Override
        public byte[] bytes() throws IOException {
            checkClosed();
            long currentPos = file.getFilePointer();
            file.seek(0);
            byte[] data = new byte[(int) size];
            file.readFully(data);
            file.seek(currentPos);
            return data;
        }

        @Override
        public long size() { return size; }

        @Override
        public boolean isDisk() { return true; }

        private void checkClosed() throws IOException {
            if (closed.get()) throw new StorageClosedException();
        }
    }

    // ======================== 工厂方法 ========================

    /** 获取磁盘缓存目录 */
    private static String getCacheDir() {
        String cachePath = DiskCacheConfig.getPath();
        if (cachePath == null || cachePath.isBlank()) {
            cachePath = System.getProperty("java.io.tmpdir");
        }
        return cachePath + "/yaoshu-token-body-cache";
    }

    /**
     * 根据数据大小创建合适的存储（自动选择内存或磁盘）
     */
    public static BodyStorage createBodyStorage(byte[] data) throws IOException {
        long size = data.length;
        long threshold = DiskCacheConfig.getThresholdBytes();

        if (DiskCacheConfig.isEnabled()
                && size >= threshold
                && DiskCacheConfig.isAvailable(size)) {
            try {
                DiskCacheConfig.incrementDiskCacheHits();
                return new DiskStorage(data);
            } catch (IOException e) {
                SysLogService.sysError("failed to create disk storage, falling back to memory: " + e.getMessage());
            }
        }

        DiskCacheConfig.incrementMemoryCacheHits();
        return new MemoryStorage(data);
    }

    /**
     * 从 InputStream 创建存储（用于大请求的流式处理）
     */
    public static BodyStorage createBodyStorageFromReader(InputStream reader, long contentLength, long maxBytes) throws IOException {
        long threshold = DiskCacheConfig.getThresholdBytes();

        if (DiskCacheConfig.isEnabled()
                && contentLength > 0
                && contentLength >= threshold
                && DiskCacheConfig.isAvailable(contentLength)) {
            try {
                DiskCacheConfig.incrementDiskCacheHits();
                return new DiskStorage(reader, maxBytes);
            } catch (IOException e) {
                if (ERR_REQUEST_BODY_TOO_LARGE.equals(e.getMessage())) {
                    throw e;
                }
                throw new IOException("disk storage creation failed: " + e.getMessage(), e);
            }
        }

        // 内存读取
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long totalRead = 0;
        while ((n = reader.read(buf)) != -1) {
            totalRead += n;
            if (totalRead > maxBytes) {
                throw new IOException(ERR_REQUEST_BODY_TOO_LARGE);
            }
            bos.write(buf, 0, n);
        }

        BodyStorage storage = createBodyStorage(bos.toByteArray());
        if (!storage.isDisk()) {
            DiskCacheConfig.incrementMemoryCacheHits();
        }
        return storage;
    }
}
