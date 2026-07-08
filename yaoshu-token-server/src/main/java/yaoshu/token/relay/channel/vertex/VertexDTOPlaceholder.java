package yaoshu.token.relay.channel.vertex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ai.yue.library.base.convert.Convert;
import lombok.Data;
import yaoshu.token.pojo.dto.ClaudeDTO;

import java.util.List;
import java.util.Map;

/**
 * Vertex AI 渠道 DTO 定义  * <p>
 * Vertex AI Claude 请求格式（含 anthropic_version）。
 */
public class VertexDTOPlaceholder {    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VertexAIClaudeRequest {
        @JsonProperty("anthropic_version")
        private String anthropicVersion;
        private List<ClaudeDTO.ClaudeMessage> messages;
        private Object system;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        @JsonProperty("stop_sequences")
        private List<String> stopSequences;
        private Boolean stream;
        private Double temperature;
        @JsonProperty("top_p")
        private Double topP;
        @JsonProperty("top_k")
        private Integer topK;
        private Object tools;
        @JsonProperty("tool_choice")
        private Object toolChoice;
        private Object thinking;
        @JsonProperty("output_config")
        private Object outputConfig;
    }

    /**
     * 将 ClaudeRequest 复制为 VertexAIClaudeRequest（追加 anthropic_version）      */
    public static VertexAIClaudeRequest copyRequest(ClaudeDTO.ClaudeRequest req, String version) {
        VertexAIClaudeRequest vertexReq = new VertexAIClaudeRequest();
        vertexReq.setAnthropicVersion(version);
        vertexReq.setSystem(req.getSystem());
        vertexReq.setMessages(req.getMessages());
        vertexReq.setMaxTokens(req.getMaxTokens());
        vertexReq.setStream(req.getStream());
        vertexReq.setTemperature(req.getTemperature());
        vertexReq.setTopP(req.getTopP());
        vertexReq.setTopK(req.getTopK());
        vertexReq.setStopSequences(req.getStopSequences());
        vertexReq.setTools(req.getTools());
        vertexReq.setToolChoice(req.getToolChoice());
        vertexReq.setThinking(req.getThinking());
        vertexReq.setOutputConfig(req.getOutputConfig());
        return vertexReq;
    }

    /**
     * 获取模型区域      * <p>
     * other 可以是 JSON 字符串（按模型名查找区域）或普通字符串（直接返回）。
     */
    @SuppressWarnings("unchecked")
    public static String getModelRegion(String other, String localModelName) {
        if (other == null || other.isEmpty()) return "global";

        // 尝试解析为 JSON 对象
        if (other.trim().startsWith("{")) {
            try {
                Map<String, Object> m = Convert.toJSONObject(other);
                Object modelRegion = m.get(localModelName);
                if (modelRegion instanceof String s) return s;
                Object defaultRegion = m.get("default");
                if (defaultRegion instanceof String s) return s;
                return "global";
            } catch (Exception e) {
                return other;
            }
        }
        return other;
    }
}
