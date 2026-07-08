package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * 图片处理服务  * <p>
 * 核心方法：base64 图片解码、URL 图片下载、图片格式检测。
 */
@Slf4j
public class ImageService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /**
     * 解码 base64 图片数据      *
     * @param base64String base64 编码的图片数据（可能含 data URI 前缀）
     * @return 解码结果（宽高、格式、清理后的 base64）
     */
    public static ImageData decodeBase64ImageData(String base64String) throws Exception {
        // 去除 data URI 前缀
        if (base64String != null) {
            int idx = base64String.indexOf(",");
            if (idx != -1) {
                base64String = base64String.substring(idx + 1);
            }
        }

        if (base64String == null || base64String.isEmpty()) {
            throw new IllegalArgumentException("base64 string is empty");
        }

        byte[] decodedData = Base64.getDecoder().decode(base64String);
        ByteArrayInputStream reader = new ByteArrayInputStream(decodedData);

        BufferedImage image = ImageIO.read(reader);
        if (image == null) {
            throw new IllegalArgumentException("failed to decode image from base64 data");
        }

        String format = detectFormat(decodedData);
        return new ImageData(image.getWidth(), image.getHeight(), format, base64String);
    }

    /**
     * 解码 base64 文件数据（含 MIME 提取）      *
     * @return mimeType + 清理后的 base64
     */
    public static FileData decodeBase64FileData(String base64String) throws Exception {
        if (base64String == null) {
            throw new IllegalArgumentException("base64 string is null");
        }

        int idx = base64String.indexOf(",");
        if (idx == -1) {
            // 无 data URI 前缀
            ImageData imgData = decodeBase64ImageData(base64String);
            return new FileData("image/" + imgData.format, imgData.cleanBase64);
        }

        String mimeType = base64String.substring(0, idx);
        base64String = base64String.substring(idx + 1);

        int semiIdx = mimeType.indexOf(";");
        if (semiIdx != -1) {
            mimeType = mimeType.substring(0, semiIdx);
        }

        int colonIdx = mimeType.indexOf(":");
        if (colonIdx != -1) {
            mimeType = mimeType.substring(colonIdx + 1);
        } else {
            ImageData imgData = decodeBase64ImageData(base64String);
            return new FileData("image/" + imgData.format, imgData.cleanBase64);
        }

        return new FileData(mimeType, base64String);
    }

    /**
     * 从 URL 获取图片，返回 mimeType + base64 编码数据      */
    public static FileData getImageFromUrl(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("failed to download image: HTTP " + response.statusCode());
        }

        byte[] data = response.body();
        String mimeType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
        String base64 = Base64.getEncoder().encodeToString(data);

        return new FileData(mimeType, base64);
    }

    /** 检测图片格式 */
    private static String detectFormat(byte[] data) {
        if (data == null || data.length < 4) return "unknown";
        // PNG
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return "png";
        // JPEG
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "jpeg";
        // GIF
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) return "gif";
        // WebP
        if (data.length >= 12 && data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) return "webp";
        // BMP
        if (data[0] == 0x42 && data[1] == 0x4D) return "bmp";
        return "unknown";
    }

    // ======================== 返回值类型 ========================

    public record ImageData(int width, int height, String format, String cleanBase64) {}
    public record FileData(String mimeType, String base64) {}
}
