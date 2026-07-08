package yaoshu.token.relay.channel.volcengine;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 火山引擎二进制协议  * <p>
 * 火山引擎 TTS/ASR 使用自定义二进制 WebSocket 协议。
 * 消息格式：Header(4字节) + 可选字段(EventType/SessionID/Sequence/Payload)。
 */
@Slf4j
public class VolcEngineProtocolsPlaceholder {

    // ======================== 常量 ========================

    public static final int MSG_TYPE_FULL_CLIENT_REQUEST = 0b0001;
    public static final int MSG_TYPE_AUDIO_ONLY_CLIENT = 0b0010;
    public static final int MSG_TYPE_FULL_SERVER_RESPONSE = 0b1001;
    public static final int MSG_TYPE_AUDIO_ONLY_SERVER = 0b1011;
    public static final int MSG_TYPE_FRONT_END_RESULT_SERVER = 0b1100;
    public static final int MSG_TYPE_ERROR = 0b1111;

    public static final int MSG_TYPE_FLAG_NO_SEQ = 0;
    public static final int MSG_TYPE_FLAG_POSITIVE_SEQ = 0b1;
    public static final int MSG_TYPE_FLAG_NEGATIVE_SEQ = 0b11;
    public static final int MSG_TYPE_FLAG_WITH_EVENT = 0b100;

    public static final int VERSION_1 = 1;
    public static final int HEADER_SIZE_4 = 1;
    public static final int SERIALIZATION_JSON = 0b1;
    public static final int COMPRESSION_NONE = 0;

    // ======================== 事件类型 ========================

    public static final int EVENT_START_CONNECTION = 1;
    public static final int EVENT_FINISH_CONNECTION = 2;
    public static final int EVENT_CONNECTION_STARTED = 50;
    public static final int EVENT_CONNECTION_FAILED = 51;
    public static final int EVENT_CONNECTION_FINISHED = 52;
    public static final int EVENT_START_SESSION = 100;
    public static final int EVENT_CANCEL_SESSION = 101;
    public static final int EVENT_FINISH_SESSION = 102;
    public static final int EVENT_SESSION_STARTED = 150;
    public static final int EVENT_SESSION_CANCELED = 151;
    public static final int EVENT_SESSION_FINISHED = 152;
    public static final int EVENT_SESSION_FAILED = 153;
    public static final int EVENT_USAGE_RESPONSE = 154;
    public static final int EVENT_TASK_REQUEST = 200;
    public static final int EVENT_UPDATE_CONFIG = 201;
    public static final int EVENT_TTS_SENTENCE_START = 350;
    public static final int EVENT_TTS_SENTENCE_END = 351;
    public static final int EVENT_TTS_RESPONSE = 352;
    public static final int EVENT_TTS_ENDED = 359;

    // ======================== 消息结构 ========================

    @Data
    public static class Message {
        private int version = VERSION_1;
        private int headerSize = HEADER_SIZE_4;
        private int msgType;
        private int msgTypeFlag;
        private int serialization = SERIALIZATION_JSON;
        private int compression = COMPRESSION_NONE;
        private int eventType;
        private String sessionID;
        private String connectID;
        private int sequence;
        private long errorCode;
        private byte[] payload;

        /**
         * 序列化为二进制          */
        public byte[] marshal() throws Exception {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();

            // Header: 3 bytes + padding to headerSize*4
            buf.write((version << 4) | headerSize);
            buf.write((msgType << 4) | msgTypeFlag);
            buf.write((serialization << 4) | compression);

            // Padding to headerSize*4 (default 4)
            int headerBytes = 4 * headerSize;
            int padding = headerBytes - 3;
            for (int i = 0; i < padding; i++) {
                buf.write(0);
            }

            // Sequence / ErrorCode（按 Go readers() 顺序，与 fromBytes 一致）
            if (msgTypeFlag == MSG_TYPE_FLAG_POSITIVE_SEQ || msgTypeFlag == MSG_TYPE_FLAG_NEGATIVE_SEQ) {
                writeInt32BE(buf, sequence);
            }
            if (msgType == MSG_TYPE_ERROR) {
                writeInt32BE(buf, (int) errorCode);
            }

            // Event + SessionID（WITH_EVENT flag）
            if (msgTypeFlag == MSG_TYPE_FLAG_WITH_EVENT) {
                writeInt32BE(buf, eventType);
                writeSessionID(buf);
            }

            // Payload
            if (payload != null) {
                writeInt32BE(buf, payload.length);
                buf.write(payload);
            } else {
                writeInt32BE(buf, 0);
            }

            return buf.toByteArray();
        }

        private void writeSessionID(ByteArrayOutputStream buf) {
            if (eventType == EVENT_START_CONNECTION || eventType == EVENT_FINISH_CONNECTION
                    || eventType == EVENT_CONNECTION_STARTED || eventType == EVENT_CONNECTION_FAILED) {
                return;
            }
            byte[] sidBytes = sessionID != null ? sessionID.getBytes() : new byte[0];
            writeInt32BE(buf, sidBytes.length);
            buf.writeBytes(sidBytes);
        }
    }

