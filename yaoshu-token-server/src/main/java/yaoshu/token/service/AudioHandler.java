package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 音频时长提取服务  * <p>
 * 使用纯 Java 二进制解析获取音频文件的时长（秒），覆盖 MP3/FLAC/M4A/OGG/Opus/AAC。
 * WAV/AIFF 通过 javax.sound 解析。
 */
@Slf4j
public final class AudioHandler {

    private AudioHandler() {
    }

    // ---- MPEG 比特率表（MPEG-1 Layer 3, 单位 kbps） ----
    private static final int[][] MP3_BITRATES_MPEG1 = {
            {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448}, // Layer 1
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},     // Layer 2
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}};    // Layer 3
    private static final int[] MP3_SAMPLE_RATES_MPEG1 = {44100, 48000, 32000};
    private static final int[] MP3_SAMPLE_RATES_MPEG2 = {22050, 24000, 16000};
    private static final int[] MP3_SAMPLES_PER_FRAME_MPEG1_LAYER3 = {0, 384, 1152, 1152}; // ver1: layer1=384, layer2=1152, layer3=1152

    /**
     * 获取音频文件时长（秒）      *
     * @param in  音频文件输入流
     * @param ext 文件扩展名（如 ".mp3"、".wav"）
     * @return 时长（秒）；不支持格式返回 0
     */
    public static double getAudioDuration(InputStream in, String ext) {
        log.info("GetAudioDuration: ext={}", ext);
        double duration;
        try {
            switch (ext.toLowerCase()) {
                case ".wav" -> duration = getWavDuration(in);
                case ".aiff", ".aif", ".aifc" -> duration = getAiffDuration(in);
                case ".mp3" -> duration = getMP3Duration(in);
                case ".flac" -> duration = getFLACDuration(in);
                case ".m4a", ".mp4" -> duration = getM4ADuration(in);
                case ".ogg", ".oga" -> duration = getOGGDuration(in);
                case ".opus" -> duration = getOpusDuration(in);
                case ".aac" -> duration = getAACDuration(in);
                case ".webm" -> duration = getDurationFallback(in, ".webm");
                default -> {
                    log.warn("unsupported audio format: {}", ext);
                    return 0;
                }
            }
        } catch (Exception e) {
            log.error("failed to get audio duration for {}: {}", ext, e.getMessage());
            return 0;
        }
        log.info("GetAudioDuration: duration={}", duration);
        return duration;
    }

    // ==================== WAV / AIFF (javax.sound) ====================

    /** WAV 时长提取 */
    private static double getWavDuration(InputStream in) throws IOException, UnsupportedAudioFileException {
        AudioInputStream audioIn = AudioSystem.getAudioInputStream(in);
        AudioFormat format = audioIn.getFormat();
        long frameLength = audioIn.getFrameLength();
        if (frameLength <= 0 || format.getFrameRate() <= 0) {
            return getDurationFallback(in, ".wav");
        }
        return (double) frameLength / format.getFrameRate();
    }

    /** AIFF 时长提取 */
    private static double getAiffDuration(InputStream in) throws IOException, UnsupportedAudioFileException {
        AudioInputStream audioIn = AudioSystem.getAudioInputStream(in);
        AudioFormat format = audioIn.getFormat();
        long frameLength = audioIn.getFrameLength();
        if (frameLength <= 0 || format.getFrameRate() <= 0) {
            return getDurationFallback(in, ".aiff");
        }
        return (double) frameLength / format.getFrameRate();
    }

    // ==================== MP3 ====================

    /**
     * MP3 时长解析      * <p>
     * 通过扫描帧同步字逐帧累加时长。优先检测 Xing VBR 头获取 VBR 文件的总帧数。
     */
    private static double getMP3Duration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 4) return 0;

        // 检测 ID3v2 标签并跳过（前 6-10 字节含标签大小，位于偏移 6，4 字节 synchsafe int）
        int offset = skipID3v2(data);

        // 按 MPEG 帧头逐帧解析
        int frameCount = 0;
        double duration = 0;
        int maxFrames = 200000; // 最多扫描 20 万帧防止死循环（约 2 小时 320kbps 音频）
        while (offset + 4 <= data.length && frameCount < maxFrames) {
            // 寻找帧同步字 (0xFFE0)
            while (offset + 4 <= data.length
                    && (data[offset] != (byte) 0xFF || (data[offset + 1] & 0xE0) != 0xE0)) {
                offset++;
            }
            if (offset + 4 > data.length) break;

            int header = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);

            int versionIndex = (header >> 19) & 0x3;  // 00:MPEG2.5, 01:保留, 10:MPEG2, 11:MPEG1
            int layerIndex = (header >> 17) & 0x3;     // 01:Layer3, 10:Layer2, 11:Layer1
            int bitrateIndex = (header >> 12) & 0xF;
            int sampleRateIndex = (header >> 10) & 0x3;

            // 非法值：versionIndex==1 或 bitrateIndex==0 或 bitrateIndex==15 或 sampleRateIndex==3
            // 或者 layerIndex==0（保留值）
            if (versionIndex == 1 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3 || layerIndex == 0) {
                offset += 2;
                continue;
            }

            int sampleRate;
            if (versionIndex == 3) { // MPEG1
                sampleRate = MP3_SAMPLE_RATES_MPEG1[sampleRateIndex];
            } else { // MPEG2 or MPEG2.5
                sampleRate = MP3_SAMPLE_RATES_MPEG2[sampleRateIndex];
            }

            int bitrate = 0;
            if (versionIndex == 3) {
                bitrate = MP3_BITRATES_MPEG1[layerIndex - 1][bitrateIndex] * 1000;
            }

            int padding = (header >> 9) & 1;
            int frameSize;
            if (versionIndex == 3 && layerIndex == 1) { // MPEG1 Layer3
                frameSize = 144 * bitrate / sampleRate + padding;
            } else {
                // 简化：非 Layer3 跳过
                offset += 2;
                continue;
            }

            if (frameSize <= 0 || offset + frameSize > data.length) {
                offset += 2;
                continue;
            }

            // 检查是否是 Xing/VBR 头（first frame 之后）
            if (frameCount == 0 && offset + frameSize + 120 <= data.length) {
                int xingOffset = offset + (versionIndex == 3 ? 36 : 21); // MPEG1:36, MPEG2:21
                if (xingOffset + 8 <= data.length) {
                    String xingTag = new String(data, xingOffset, 4);
                    if ("Xing".equals(xingTag) || "Info".equals(xingTag)) {
                        int flags = readBigEndianInt(data, xingOffset + 4);
                        if ((flags & 0x01) != 0 && xingOffset + 12 <= data.length) {
                            long vbrFrames = readBigEndianInt(data, xingOffset + 8) & 0xFFFFFFFFL;
                            if (vbrFrames > 0 && sampleRate > 0) {
                                return (double) vbrFrames * MP3_SAMPLES_PER_FRAME_MPEG1_LAYER3[layerIndex] / sampleRate;
                            }
                        }
                    }
                }
            }

            double frameDuration = (double) MP3_SAMPLES_PER_FRAME_MPEG1_LAYER3[layerIndex] / sampleRate;
            duration += frameDuration;
            frameCount++;
            offset += frameSize;
        }

        return duration;
    }

    /** 跳过 ID3v2 标签头，返回音频数据起始偏移 */
    private static int skipID3v2(byte[] data) {
        if (data.length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            // synchsafe int: 4 字节，每字节只用低 7 位
            int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14)
                    | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            return 10 + size;
        }
        return 0;
    }

    // ==================== FLAC ====================

    /**
     * FLAC 时长解析      * <p>
     * 解析 STREAMINFO 块：min/max block size(2+2) + min/max frame size(3+3) +
     * sample rate(20bit) + channels(3bit) + bits per sample(5bit) + total samples(36bit)。
     */
    private static double getFLACDuration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 42) return 0;

        int offset = 0;
        // 跳过 "fLaC" 魔数 (4 bytes)
        if (data[0] != 'f' || data[1] != 'L' || data[2] != 'a' || data[3] != 'C') {
            return 0;
        }
        offset = 4;

        // 解析元数据块直到找到 STREAMINFO (type=0) 或数据结束
        while (offset + 4 <= data.length) {
            boolean isLast = (data[offset] & 0x80) != 0; // 1 bit
            int blockType = data[offset] & 0x7F;          // 7 bits
            int blockSize = ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);          // 24 bits big-endian
            offset += 4;

            if (blockType != 0) {
                offset += blockSize;
                if (isLast) break;
                continue;
            }

            // STREAMINFO: 34 bytes
            if (offset + 34 > data.length) return 0;

            // 读 total samples（最后一个字节的 36 位中的低36位，实际上是字节4-7的高60位中的36位）
            // Go: stream.Info.NSamples / stream.Info.SampleRate
            // 格式: 2 bytes min block + 2 bytes max block + 3 bytes min frame + 3 bytes max frame
            // + 20 bit sample rate + 3 bit channels + 5 bit bit depth + 36 bit total samples
            // 也就是偏移 10 开始: sample_rate(20bit) | channels(3bit) | bit_depth(5bit) | total_samples(36bit)
            long raw = ((long)(data[offset + 10] & 0xFF) << 56)
                    | ((long)(data[offset + 11] & 0xFF) << 48)
                    | ((long)(data[offset + 12] & 0xFF) << 40)
                    | ((long)(data[offset + 13] & 0xFF) << 32)
                    | ((long)(data[offset + 14] & 0xFF) << 24)
                    | ((long)(data[offset + 15] & 0xFF) << 16)
                    | ((long)(data[offset + 16] & 0xFF) << 8)
                    | (data[offset + 17] & 0xFF);

            int sampleRate = (int) (raw >> 44) & 0xFFFFF; // 20 bits
            long totalSamples = raw & 0xFFFFFFFFFFFL;      // 36 bits

            if (sampleRate > 0) {
                return (double) totalSamples / sampleRate;
            }
            return 0;
        }
        return 0;
    }

    // ==================== M4A / MP4 ====================

    /**
     * M4A/MP4 时长解析      * <p>
     * 解析 MP4 atom 树，在 moov/trak/mdia/mdhd 或 moov/mvhd 中找 duration 和 time_scale。
     * 优先使用 mvhd（movie header）的 duration/time_scale。
     */
    private static double getM4ADuration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 32) return 0;

        // 在 atom 树中搜索 moov→mvhd
        int moovStart = findAtom(data, 0, data.length, "moov");
        if (moovStart < 0) {
            // moov 可能在文件末尾（faststart），从结尾向前搜索
            moovStart = findAtom(data, 0, data.length, "moov");
        }
        if (moovStart < 0) return 0;

        int moovEnd = moovStart + readBigEndianInt(data, moovStart);
        if (moovEnd > data.length) moovEnd = data.length;

        // 查找 mvhd (movie header)
        int mvhdOff = findAtom(data, moovStart + 8, moovEnd, "mvhd");
        if (mvhdOff < 0) return 0;

        // mvhd layout: size(4) + type(4) + version(1) + flags(3) +
        // version0: creation(4) + modification(4) + time_scale(4) + duration(4)
        // version1: creation(8) + modification(8) + time_scale(4) + duration(8)
        int ver = data[mvhdOff + 8] & 0xFF;
        long timeScale = readBigEndianInt(data, mvhdOff + 20) & 0xFFFFFFFFL;
        long duration;
        if (ver == 1) {
            duration = readBigEndianLong(data, mvhdOff + 28);
        } else {
            duration = readBigEndianInt(data, mvhdOff + 24) & 0xFFFFFFFFL;
        }

        if (timeScale > 0) {
            return (double) duration / timeScale;
        }
        return 0;
    }

    /** 在 data[pos, end) 范围内查找四字符 atom type，返回 atom 起始偏移，未找到返回 -1 */
    private static int findAtom(byte[] data, int pos, int end, String type) {
        while (pos + 8 <= end) {
            long size = readBigEndianInt(data, pos) & 0xFFFFFFFFL;
            if (size < 8) size = end - pos; // 无效 size 时用剩余长度
            int atomEnd = (int) Math.min(pos + size, end);
            if (pos + 4 <= end
                    && data[pos + 4] == type.charAt(0)
                    && data[pos + 5] == type.charAt(1)
                    && data[pos + 6] == type.charAt(2)
                    && data[pos + 7] == type.charAt(3)) {
                return pos;
            }
            pos = atomEnd;
        }
        return -1;
    }

    // ==================== OGG / Vorbis ====================

    /**
     * OGG Vorbis 时长解析      * <p>
     * 解析 OGG 页面：最后一页的 granule position = 总采样数。
     * 从第一页的 Vorbis identification header 获取 sample rate。
     */
    private static double getOGGDuration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 58) return 0;

        int offset = 0;
        int sampleRate = 0;
        long lastGranule = 0;

        while (offset + 27 <= data.length) {
            // 检查 "OggS" 魔数
            if (data[offset] != 'O' || data[offset + 1] != 'g'
                    || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
                offset++;
                continue;
            }

            int headerType = data[offset + 5] & 0xFF; // 1=continued, 2=first page (BOS), 4=last page (EOS)
            long granulePos = readLittleEndianLong(data, offset + 6);
            if (granulePos > lastGranule) {
                lastGranule = granulePos;
            }

            int numSegments = data[offset + 26] & 0xFF;
            int segTableStart = offset + 27;
            int segTableEnd = segTableStart + numSegments;
            if (segTableEnd > data.length) break;

            // 计算页面数据大小
            int pageDataSize = 0;
            for (int i = segTableStart; i < segTableEnd; i++) {
                pageDataSize += data[i] & 0xFF;
            }

            int pageDataStart = segTableEnd;

            // 第一页：读 Vorbis identification header 获取 sample rate
            if ((headerType & 0x02) != 0 && sampleRate == 0
                    && pageDataStart + 12 <= data.length) {
                // Vorbis header: packet_type(1) + "vorbis"(6) + version(4) + channels(1) + sample_rate(4)
                int pktType = data[pageDataStart] & 0xFF;
                if (pktType == 1) {
                    sampleRate = readLittleEndianInt(data, pageDataStart + 12);
                }
            }

            offset = pageDataStart + pageDataSize;
        }

        if (sampleRate > 0 && lastGranule > 0) {
            return (double) lastGranule / sampleRate;
        }
        return 0;
    }

    // ==================== Opus ====================

    /**
     * Opus 时长解析      * <p>
     * Opus 固定 48kHz 采样率。解析 OGG 容器中所有页面的 granule position。
     * 需从第一页的 OpusHead 包中读取 pre-skip 并从 granule position 中减去。
     */
    private static double getOpusDuration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 47) return 0;

        int offset = 0;
        long lastGranule = 0;
        int preSkip = 0;

        while (offset + 27 <= data.length) {
            if (data[offset] != 'O' || data[offset + 1] != 'g'
                    || data[offset + 2] != 'g' || data[offset + 3] != 'S') {
                offset++;
                continue;
            }

            int headerType = data[offset + 5] & 0xFF;
            long granulePos = readLittleEndianLong(data, offset + 6);
            if (granulePos > lastGranule) {
                lastGranule = granulePos;
            }

            int numSegments = data[offset + 26] & 0xFF;
            int segTableStart = offset + 27;
            int segTableEnd = segTableStart + numSegments;
            if (segTableEnd > data.length) break;

            int pageDataSize = 0;
            for (int i = segTableStart; i < segTableEnd; i++) {
                pageDataSize += data[i] & 0xFF;
            }
            int pageDataStart = segTableEnd;

            // 第一页：读 OpusHead 获取 pre-skip
            if ((headerType & 0x02) != 0 && preSkip == 0
                    && pageDataStart + 19 <= data.length) {
                // "OpusHead" 魔数验证 (8 bytes)
                if (data[pageDataStart] == 'O' && data[pageDataStart + 1] == 'p'
                        && data[pageDataStart + 2] == 'u' && data[pageDataStart + 3] == 's') {
                    // pre-skip: 偏移 10, 2 bytes little-endian
                    preSkip = readLittleEndianShort(data, pageDataStart + 10);
                }
            }

            offset = pageDataStart + pageDataSize;
        }

        long effectiveGranule = lastGranule - preSkip;
        if (effectiveGranule > 0) {
            return (double) effectiveGranule / 48000.0; // Opus 采样率固定 48kHz
        }
        return 0;
    }

    // ==================== AAC ====================

    /**
     * AAC ADTS 时长解析      * <p>
     * 扫描全部 ADTS 帧，从第一帧获取采样率和采样频率索引。
     * 每帧 = 1024 采样（AAC LC）。duration = total_samples / sample_rate。
     */
    private static double getAACDuration(InputStream in) throws IOException {
        byte[] data = readAllBytes(in);
        if (data.length < 7) return 0;

        // AAC ADTS 采样频率索引 → Hz
        int[] aacSampleRates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000, 7350};

        int totalFrames = 0;
        int sampleRate = 0;
        int offset = 0;

        // 每帧 1024 采样（AAC LC，通过 ADTS protection_absent 判定是否 7 字节头）
        while (offset + 7 <= data.length) {
            // 寻找 ADTS 同步字 (0xFFF)
            while (offset + 2 <= data.length
                    && ((data[offset] & 0xFF) != 0xFF || (data[offset + 1] & 0xF0) != 0xF0)) {
                offset++;
            }
            if (offset + 7 > data.length) break;

            // ADTS header: sync(12) + version(1) + layer(2) + protection(1)
            // + profile(2) + sampleRateIdx(4) + private(1) + channelConf(3) + ...
            int sampleRateIdx = (data[offset + 2] >> 2) & 0xF;
            int protectionBit = (data[offset + 1] & 0x01);

            if (sampleRate == 0 && sampleRateIdx < aacSampleRates.length) {
                sampleRate = aacSampleRates[sampleRateIdx];
            }

            // 帧长度在第 3-4 字节
            int frameLen = ((data[offset + 3] & 0x03) << 11)
                    | ((data[offset + 4] & 0xFF) << 3)
                    | ((data[offset + 5] & 0xE0) >> 5);

            if (frameLen < 7 || offset + frameLen > data.length) {
                offset += 2;
                continue;
            }

            totalFrames++;
            offset += frameLen;
        }

        if (sampleRate > 0 && totalFrames > 0) {
            long totalSamples = (long) totalFrames * 1024;
            return (double) totalSamples / sampleRate;
        }
        return 0;
    }

    // ==================== 工具方法 ====================

    /** 读取整个 InputStream 为 byte[] */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** 4 字节 big-endian int */
    private static int readBigEndianInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    /** 8 字节 big-endian long */
    private static long readBigEndianLong(byte[] data, int offset) {
        return ((long) readBigEndianInt(data, offset) << 32)
                | (readBigEndianInt(data, offset + 4) & 0xFFFFFFFFL);
    }

    /** 8 字节 little-endian long */
    private static long readLittleEndianLong(byte[] data, int offset) {
        return ((long)(data[offset] & 0xFF))
                | ((long)(data[offset + 1] & 0xFF) << 8)
                | ((long)(data[offset + 2] & 0xFF) << 16)
                | ((long)(data[offset + 3] & 0xFF) << 24)
                | ((long)(data[offset + 4] & 0xFF) << 32)
                | ((long)(data[offset + 5] & 0xFF) << 40)
                | ((long)(data[offset + 6] & 0xFF) << 48)
                | ((long)(data[offset + 7] & 0xFF) << 56);
    }

    /** 4 字节 little-endian int */
    private static int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    /** 2 字节 little-endian short (unsigned) */
    private static int readLittleEndianShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /** 兜底：文件大小估算（WebM 等无法纯 Java 精确解析的格式），对应 getDurationFallback */
    private static double getDurationFallback(InputStream in, String ext) throws IOException {
        int available = in.available();
        if (available > 0) {
            return (double) available / (16 * 1024); // 假设 128kbps = 16KB/s
        }
        return 0;
    }
}
