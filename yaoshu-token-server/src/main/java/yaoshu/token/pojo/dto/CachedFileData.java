package yaoshu.token.pojo.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * 缓存的文件数据（支持内存和磁盘两种模式）  */
@Getter
@Setter
public class CachedFileData implements AutoCloseable {

    private String mimeType;
    private long size;
    private long diskSize;

    // 内存模式
    private String base64Data;

    // 磁盘模式
    private String diskPath;
    private boolean isDisk;
    private final java.util.concurrent.locks.ReentrantLock diskMu = new java.util.concurrent.locks.ReentrantLock();
    private boolean diskClosed;
    private boolean statDecremented;

    private Consumer<Long> onClose;

    /** 内存缓存构造 */
    public static CachedFileData newMemory(String base64Data, String mimeType, long size) {
        CachedFileData c = new CachedFileData();
        c.base64Data = base64Data;
        c.mimeType = mimeType;
        c.size = size;
        c.isDisk = false;
        return c;
    }

    /** 磁盘缓存构造 */
    public static CachedFileData newDisk(String diskPath, String mimeType, long size) {
        CachedFileData c = new CachedFileData();
        c.diskPath = diskPath;
        c.mimeType = mimeType;
        c.size = size;
        c.isDisk = true;
        return c;
    }

    /**
     * 获取 Base64 数据（磁盘模式从文件读取）
     */
    public String getBase64Data() {
        if (!isDisk) {
            return base64Data;
        }
        diskMu.lock();
        try {
            if (diskClosed) {
                throw new RuntimeException("disk cache already closed");
            }
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(diskPath)));
        } catch (java.io.IOException e) {
            throw new RuntimeException("failed to read from disk cache: " + diskPath, e);
        } finally {
            diskMu.unlock();
        }
    }

    public void setBase64Data(String data) {
        if (!isDisk) {
            this.base64Data = data;
        }
    }

    @Override
    public void close() {
        if (!isDisk) {
            base64Data = "";
            return;
        }
        diskMu.lock();
        try {
            if (diskClosed) {
                return;
            }
            diskClosed = true;
            if (diskPath != null && !diskPath.isEmpty()) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(diskPath));
                } catch (java.io.IOException ignored) {
                }
                if (!statDecremented && onClose != null) {
                    onClose.accept(diskSize);
                    statDecremented = true;
                }
            }
        } finally {
            diskMu.unlock();
        }
    }
}
