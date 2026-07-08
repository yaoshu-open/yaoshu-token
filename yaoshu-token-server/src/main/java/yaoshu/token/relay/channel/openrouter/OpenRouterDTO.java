package yaoshu.token.relay.channel.openrouter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

public final class OpenRouterDTO {
    private OpenRouterDTO() {}

    @Data @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestReasoning {
        private boolean enabled;
        private String effort;
        private int maxTokens;
        private boolean exclude;
    }

    @Data
    public static class OpenRouterEnterpriseResponse {
        private Object data;
        private boolean success;
    }
}
