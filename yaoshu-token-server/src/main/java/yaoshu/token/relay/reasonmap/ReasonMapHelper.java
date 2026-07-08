package yaoshu.token.relay.reasonmap;

import yaoshu.token.constant.FinishReasonEnum;

/**
 * Claude / OpenAI 停止原因互转  */
public final class ReasonMapHelper {

    private ReasonMapHelper() {
    }

    /**
     * Claude → OpenAI 停止原因映射      */
    public static String claudeStopReasonToOpenAIFinishReason(String stopReason) {
        if (stopReason == null) return null;
        return switch (stopReason.toLowerCase()) {
            case "stop_sequence" -> "stop";
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "refusal" -> FinishReasonEnum.CONTENT_FILTER;
            default -> stopReason;
        };
    }

    /**
     * OpenAI → Claude 停止原因映射      */
    public static String openAIFinishReasonToClaudeStopReason(String finishReason) {
        if (finishReason == null) return null;
        return switch (finishReason.toLowerCase()) {
            case "stop" -> "end_turn";
            case "stop_sequence" -> "stop_sequence";
            case "length", "max_tokens" -> "max_tokens";
            case FinishReasonEnum.CONTENT_FILTER -> "refusal";
            case "tool_calls" -> "tool_use";
            default -> finishReason;
        };
    }
}
