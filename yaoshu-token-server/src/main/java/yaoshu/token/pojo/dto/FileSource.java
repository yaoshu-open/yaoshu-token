package yaoshu.token.pojo.dto;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 文件来源抽象接口  * <p>
 * 支持 URL 和 Base64 两种文件来源，提供懒加载和缓存机制。
 * Go 使用接口 + struct 嵌入，Java 使用抽象类替代。
 */
public abstract class FileSource {

    // ——— 缓存与注册状态 ———
    CachedFileData cachedData;
    boolean cacheLoaded;
    boolean registered;
    final ReentrantLock mu = new ReentrantLock();

    public abstract boolean isURL();
    public abstract String getIdentifier();
    public abstract String getRawData();
    public abstract void clearRawData();

    public void setCache(CachedFileData data) {
        this.cachedData = data;
        this.cacheLoaded = true;
    }

    public CachedFileData getCache() {
        return cachedData;
    }

    public boolean hasCache() {
        return cacheLoaded && cachedData != null;
    }

    public void clearCache() {
        if (cachedData != null) {
            cachedData.close();
        }
        cachedData = null;
        cacheLoaded = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public ReentrantLock mu() {
        return mu;
    }

    // ——— URLSource ———
    public static class URLSource extends FileSource {
        private String url;

        public URLSource() {
        }

        public URLSource(String url) {
            this.url = url;
        }

        @Override
        public boolean isURL() {
            return true;
        }

        @Override
        public String getIdentifier() {
            if (url != null && url.length() > 100) {
                return url.substring(0, 100) + "...";
            }
            return url;
        }

        @Override
        public String getRawData() {
            return url;
        }

        @Override
        public void clearRawData() {
            // URL 不清除原始数据
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    // ——— Base64Source ———
    public static class Base64Source extends FileSource {
        private String base64Data;
        private String mimeType;

        public Base64Source() {
        }

        public Base64Source(String base64Data, String mimeType) {
            this.base64Data = base64Data;
            this.mimeType = mimeType;
        }

        @Override
        public boolean isURL() {
            return false;
        }

        @Override
        public String getIdentifier() {
            if (base64Data != null && base64Data.length() > 50) {
                return "base64:" + base64Data.substring(0, 50) + "...";
            }
            return "base64:" + base64Data;
        }

        @Override
        public String getRawData() {
            return base64Data;
        }

        @Override
        public void clearRawData() {
            if (base64Data != null && base64Data.length() > 1024) {
                base64Data = "";
            }
        }

        public String getBase64Data() {
            return base64Data;
        }

        public void setBase64Data(String base64Data) {
            this.base64Data = base64Data;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    // ——— 工厂方法 ———
    public static URLSource newURLFileSource(String url) {
        return new URLSource(url);
    }

    public static Base64Source newBase64FileSource(String base64Data, String mimeType) {
        return new Base64Source(base64Data, mimeType);
    }

    public static FileSource newFileSourceFromData(String data, String mimeType) {
        if (data != null && (data.startsWith("http://") || data.startsWith("https://"))) {
            return newURLFileSource(data);
        }
        return newBase64FileSource(data, mimeType);
    }
}
