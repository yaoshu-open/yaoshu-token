package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 图片生成请求 DTO  *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAIImageDTO {

    private String model;
    private String prompt;

    @JsonProperty("n")
    private Integer n;

    private String size;
    private String quality;

    @JsonProperty("response_format")
    private String responseFormat;

    private Object style;
    private Object user;

    @JsonProperty("extra_fields")
    private Object extraFields;

    private Object background;
    private Object moderation;

    @JsonProperty("output_format")
    private Object outputFormat;

    @JsonProperty("output_compression")
    private Object outputCompression;

    @JsonProperty("partial_images")
    private Object partialImages;

    private Boolean stream;
    private Object images;
    private Object mask;

    @JsonProperty("input_fidelity")
    private Object inputFidelity;

    private Boolean watermark;

    @JsonProperty("watermark_enabled")
    private Object watermarkEnabled;

    @JsonProperty("user_id")
    private Object userId;

    private Object image;

    /** 额外未识别参数 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Map<String, Object> extra = new LinkedHashMap<>();
}
