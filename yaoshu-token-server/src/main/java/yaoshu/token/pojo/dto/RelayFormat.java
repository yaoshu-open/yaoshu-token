package yaoshu.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Relay 格式常量  */
public final class RelayFormat {

    private RelayFormat() {
    }

    public static final String OPENAI = "openai";
    public static final String CLAUDE = "claude";
    public static final String GEMINI = "gemini";
    public static final String OPENAI_RESPONSES = "openai_responses";
    public static final String OPENAI_RESPONSES_COMPACTION = "openai_responses_compaction";
    public static final String OPENAI_AUDIO = "openai_audio";
    public static final String OPENAI_IMAGE = "openai_image";
    public static final String OPENAI_REALTIME = "openai_realtime";
    public static final String RERANK = "rerank";
    public static final String EMBEDDING = "embedding";

    public static final String TASK = "task";
    public static final String MJ_PROXY = "mj_proxy";
}
