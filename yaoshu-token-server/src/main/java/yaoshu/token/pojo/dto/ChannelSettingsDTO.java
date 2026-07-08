package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 渠道设置 DTO  *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSettingsDTO {

    @JsonProperty("force_format")
    private Boolean forceFormat;

    @JsonProperty("thinking_to_content")
    private Boolean thinkingToContent;

    public boolean isThinkingToContent() { return Boolean.TRUE.equals(thinkingToContent); }

    private String proxy;

    @JsonProperty("pass_through_body_enabled")
    private Boolean passThroughBodyEnabled;

    @JsonProperty("system_prompt")
    private String systemPrompt;

    @JsonProperty("system_prompt_override")
    private Boolean systemPromptOverride;
}
