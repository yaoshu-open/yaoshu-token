package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 文件解码服务  * <p>
 * 提供 URL 文件类型检测、MIME 类型映射能力。
 */
@Slf4j
public class FileDecoderService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /** 扩展名 → MIME 映射 */
    private static final Map<String, String> MIME_MAP = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"), Map.entry("webp", "image/webp"),
            Map.entry("tiff", "image/tiff"), Map.entry("svg", "image/svg+xml"),
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"),
            Map.entry("json", "application/json"), Map.entry("xml", "application/xml"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("mp4", "video/mp4"), Map.entry("avi", "video/x-msvideo"),
            Map.entry("webm", "video/webm"), Map.entry("mov", "video/quicktime")
    );

    /**
     * 从 URL 获取文件类型      * <p>
     * 返回 MIME type（如 image/jpeg），失败返回空字符串。
     */
    public static String getFileTypeFromUrl(String url, String... reasons) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("failed to download file from {}, status code: {}", url, response.statusCode());
                return null;
            }

            // 1. 检查 Content-Type header
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            contentType = contentType.trim();
            if (contentType.contains(";")) {
                contentType = contentType.substring(0, contentType.indexOf(";")).trim();
            }
            if (!contentType.isEmpty() && !"application/octet-stream".equals(contentType)) {
                return contentType;
            }

            // 2. 检查 Content-Disposition header
            String contentDisposition = response.headers().firstValue("Content-Disposition").orElse("");
            if (!contentDisposition.isEmpty()) {
                String mimeType = getMimeTypeFromContentDisposition(contentDisposition);
                if (mimeType != null) return mimeType;
            }

            // 3. 从 URL 路径推断扩展名
            return getMimeTypeFromUrl(url);

        } catch (Exception e) {
            log.error("failed to get file type from url: {}, error: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 根据扩展名获取 MIME 类型      */
    public static String getMimeTypeByExtension(String ext) {
        if (ext == null) return "application/octet-stream";
        return MIME_MAP.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }

    /** 从 Content-Disposition 提取 MIME */
    private static String getMimeTypeFromContentDisposition(String cd) {
        String[] parts = cd.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.toLowerCase().startsWith("filename=")) {
                String name = part.substring("filename=".length()).trim();
                // 去引号
                if (name.length() > 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1);
                }
                int dot = name.lastIndexOf(".");
                if (dot != -1 && dot + 1 < name.length()) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    String mt = getMimeTypeByExtension(ext);
                    if (!"application/octet-stream".equals(mt)) return mt;
                }
                break;
            }
        }
        return null;
    }

    /** 从 URL 路径推断 MIME */
    private static String getMimeTypeFromUrl(String url) {
        // 去掉查询参数
        String cleanedUrl = url;
        int q = cleanedUrl.indexOf("?");
        if (q != -1) cleanedUrl = cleanedUrl.substring(0, q);

        int slash = cleanedUrl.lastIndexOf("/");
        if (slash != -1 && slash + 1 < cleanedUrl.length()) {
            String last = cleanedUrl.substring(slash + 1);
            int dot = last.lastIndexOf(".");
            if (dot != -1 && dot + 1 < last.length()) {
                String ext = last.substring(dot + 1).toLowerCase();
                return getMimeTypeByExtension(ext);
            }
        }
        return "application/octet-stream";
    }
}
