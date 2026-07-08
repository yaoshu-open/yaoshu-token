package yaoshu.token.constant;

import java.util.Map;

/**
 * Midjourney 常量  */
public final class MidjourneyConstants {

    private MidjourneyConstants() {
    }

    /* 错误码 */
    public static final int MJ_ERROR_UNKNOWN = 5;
    public static final int MJ_REQUEST_ERROR = 4;

    /* MJ 动作 */
    public static final String MJ_ACTION_IMAGINE = "IMAGINE";
    public static final String MJ_ACTION_DESCRIBE = "DESCRIBE";
    public static final String MJ_ACTION_BLEND = "BLEND";
    public static final String MJ_ACTION_UPSCALE = "UPSCALE";
    public static final String MJ_ACTION_VARIATION = "VARIATION";
    public static final String MJ_ACTION_REROLL = "REROLL";
    public static final String MJ_ACTION_INPAINT = "INPAINT";
    public static final String MJ_ACTION_MODAL = "MODAL";
    public static final String MJ_ACTION_ZOOM = "ZOOM";
    public static final String MJ_ACTION_CUSTOM_ZOOM = "CUSTOM_ZOOM";
    public static final String MJ_ACTION_SHORTEN = "SHORTEN";
    public static final String MJ_ACTION_HIGH_VARIATION = "HIGH_VARIATION";
    public static final String MJ_ACTION_LOW_VARIATION = "LOW_VARIATION";
    public static final String MJ_ACTION_PAN = "PAN";
    public static final String MJ_ACTION_SWAP_FACE = "SWAP_FACE";
    public static final String MJ_ACTION_UPLOAD = "UPLOAD";
    public static final String MJ_ACTION_VIDEO = "VIDEO";
    public static final String MJ_ACTION_EDITS = "EDITS";

    /** Midjourney 模型 → 动作映射 */
    public static final Map<String, String> MIDJOURNEY_MODEL_2_ACTION = Map.ofEntries(
            Map.entry("mj_imagine", MJ_ACTION_IMAGINE),
            Map.entry("mj_describe", MJ_ACTION_DESCRIBE),
            Map.entry("mj_blend", MJ_ACTION_BLEND),
            Map.entry("mj_upscale", MJ_ACTION_UPSCALE),
            Map.entry("mj_variation", MJ_ACTION_VARIATION),
            Map.entry("mj_reroll", MJ_ACTION_REROLL),
            Map.entry("mj_modal", MJ_ACTION_MODAL),
            Map.entry("mj_inpaint", MJ_ACTION_INPAINT),
            Map.entry("mj_zoom", MJ_ACTION_ZOOM),
            Map.entry("mj_custom_zoom", MJ_ACTION_CUSTOM_ZOOM),
            Map.entry("mj_shorten", MJ_ACTION_SHORTEN),
            Map.entry("mj_high_variation", MJ_ACTION_HIGH_VARIATION),
            Map.entry("mj_low_variation", MJ_ACTION_LOW_VARIATION),
            Map.entry("mj_pan", MJ_ACTION_PAN),
            Map.entry("swap_face", MJ_ACTION_SWAP_FACE),
            Map.entry("mj_upload", MJ_ACTION_UPLOAD),
            Map.entry("mj_video", MJ_ACTION_VIDEO),
            Map.entry("mj_edits", MJ_ACTION_EDITS)
    );
}
