package yaoshu.token.pojo.dto;

/**
 * 错误码常量  */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String INVALID_REQUEST = "invalid_request";
    public static final String SENSITIVE_WORDS_DETECTED = "sensitive_words_detected";
    public static final String VIOLATION_FEE_GROK_CSAM = "violation_fee.grok.csam";

    /* new api error */
    public static final String COUNT_TOKEN_FAILED = "count_token_failed";
    public static final String MODEL_PRICE_ERROR = "model_price_error";
    public static final String INVALID_API_TYPE = "invalid_api_type";
    public static final String JSON_MARSHAL_FAILED = "json_marshal_failed";
    public static final String DO_REQUEST_FAILED = "do_request_failed";
    public static final String GET_CHANNEL_FAILED = "get_channel_failed";
    public static final String GEN_RELAY_INFO_FAILED = "gen_relay_info_failed";

    /* channel error */
    public static final String CHANNEL_NO_AVAILABLE_KEY = "channel:no_available_key";
    public static final String CHANNEL_PARAM_OVERRIDE_INVALID = "channel:param_override_invalid";
    public static final String CHANNEL_HEADER_OVERRIDE_INVALID = "channel:header_override_invalid";
    public static final String CHANNEL_MODEL_MAPPED_ERROR = "channel:model_mapped_error";
    public static final String CHANNEL_AWS_CLIENT_ERROR = "channel:aws_client_error";
    public static final String CHANNEL_INVALID_KEY = "channel:invalid_key";
    public static final String CHANNEL_RESPONSE_TIME_EXCEEDED = "channel:response_time_exceeded";

    /* client request error */
    public static final String READ_REQUEST_BODY_FAILED = "read_request_body_failed";
    public static final String CONVERT_REQUEST_FAILED = "convert_request_failed";
    public static final String ACCESS_DENIED = "access_denied";

    /* request error */
    public static final String BAD_REQUEST_BODY = "bad_request_body";

    /* response error */
    public static final String READ_RESPONSE_BODY_FAILED = "read_response_body_failed";
    public static final String BAD_RESPONSE_STATUS_CODE = "bad_response_status_code";
    public static final String BAD_RESPONSE = "bad_response";
    public static final String BAD_RESPONSE_BODY = "bad_response_body";
    public static final String EMPTY_RESPONSE = "empty_response";
    public static final String AWS_INVOKE_ERROR = "aws_invoke_error";
    public static final String MODEL_NOT_FOUND = "model_not_found";
    public static final String PROMPT_BLOCKED = "prompt_blocked";

    /* sql error */
    public static final String QUERY_DATA_ERROR = "query_data_error";
    public static final String UPDATE_DATA_ERROR = "update_data_error";

    /* quota error */
    public static final String INSUFFICIENT_USER_QUOTA = "insufficient_user_quota";
    public static final String PRE_CONSUME_TOKEN_QUOTA_FAILED = "pre_consume_token_quota_failed";
}
