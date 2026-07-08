package yaoshu.token.service.openaicompat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import yaoshu.token.pojo.dto.OpenAIResponseDTO;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAIResponsesResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ResponsesOutput;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ResponsesOutputContent;
import yaoshu.token.service.openaicompat.ResponseToChatService.ResponsesToChatResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResponseToChatService — Responses→Chat 转换")
class ResponseToChatServiceTest {

    // ======================== Null / 空 ========================

    @Test
    @DisplayName("null 响应抛异常")
    void nullResponse() {
        assertThrows(IllegalArgumentException.class,
                () -> ResponseToChatService.responsesResponseToChatCompletionsResponse(null, "chatcmpl-1"));
    }

    @Test
    @DisplayName("空 output → 返回空文本")
    void emptyOutput() {
        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of());

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        assertNotNull(result);
        assertEquals("chatcmpl-1", result.response().getId());
        assertEquals("chat.completion", result.response().getObject());
        assertNotNull(result.response().getChoices());
        assertEquals(1, result.response().getChoices().size());

        var msg = result.response().getChoices().get(0).getMessage();
        assertEquals("assistant", msg.getRole());
        assertEquals("", msg.getContent());
        assertEquals("stop", result.response().getChoices().get(0).getFinishReason());
    }

    // ======================== 文本输出 ========================

    @Test
    @DisplayName("assistant 消息文本提取")
    void assistantMessageText() {
        var content = new ResponsesOutputContent();
        content.setType("output_text");
        content.setText("Hello, world!");

        var output = new ResponsesOutput();
        output.setType("message");
        output.setRole("assistant");
        output.setContent(List.of(content));

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));
        resp.setModel("gpt-4o");
        resp.setCreatedAt(1234567890);

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        assertEquals("gpt-4o", result.response().getModel());
        assertEquals(1234567890, result.response().getCreated());

        var msg = result.response().getChoices().get(0).getMessage();
        assertEquals("Hello, world!", msg.getContent());
        assertEquals("stop", result.response().getChoices().get(0).getFinishReason());
    }

    @Test
    @DisplayName("多段 output_text 拼接")
    void multiSegmentOutputText() {
        var c1 = new ResponsesOutputContent();
        c1.setType("output_text");
        c1.setText("Hello, ");

        var c2 = new ResponsesOutputContent();
        c2.setType("output_text");
        c2.setText("world!");

        var output = new ResponsesOutput();
        output.setType("message");
        output.setRole("assistant");
        output.setContent(List.of(c1, c2));

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        assertEquals("Hello, world!", result.response().getChoices().get(0).getMessage().getContent());
    }

    @Test
    @DisplayName("回退：非 assistant 角色文本提取")
    void fallbackTextExtraction() {
        var content = new ResponsesOutputContent();
        content.setType("output_text");
        content.setText("fallback text");

        var output = new ResponsesOutput();
        output.setType("message");
        output.setRole("user"); // 非 assistant
        output.setContent(List.of(content));

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));

        String text = ResponseToChatService.extractOutputTextFromResponses(resp);

        assertEquals("fallback text", text);
    }

    // ======================== Tool Calls ========================

    @Test
    @DisplayName("function_call → tool_calls 转换")
    void functionCallToToolCalls() {
        var output = new ResponsesOutput();
        output.setType("function_call");
        output.setName("get_weather");
        output.setCallId("call_abc123");
        output.setId("item_xyz");
        output.setArguments("{\"city\": \"Beijing\"}");

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        var msg = result.response().getChoices().get(0).getMessage();
        assertEquals("assistant", msg.getRole());
        assertEquals("", msg.getContent());  // tool_calls 时 content 为空
        assertEquals("tool_calls", result.response().getChoices().get(0).getFinishReason());

        assertNotNull(msg.getToolCalls());
        assertEquals(1, msg.getToolCalls().size());

        var tc = msg.getToolCalls().get(0);
        assertEquals("call_abc123", tc.getId());
        assertEquals("function", tc.getType());
        assertEquals("get_weather", tc.getFunction().getName());
        assertTrue(tc.getFunction().getArguments().contains("Beijing"));
    }

    @Test
    @DisplayName("function_call callId 回退到 id")
    void functionCallFallbackId() {
        var output = new ResponsesOutput();
        output.setType("function_call");
        output.setName("search");
        // callId 为空，id 不为空
        output.setId("item_fallback");
        output.setArguments("{}");

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        var tc = result.response().getChoices().get(0).getMessage().getToolCalls().get(0);
        assertEquals("item_fallback", tc.getId());
    }

    @Test
    @DisplayName("function_call 名称为空 → 跳过")
    void skipEmptyNameToolCall() {
        var output = new ResponsesOutput();
        output.setType("function_call");
        output.setName("  ");  // 空白
        output.setCallId("call_x");
        output.setArguments("{}");

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of(output));

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        // 无有效 tool_call，应回退为文本模式
        assertEquals("stop", result.response().getChoices().get(0).getFinishReason());
        assertNull(result.response().getChoices().get(0).getMessage().getToolCalls());
        assertEquals("", result.response().getChoices().get(0).getMessage().getContent());
    }

    // ======================== Usage 映射 ========================

    @Test
    @DisplayName("Usage 映射：input/output/total tokens")
    void usageMapping() {
        var usage = new OpenAIResponseDTO.Usage();
        usage.setInputTokens(100);
        usage.setOutputTokens(50);
        usage.setTotalTokens(150);

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of());
        resp.setUsage(usage);

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        assertEquals(100, result.usage().getPromptTokens());
        assertEquals(50, result.usage().getCompletionTokens());
        assertEquals(150, result.usage().getTotalTokens());
    }

    @Test
    @DisplayName("Usage 映射：total 为空时自动计算")
    void usageTotalAutoCalc() {
        var usage = new OpenAIResponseDTO.Usage();
        usage.setInputTokens(200);
        usage.setOutputTokens(80);

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of());
        resp.setUsage(usage);

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        assertEquals(200, result.usage().getPromptTokens());
        assertEquals(80, result.usage().getCompletionTokens());
        assertEquals(280, result.usage().getTotalTokens());
    }

    @Test
    @DisplayName("Usage 映射：token details")
    void usageTokenDetails() {
        var usage = new OpenAIResponseDTO.Usage();
        usage.setInputTokens(300);
        usage.setOutputTokens(200);

        var inputTokenDetails = new OpenAIResponseDTO.InputTokenDetails();
        inputTokenDetails.setCachedTokens(10);
        inputTokenDetails.setImageTokens(5);
        inputTokenDetails.setAudioTokens(3);
        usage.setInputTokensDetails(inputTokenDetails);

        var resp = new OpenAIResponsesResponse();
        resp.setOutput(List.of());
        resp.setUsage(usage);

        ResponsesToChatResult result = ResponseToChatService.responsesResponseToChatCompletionsResponse(resp, "chatcmpl-1");

        var itd = result.usage().getInputTokensDetails();
        assertNotNull(itd);
        assertEquals(10, itd.getCachedTokens());
        assertEquals(5, itd.getImageTokens());
        assertEquals(3, itd.getAudioTokens());
    }

    // ======================== extractOutputTextFromResponses ========================

    @Test
    @DisplayName("extractOutputTextFromResponses：null/空响应")
    void extractTextNull() {
        assertEquals("", ResponseToChatService.extractOutputTextFromResponses(null));

        var resp = new OpenAIResponsesResponse();
        assertEquals("", ResponseToChatService.extractOutputTextFromResponses(resp));
    }
}
