package yaoshu.token.service.openaicompat;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.FunctionResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.Message;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAIResponsesResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponse;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponseChoice;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ResponsesOutput;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.ToolCallResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.pojo.dto.Usage.CompletionTokenDetails;
import yaoshu.token.pojo.dto.Usage.InputTokensDetails;
import yaoshu.token.pojo.dto.Usage.PromptTokensDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Responses → Chat 转换服务  */
@Slf4j
public final class ResponseToChatService {

    private ResponseToChatService() {
    }

    /**
     * Responses 响应转 Chat Completions 响应（同步），
     *
     * @param resp Responses 响应
     * @param id   Chat Completions 响应 ID
     * @return 转换结果（含 OpenAITextResponse + Usage）
     */
    public static ResponsesToChatResult responsesResponseToChatCompletionsResponse(
            OpenAIResponsesResponse resp, String id) {
        if (resp == null) {
            throw new IllegalArgumentException("response is nil");
        }

        // 1. 提取输出文本
        String text = extractOutputTextFromResponses(resp);

        // 2. 映射 Usage（从 OpenAIResponseDTO.Usage → pojo.dto.Usage）
        Usage usage = mapUsage(resp);

        // 3. created 时间
        Object created = resp.getCreatedAt();

        // 4. 无文本输出时检查 tool_calls
        List<ToolCallResponse> toolCalls = null;
        if ((text == null || text.isEmpty()) && resp.getOutput() != null && !resp.getOutput().isEmpty()) {
            toolCalls = extractToolCalls(resp.getOutput());
        }

        // 5. 构建 finish_reason
        String finishReason = "stop";
        if (toolCalls != null && !toolCalls.isEmpty()) {
            finishReason = "tool_calls";
        }

        // 6. 构建 Message
        Message msg = new Message();
        msg.setRole("assistant");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            msg.setToolCallsAndClearContent(toolCalls);
        } else {
            msg.setContent(text != null ? text : "");
        }

        // 7. 构建 OpenAITextResponse
        OpenAITextResponseChoice choice = new OpenAITextResponseChoice();
        choice.setIndex(0);
        choice.setMessage(msg);
        choice.setFinishReason(finishReason);

        OpenAITextResponse out = new OpenAITextResponse();
        out.setId(id);
        out.setObject("chat.completion");
        out.setCreated(created);
        out.setModel(resp.getModel());
        out.setChoices(List.of(choice));
        out.setUsage(resp.getUsage()); // 序列化用 OpenAIResponseDTO.Usage

        return new ResponsesToChatResult(out, usage);
    }

    /**
     * 从 Responses 输出中提取 assistant 文本，
     */
    public static String extractOutputTextFromResponses(OpenAIResponsesResponse resp) {
        if (resp == null || resp.getOutput() == null || resp.getOutput().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 优先提取 type=="message" 且 role=="assistant" 的 output_text
        for (ResponsesOutput out : resp.getOutput()) {
            if (!"message".equals(out.getType())) continue;
            if (out.getRole() != null && !"assistant".equals(out.getRole())) continue;
            if (out.getContent() != null) {
                for (var c : out.getContent()) {
                    if ("output_text".equals(c.getType()) && c.getText() != null) {
                        sb.append(c.getText());
                    }
                }
            }
        }
        if (sb.length() > 0) {
            return sb.toString();
        }

        // 回退：提取所有内容文本（不限类型/角色）
        for (ResponsesOutput out : resp.getOutput()) {
            if (out.getContent() != null) {
                for (var c : out.getContent()) {
                    if (c.getText() != null) {
                        sb.append(c.getText());
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 映射 OpenAIResponseDTO.Usage → pojo.dto.Usage，
     */
    private static Usage mapUsage(OpenAIResponsesResponse resp) {
        Usage usage = new Usage();
        if (resp.getUsage() == null) return usage;

        var u = resp.getUsage();
        if (u.getInputTokens() != null && u.getInputTokens() != 0) {
            usage.setPromptTokens(u.getInputTokens());
        }
        if (u.getOutputTokens() != null && u.getOutputTokens() != 0) {
            usage.setCompletionTokens(u.getOutputTokens());
        }
        if (u.getTotalTokens() != null && u.getTotalTokens() != 0) {
            usage.setTotalTokens(u.getTotalTokens());
        } else {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        if (u.getInputTokensDetails() != null) {
            var details = u.getInputTokensDetails();
            InputTokensDetails itd = new InputTokensDetails();
            itd.setCachedTokens(details.getCachedTokens() != null ? details.getCachedTokens() : 0);
            itd.setImageTokens(details.getImageTokens() != null ? details.getImageTokens() : 0);
            itd.setAudioTokens(details.getAudioTokens() != null ? details.getAudioTokens() : 0);
            usage.setInputTokensDetails(itd);
        }
        if (u.getPromptTokensDetails() != null) {
            var details = u.getPromptTokensDetails();
            PromptTokensDetails ptd = new PromptTokensDetails();
            ptd.setCachedTokens(details.getCachedTokens() != null ? details.getCachedTokens() : 0);
            usage.setPromptTokensDetails(ptd);
        }
        if (u.getCompletionTokenDetails() != null
                && u.getCompletionTokenDetails().getReasoningTokens() != null
                && u.getCompletionTokenDetails().getReasoningTokens() != 0) {
            CompletionTokenDetails ctd = new CompletionTokenDetails();
            ctd.setReasoningTokens(u.getCompletionTokenDetails().getReasoningTokens());
            usage.setCompletionTokenDetails(ctd);
        }
        return usage;
    }

    /**
     * 从 ResponsesOutput 列表中提取 function_call tool calls，
     */
    private static List<ToolCallResponse> extractToolCalls(List<ResponsesOutput> output) {
        List<ToolCallResponse> toolCalls = new ArrayList<>();
        for (var out : output) {
            if (!"function_call".equals(out.getType())) continue;
            String name = out.getName();
            if (name == null || name.trim().isEmpty()) continue;
            String callId = out.getCallId();
            if (callId == null || callId.trim().isEmpty()) {
                callId = (out.getId() != null) ? out.getId().trim() : "";
            }
            if (callId.isEmpty()) continue;

            FunctionResponse function = new FunctionResponse();
            function.setName(name.trim());
            function.setArguments(out.argumentsString());

            ToolCallResponse tc = new ToolCallResponse();
            tc.setId(callId.trim());
            tc.setType("function");
            tc.setFunction(function);
            toolCalls.add(tc);
        }
        return toolCalls;
    }

    /**
     * Responses→Chat 转换结果封装
     */
    public record ResponsesToChatResult(OpenAITextResponse response, Usage usage) {
    }
}
