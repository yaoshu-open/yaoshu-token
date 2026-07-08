package yaoshu.token.relay.channel.perplexity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

public final class PerplexityDTO {
    private PerplexityDTO() {}

    @Data @Accessors(chain = true) @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PerplexityRequest {
        private String model;
        private Object messages;
        private Boolean stream;
    }
}