    /**
     * 创建 FullClientRequest 消息      */
    public static Message newFullClientRequest(byte[] payload) {
        Message msg = new Message();
        msg.setMsgType(MSG_TYPE_FULL_CLIENT_REQUEST);
        msg.setMsgTypeFlag(MSG_TYPE_FLAG_NO_SEQ);
        msg.setPayload(payload);
        return msg;
    }

    /**
     * 从二进制数据解析消息      */
    public static Message fromBytes(byte[] data) throws Exception {
        if (data.length < 3) {
            throw new IllegalArgumentException("data too short: expected at least 3 bytes, got " + data.length);
        }

        int typeAndFlag = data[1] & 0xFF;
        int msgType = (typeAndFlag >> 4) & 0xFF;
        int msgTypeFlag = typeAndFlag & 0x0F;

        Message msg = new Message();
        msg.setMsgType(msgType);
        msg.setMsgTypeFlag(msgTypeFlag);

        // 解析 header
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        byte vh = buf.get();
        msg.setVersion((vh >> 4) & 0x0F);
        msg.setHeaderSize(vh & 0x0F);
        buf.get(); // typeAndFlag
        byte sc = buf.get();
        msg.setSerialization((sc >> 4) & 0x0F);
        msg.setCompression(sc & 0x0F);

        // Skip padding
        int headerBytes = 4 * msg.getHeaderSize();
        int paddingSize = headerBytes - 3;
        if (paddingSize > 0) {
            buf.position(buf.position() + paddingSize);
        }

        // 按 Go readers() 顺序解析字段
        // 1. Sequence / ErrorCode（根据 msgType + msgTypeFlag）
        if (msgType == MSG_TYPE_FULL_CLIENT_REQUEST || msgType == MSG_TYPE_FULL_SERVER_RESPONSE
                || msgType == MSG_TYPE_FRONT_END_RESULT_SERVER
                || msgType == MSG_TYPE_AUDIO_ONLY_CLIENT || msgType == MSG_TYPE_AUDIO_ONLY_SERVER) {
            if (msgTypeFlag == MSG_TYPE_FLAG_POSITIVE_SEQ || msgTypeFlag == MSG_TYPE_FLAG_NEGATIVE_SEQ) {
                msg.setSequence(buf.getInt());
            }
        } else if (msgType == MSG_TYPE_ERROR) {
            // uint32 → long
            msg.setErrorCode(buf.getInt() & 0xFFFFFFFFL);
        } else {
            throw new IllegalArgumentException("unsupported message type: " + msgType);
        }

        // 2. Event + SessionID + ConnectID（WITH_EVENT flag）
        if (msgTypeFlag == MSG_TYPE_FLAG_WITH_EVENT) {
            msg.setEventType(buf.getInt());
            readSessionID(buf, msg);
            readConnectID(buf, msg);
        }

        // 3. Payload
        if (buf.remaining() >= 4) {
            int payloadSize = buf.getInt();
            if (payloadSize > 0 && buf.remaining() >= payloadSize) {
                byte[] payload = new byte[payloadSize];
                buf.get(payload);
                msg.setPayload(payload);
            }
        }

        return msg;
    }

    /** 读取 SessionID*/
    private static void readSessionID(ByteBuffer buf, Message msg) {
        int eventType = msg.getEventType();
        if (eventType == EVENT_START_CONNECTION || eventType == EVENT_FINISH_CONNECTION
                || eventType == EVENT_CONNECTION_STARTED || eventType == EVENT_CONNECTION_FAILED
                || eventType == EVENT_CONNECTION_FINISHED) {
            return;
        }
        if (buf.remaining() < 4) return;
        int size = buf.getInt();
        if (size > 0 && buf.remaining() >= size) {
            byte[] sid = new byte[size];
            buf.get(sid);
            msg.setSessionID(new String(sid));
        }
    }

    /** 读取 ConnectID*/
    private static void readConnectID(ByteBuffer buf, Message msg) {
        int eventType = msg.getEventType();
        if (eventType != EVENT_CONNECTION_STARTED && eventType != EVENT_CONNECTION_FAILED
                && eventType != EVENT_CONNECTION_FINISHED) {
            return;
        }
        if (buf.remaining() < 4) return;
        int size = buf.getInt();
        if (size > 0 && buf.remaining() >= size) {
            byte[] cid = new byte[size];
            buf.get(cid);
            msg.setConnectID(new String(cid));
        }
    }

    /** 大端序写入 int32 */
    private static void writeInt32BE(ByteArrayOutputStream buf, int value) {
        buf.write((value >> 24) & 0xFF);
        buf.write((value >> 16) & 0xFF);
        buf.write((value >> 8) & 0xFF);
        buf.write(value & 0xFF);
    }
}
