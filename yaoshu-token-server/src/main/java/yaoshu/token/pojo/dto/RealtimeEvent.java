package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Realtime 事件 DTO  */
@Data
public class RealtimeEvent {

    public static final String TYPE_ERROR = "error";
    public static final String TYPE_SESSION_UPDATE = "session.update";
    public static final String TYPE_RESPONSE_DONE = "response.done";
    public static final String TYPE_SESSION_UPDATED = "session.updated";
    public static final String TYPE_SESSION_CREATED = "session.created";
    public static final String TYPE_RESPONSE_AUDIO_DELTA = "response.audio.delta";
    public static final String TYPE_RESPONSE_AUDIO_TRANSCRIPTION_DELTA = "response.audio_transcript.delta";
    public static final String TYPE_RESPONSE_FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    public static final String TYPE_RESPONSE_FUNCTION_CALL_ARGUMENTS_DONE = "response.function_call_arguments.done";
    public static final String TYPE_CONVERSATION_ITEM_CREATED = "conversation.item.created";
    public static final String TYPE_INPUT_AUDIO_BUFFER_APPEND = "input_audio_buffer.append";

    @JsonProperty("event_id")
    private String eventId;
    private String type;
    private RealtimeSession session;
    private RealtimeItem item;
    private OpenAIError error;
    private RealtimeResponse response;
    private String delta;
    private String audio;

    @Data
    public static class RealtimeResponse {
        private Usage usage;
    }

    @Data
    public static class RealtimeSession {
        private List<String> modalities;
        private String instructions;
        private String voice;
        @JsonProperty("input_audio_format")
        private String inputAudioFormat;
        @JsonProperty("output_audio_format")
        private String outputAudioFormat;
        @JsonProperty("input_audio_transcription")
        private InputAudioTranscription inputAudioTranscription;
        @JsonProperty("turn_detection")
        private Object turnDetection;
        private List<RealtimeTool> tools;
        @JsonProperty("tool_choice")
        private String toolChoice;
        private Double temperature;
    }

    @Data
    public static class InputAudioTranscription {
        private String model;
    }

    @Data
    public static class RealtimeTool {
        private String type;
        private String name;
        private String description;
        private Object parameters;
    }

    @Data
    public static class RealtimeItem {
        private String id;
        private String type;
        private String status;
        private String role;
        private List<RealtimeContent> content;
        private String name;
        @JsonProperty("tool_calls")
        private Object toolCalls;
        @JsonProperty("call_id")
        private String callId;
    }

    @Data
    public static class RealtimeContent {
        private String type;
        private String text;
        private String audio;
        private String transcript;
    }
}
