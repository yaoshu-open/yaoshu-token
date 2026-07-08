package yaoshu.token.constant;

import java.util.Map;

/**
 * Task 任务常量  */
public final class TaskConstants {

    private TaskConstants() {
    }

    /* 任务平台 */
    public static final String TASK_PLATFORM_SUNO = "suno";
    public static final String TASK_PLATFORM_MIDJOURNEY = "mj";

    /* Suno 动作 */
    public static final String SUNO_ACTION_MUSIC = "MUSIC";
    public static final String SUNO_ACTION_LYRICS = "LYRICS";

    /* Task 动作 */
    public static final String TASK_ACTION_GENERATE = "generate";
    public static final String TASK_ACTION_TEXT_GENERATE = "textGenerate";
    public static final String TASK_ACTION_FIRST_TAIL_GENERATE = "firstTailGenerate";
    public static final String TASK_ACTION_REFERENCE_GENERATE = "referenceGenerate";
    public static final String TASK_ACTION_REMIX = "remixGenerate";

    /** Suno 模型 → 动作映射 */
    public static final Map<String, String> SUNO_MODEL_2_ACTION = Map.of(
            "suno_music", SUNO_ACTION_MUSIC,
            "suno_lyrics", SUNO_ACTION_LYRICS
    );

    /* 任务状态*/     public static final String TASK_STATUS_NOT_START = "NOT_START";
    public static final String TASK_STATUS_SUBMITTED = "SUBMITTED";
    public static final String TASK_STATUS_QUEUED = "QUEUED";
    public static final String TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String TASK_STATUS_FAILURE = "FAILURE";
    public static final String TASK_STATUS_SUCCESS = "SUCCESS";
    public static final String TASK_STATUS_UNKNOWN = "UNKNOWN";

    /** 任务查询批次上限*/
    public static final int TASK_QUERY_LIMIT = Integer.parseInt(System.getProperty("TASK_QUERY_LIMIT", "1000"));

    /** 任务超时分钟数*/
    public static final int TASK_TIMEOUT_MINUTES = Integer.parseInt(System.getProperty("TASK_TIMEOUT_MINUTES", "1440"));
}
