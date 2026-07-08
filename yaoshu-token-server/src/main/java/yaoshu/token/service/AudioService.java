package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * 音频处理服务  * <p>
 * 核心方法：base64 音频解码、音频格式检测。
 */
@Slf4j
public class AudioService {

    /**
     * 解码 base64 音频数据      *
     * @param base64String base64 编码的音频数据
     * @return 音频字节流
     */
    public static byte[] decodeBase64AudioData(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            throw new IllegalArgumentException("base64 audio string is empty");
        }

        // 去除 data URI 前缀
        int idx = base64String.indexOf(",");
        if (idx != -1) {
            base64String = base64String.substring(idx + 1);
        }

        return Base64.getDecoder().decode(base64String);
    }

    /**
     * 检测音频格式      * <p>
     * 通过文件头魔术字节判断音频格式。
     */
    public static String getAudioFormat(byte[] data) {
        if (data == null || data.length < 4) return "unknown";
        // MP3
        if (data[0] == (byte) 0xFF && (data[1] & 0xE0) == 0xE0) return "mp3";
        // WAV/RIFF
        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46) return "wav";
        // OGG
        if (data[0] == 0x4F && data[1] == 0x67 && data[2] == 0x67 && data[3] == 0x53) return "ogg";
        // FLAC
        if (data[0] == 0x66 && data[1] == 0x4C && data[2] == 0x61 && data[3] == 0x43) return "flac";
        // AAC/M4A
        if (data.length >= 8 && data[4] == 0x66 && data[5] == 0x74 && data[6] == 0x79 && data[7] == 0x70) return "aac";
        return "unknown";
    }

    /**
     * 获取音频 MIME 类型
     */
    public static String getAudioMimeType(String format) {
        if (format == null) return "audio/mpeg";
        return switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "aac", "m4a" -> "audio/aac";
            default -> "audio/mpeg";
        };
    }
}
