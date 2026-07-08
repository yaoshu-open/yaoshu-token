package yaoshu.token.relay.constant;

/**
 * Relay 模式常量  * <p>
 * 定义所有 AI API 请求的转发模式枚举值，以及请求路径 → 模式的路由映射。
 */
public final class RelayModeEnum {

    private RelayModeEnum() {
    }

    // ======================== Relay 模式常量 ========================

    public static final int UNKNOWN = 0;
    public static final int CHAT_COMPLETIONS = 1;
    public static final int COMPLETIONS = 2;
    public static final int EMBEDDINGS = 3;
    public static final int MODERATIONS = 4;
    public static final int IMAGES_GENERATIONS = 5;
    public static final int IMAGES_EDITS = 6;
    public static final int EDITS = 7;

    /* Midjourney */
    public static final int MIDJOURNEY_IMAGINE = 8;
    public static final int MIDJOURNEY_DESCRIBE = 9;
    public static final int MIDJOURNEY_BLEND = 10;
    public static final int MIDJOURNEY_CHANGE = 11;
    public static final int MIDJOURNEY_SIMPLE_CHANGE = 12;
    public static final int MIDJOURNEY_NOTIFY = 13;
    public static final int MIDJOURNEY_TASK_FETCH = 14;
    public static final int MIDJOURNEY_TASK_IMAGE_SEED = 15;
    public static final int MIDJOURNEY_TASK_FETCH_BY_CONDITION = 16;
    public static final int MIDJOURNEY_ACTION = 17;
    public static final int MIDJOURNEY_MODAL = 18;
    public static final int MIDJOURNEY_SHORTEN = 19;
    public static final int SWAP_FACE = 20;
    public static final int MIDJOURNEY_UPLOAD = 21;
    public static final int MIDJOURNEY_VIDEO = 22;
    public static final int MIDJOURNEY_EDITS = 23;

    /* Audio */
    public static final int AUDIO_SPEECH = 24;        // TTS
    public static final int AUDIO_TRANSCRIPTION = 25;  // Whisper
    public static final int AUDIO_TRANSLATION = 26;    // Whisper

    /* Suno */
    public static final int SUNO_FETCH = 27;
    public static final int SUNO_FETCH_BY_ID = 28;
    public static final int SUNO_SUBMIT = 29;

    /* Video */
    public static final int VIDEO_FETCH_BY_ID = 30;
    public static final int VIDEO_SUBMIT = 31;

    public static final int RERANK = 32;
    public static final int RESPONSES = 33;
    public static final int REALTIME = 34;
    public static final int GEMINI = 35;
    public static final int RESPONSES_COMPACT = 36;

    // ======================== 路径 → 模式映射 ========================

    /**
     * 将请求路径映射为 Relay 模式      */
    public static int path2RelayMode(String path) {
        if (path == null) {
            return UNKNOWN;
        }
        if (path.startsWith("/v1/chat/completions") || path.startsWith("/pg/chat/completions")) {
            return CHAT_COMPLETIONS;
        } else if (path.startsWith("/v1/completions")) {
            return COMPLETIONS;
        } else if (path.startsWith("/v1/embeddings")) {
            return EMBEDDINGS;
        } else if (path.endsWith("embeddings")) {
            return EMBEDDINGS;
        } else if (path.startsWith("/v1/moderations")) {
            return MODERATIONS;
        } else if (path.startsWith("/v1/images/generations")) {
            return IMAGES_GENERATIONS;
        } else if (path.startsWith("/v1/images/edits")) {
            return IMAGES_EDITS;
        } else if (path.startsWith("/v1/edits")) {
            return EDITS;
        } else if (path.startsWith("/v1/responses/compact")) {
            return RESPONSES_COMPACT;
        } else if (path.startsWith("/v1/responses")) {
            return RESPONSES;
        } else if (path.startsWith("/v1/audio/speech")) {
            return AUDIO_SPEECH;
        } else if (path.startsWith("/v1/audio/transcriptions")) {
            return AUDIO_TRANSCRIPTION;
        } else if (path.startsWith("/v1/audio/translations")) {
            return AUDIO_TRANSLATION;
        } else if (path.startsWith("/v1/rerank")) {
            return RERANK;
        } else if (path.startsWith("/v1/realtime")) {
            return REALTIME;
        } else if (path.startsWith("/v1beta/models") || path.startsWith("/v1/models")) {
            return GEMINI;
        } else if (path.startsWith("/mj")) {
            return path2RelayModeMidjourney(path);
        }
        return UNKNOWN;
    }

    /**
     * Midjourney 路径 → 模式映射      */
    public static int path2RelayModeMidjourney(String path) {
        if (path.endsWith("/mj/submit/action")) {
            return MIDJOURNEY_ACTION;
        } else if (path.endsWith("/mj/submit/modal")) {
            return MIDJOURNEY_MODAL;
        } else if (path.endsWith("/mj/submit/shorten")) {
            return MIDJOURNEY_SHORTEN;
        } else if (path.endsWith("/mj/insight-face/swap")) {
            return SWAP_FACE;
        } else if (path.endsWith("/submit/upload-discord-images")) {
            return MIDJOURNEY_UPLOAD;
        } else if (path.endsWith("/mj/submit/imagine")) {
            return MIDJOURNEY_IMAGINE;
        } else if (path.endsWith("/mj/submit/video")) {
            return MIDJOURNEY_VIDEO;
        } else if (path.endsWith("/mj/submit/edits")) {
            return MIDJOURNEY_EDITS;
        } else if (path.endsWith("/mj/submit/blend")) {
            return MIDJOURNEY_BLEND;
        } else if (path.endsWith("/mj/submit/describe")) {
            return MIDJOURNEY_DESCRIBE;
        } else if (path.endsWith("/mj/notify")) {
            return MIDJOURNEY_NOTIFY;
        } else if (path.endsWith("/mj/submit/change")) {
            return MIDJOURNEY_CHANGE;
        } else if (path.endsWith("/mj/submit/simple-change")) {
            return MIDJOURNEY_SIMPLE_CHANGE;
        } else if (path.endsWith("/fetch")) {
            return MIDJOURNEY_TASK_FETCH;
        } else if (path.endsWith("/image-seed")) {
            return MIDJOURNEY_TASK_IMAGE_SEED;
        } else if (path.endsWith("/list-by-condition")) {
            return MIDJOURNEY_TASK_FETCH_BY_CONDITION;
        }
        return UNKNOWN;
    }

    /**
     * Suno 路径 → 模式映射      */
    public static int path2RelaySuno(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/fetch")) {
            return SUNO_FETCH;
        } else if ("GET".equalsIgnoreCase(method) && path.contains("/fetch/")) {
            return SUNO_FETCH_BY_ID;
        } else if (path.contains("/submit/")) {
            return SUNO_SUBMIT;
        }
        return UNKNOWN;
    }
}
