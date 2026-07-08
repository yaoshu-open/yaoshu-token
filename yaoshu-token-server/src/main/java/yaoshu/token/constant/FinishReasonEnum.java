package yaoshu.token.constant;

/**
 * 完成原因常量  */
public final class FinishReasonEnum {

    private FinishReasonEnum() {
    }

    public static final String STOP = "stop";
    public static final String TOOL_CALLS = "tool_calls";
    public static final String LENGTH = "length";
    public static final String FUNCTION_CALL = "function_call";
    public static final String CONTENT_FILTER = "content_filter";
}
