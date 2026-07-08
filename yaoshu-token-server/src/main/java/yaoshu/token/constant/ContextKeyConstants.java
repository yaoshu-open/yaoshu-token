package yaoshu.token.constant;

/**
 * 上下文键名常量  * <p>
 * 用于 HTTP 请求上下文中存取数据（request.setAttribute / request.getAttribute）。
 */
public final class ContextKeyConstants {

    private ContextKeyConstants() {
    }

    public static final String TOKEN_COUNT_META = "token_count_meta";
    public static final String PROMPT_TOKENS = "prompt_tokens";
    public static final String ESTIMATED_TOKENS = "estimated_tokens";

    public static final String ORIGINAL_MODEL = "original_model";
    public static final String REQUEST_START_TIME = "request_start_time";

    /* token related keys */
    public static final String TOKEN_UNLIMITED_QUOTA = "token_unlimited_quota";
    public static final String TOKEN_KEY = "token_key";
    public static final String TOKEN_ID = "token_id";
    public static final String TOKEN_GROUP = "token_group";
    public static final String TOKEN_SPECIFIC_CHANNEL_ID = "specific_channel_id";
    public static final String TOKEN_MODEL_LIMIT_ENABLED = "token_model_limit_enabled";
    public static final String TOKEN_MODEL_LIMIT = "token_model_limit";
    public static final String TOKEN_CROSS_GROUP_RETRY = "token_cross_group_retry";

    /* channel related keys */
    public static final String CHANNEL_ID = "channel_id";
    public static final String CHANNEL_NAME = "channel_name";
    public static final String CHANNEL_CREATE_TIME = "channel_create_time";
    public static final String CHANNEL_BASE_URL = "base_url";
    public static final String CHANNEL_TYPE = "channel_type";
    public static final String CHANNEL_SETTING = "channel_setting";
    public static final String CHANNEL_OTHER_SETTING = "channel_other_setting";
    public static final String CHANNEL_PARAM_OVERRIDE = "param_override";
    public static final String CHANNEL_HEADER_OVERRIDE = "header_override";
    public static final String CHANNEL_ORGANIZATION = "channel_organization";
    public static final String CHANNEL_AUTO_BAN = "auto_ban";
    public static final String CHANNEL_MODEL_MAPPING = "model_mapping";
    public static final String CHANNEL_STATUS_CODE_MAPPING = "status_code_mapping";
    public static final String CHANNEL_IS_MULTI_KEY = "channel_is_multi_key";
    public static final String CHANNEL_MULTI_KEY_INDEX = "channel_multi_key_index";
    public static final String CHANNEL_KEY = "channel_key";

    public static final String AUTO_GROUP = "auto_group";
    public static final String AUTO_GROUP_INDEX = "auto_group_index";
    public static final String AUTO_GROUP_RETRY_INDEX = "auto_group_retry_index";

    /* user related keys */
    public static final String USER_ID = "id";
    public static final String USER_SETTING = "user_setting";
    public static final String USER_QUOTA = "user_quota";
    public static final String USER_STATUS = "user_status";
    public static final String USER_EMAIL = "user_email";
    public static final String USER_GROUP = "user_group";
    public static final String USING_GROUP = "group";
    public static final String USER_NAME = "username";

    public static final String LOCAL_COUNT_TOKENS = "local_count_tokens";

    public static final String SYSTEM_PROMPT_OVERRIDE = "system_prompt_override";

    /** 请求结束时需清理的文件来源列表 */
    public static final String FILE_SOURCES_TO_CLEANUP = "file_sources_to_cleanup";

    /** 管理员拒绝原因（仅内部日志使用，不返回给终端用户） */
    public static final String ADMIN_REJECT_REASON = "admin_reject_reason";

    /** 用户语言偏好（i18n） */
    public static final String LANGUAGE = "language";
    /** 是否为流式请求 */
    public static final String IS_STREAM = "is_stream";

    /** DistributorFilter 首次解析后缓存的请求体 Map，供 RelayController 复用，避免二次消费 InputStream */
    public static final String PARSED_REQUEST_BODY = "parsed_request_body";
}
