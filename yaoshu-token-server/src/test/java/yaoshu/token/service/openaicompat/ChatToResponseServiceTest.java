package yaoshu.token.service.openaicompat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest.Message;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatToResponseService — Chat→Responses 转换")
class ChatToResponseServiceTest {

    // ======================== 基本转换 ========================

    @Test
    @DisplayName("基本 user 消息转换")
    void basicUserMessage() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hello", null, null, null, null)))
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertEquals("gpt-4o", result.getModel());
        assertNotNull(result.getInput());
        assertTrue(result.getInput().toString().contains("hello"));
        assertTrue(result.getInput().toString().contains("\"role\":\"user\""));
    }

    @Test
    @DisplayName("system 消息 → instructions")
    void systemToInstructions() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        new Message("system", "You are helpful", null, null, null, null),
                        new Message("user", "hi", null, null, null, null)
                ))
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertNotNull(result.getInstructions());
        assertTrue(result.getInstructions().toString().contains("You are helpful"));
    }

    @Test
    @DisplayName("多 system 消息用 \\n\\n 拼接")
    void multipleSystemMessages() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        new Message("system", "Rule 1", null, null, null, null),
                        new Message("system", "Rule 2", null, null, null, null),
                        new Message("user", "ok", null, null, null, null)
                ))
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        String instructions = result.getInstructions().toString();
        assertTrue(instructions.contains("Rule 1"));
        assertTrue(instructions.contains("Rule 2"));
    }

    // ======================== 约束校验 ========================

    @Test
    @DisplayName("n>1 应抛出异常")
    void rejectNGreaterThanOne() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .n(3)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> ChatToResponseService.chatCompletionsRequestToResponsesRequest(req));
    }

    @Test
    @DisplayName("空 model 应抛出异常")
    void rejectEmptyModel() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> ChatToResponseService.chatCompletionsRequestToResponsesRequest(req));
    }

    @Test
    @DisplayName("null 请求应抛出异常")
    void rejectNullRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> ChatToResponseService.chatCompletionsRequestToResponsesRequest(null));
    }

    // ======================== max_output_tokens ========================

    @Test
    @DisplayName("max_tokens → max_output_tokens")
    void maxTokensMapping() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .maxTokens(100)
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertEquals(100, result.getMaxOutputTokens());
    }

    @Test
    @DisplayName("max_completion_tokens 优先于 max_tokens")
    void maxCompletionTokensPriority() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .maxTokens(100)
                .maxCompletionTokens(200)
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertEquals(200, result.getMaxOutputTokens());
    }

    // ======================== 温度 / topP 透传 ========================

    @Test
    @DisplayName("temperature 透传")
    void temperaturePassthrough() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .temperature(0.7)
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertEquals(0.7, result.getTemperature());
    }

    @Test
    @DisplayName("topP 透传")
    void topPPassthrough() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .topP(0.9)
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertEquals(0.9, result.getTopP());
    }

    // ======================== reasoning_effort ========================

    @Test
    @DisplayName("reasoning_effort → reasoning.effort + summary=detailed")
    void reasoningEffortMapping() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("o3-mini")
                .messages(List.of(new Message("user", "hi", null, null, null, null)))
                .reasoningEffort("medium")
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertNotNull(result.getReasoning());
        assertEquals("medium", result.getReasoning().getEffort());
        assertEquals("detailed", result.getReasoning().getSummary());
    }

    // ======================== 空消息处理 ========================

    @Test
    @DisplayName("空 content 消息不抛异常")
    void emptyContentMessage() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        new Message("user", "", null, null, null, null)
                ))
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertNotNull(result.getInput());
        assertFalse(result.getInput().toString().isEmpty());
    }

    // ======================== 完整流程 ========================

    @Test
    @DisplayName("完整 Chat→Responses 不抛异常")
    void fullConversionNoException() {
        GeneralOpenAIRequest req = GeneralOpenAIRequest.builder()
                .model("gpt-4o")
                .messages(List.of(
                        new Message("system", "System prompt", null, null, null, null),
                        new Message("user", "Hello", null, null, null, null),
                        new Message("assistant", "Hi there!", null, null, null, null)
                ))
                .temperature(0.8)
                .topP(1.0)
                .maxTokens(2048)
                .stream(true)
                .build();

        OpenAIResponsesRequest result = ChatToResponseService.chatCompletionsRequestToResponsesRequest(req);

        assertNotNull(result);
        assertEquals("gpt-4o", result.getModel());
        assertEquals(2048, result.getMaxOutputTokens());
        assertTrue(Boolean.TRUE.equals(result.getStream()));
    }
}
