package yaoshu.token.relay.channel.task.gemini;

import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.Part;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Base64;

/**
 * Task Gemini 图片中转处理器  * <p>
 * 提供 Veo 视频生成任务的图片输入提取与解析。
 */
@Slf4j
public class TaskGeminiImageHandler {

    private static final int MAX_VEO_IMAGE_SIZE = 20 * 1024 * 1024; // 20 MB

    /**
     * 从 multipart 表单提取图片      * <p>
     * 读取第一个 input_reference 文件并返回 VeoImageInput。
     */
    public static TaskGeminiDTO.VeoImageInput extractMultipartImage(Part filePart) throws Exception {
        if (filePart == null || filePart.getSize() == 0) {
            return null;
        }
        if (filePart.getSize() > MAX_VEO_IMAGE_SIZE) {
            return null;
        }

        byte[] fileBytes;
        try (InputStream is = filePart.getInputStream()) {
            fileBytes = is.readAllBytes();
        }

        String mimeType = filePart.getContentType();
        if (mimeType == null || mimeType.isEmpty() || "application/octet-stream".equals(mimeType)) {
            mimeType = URLConnection.guessContentTypeFromName(filePart.getSubmittedFileName());
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
        }

        TaskGeminiDTO.VeoImageInput input = new TaskGeminiDTO.VeoImageInput();
        input.setBytesBase64Encoded(Base64.getEncoder().encodeToString(fileBytes));
        input.setMimeType(mimeType);
        return input;
    }

    /**
     * 解析图片输入字符串      * <p>
     * 支持 data URI 格式和原始 base64 格式。
     */
    public static TaskGeminiDTO.VeoImageInput parseImageInput(String imageStr) {
        if (imageStr == null) return null;
        imageStr = imageStr.trim();
        if (imageStr.isEmpty()) return null;

        if (imageStr.startsWith("data:")) {
            return parseDataURI(imageStr);
        }

        try {
            byte[] raw = Base64.getDecoder().decode(imageStr);
            String mimeType = URLConnection.guessContentTypeFromName("");
            TaskGeminiDTO.VeoImageInput input = new TaskGeminiDTO.VeoImageInput();
            input.setBytesBase64Encoded(imageStr);
            input.setMimeType(mimeType != null ? mimeType : "image/jpeg");
            return input;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 data URI 格式图片      * <p>
     * 格式：data:image/png;base64,iVBOR...
     */
    private static TaskGeminiDTO.VeoImageInput parseDataURI(String uri) {
        String rest = uri.substring(5); // remove "data:"
        int idx = rest.indexOf(",");
        if (idx == -1) return null;

        String meta = rest.substring(0, idx); // image/png;base64
        String data = rest.substring(idx + 1);

        String mimeType = "image/jpeg";
        String[] metaParts = meta.split(";");
        if (metaParts.length > 0 && !metaParts[0].isEmpty()) {
            mimeType = metaParts[0];
        }

        TaskGeminiDTO.VeoImageInput input = new TaskGeminiDTO.VeoImageInput();
        input.setBytesBase64Encoded(data);
        input.setMimeType(mimeType);
        return input;
    }
}
