package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 渠道其他设置 DTO（dto/channel_settings.go — ChannelOtherSettings）  *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelOtherSettingsDTO {

    @JsonProperty("azure_responses_version")
    private String azureResponsesVersion;

    @JsonProperty("vertex_key_type")
    private String vertexKeyType;

    @JsonProperty("openrouter_enterprise")
    private Boolean openRouterEnterprise;

    @JsonProperty("claude_beta_query")
    private Boolean claudeBetaQuery;

    public boolean isClaudeBetaQuery() { return Boolean.TRUE.equals(claudeBetaQuery); }

    @JsonProperty("allow_service_tier")
    private Boolean allowServiceTier;

    @JsonProperty("allow_inference_geo")
    private Boolean allowInferenceGeo;

    @JsonProperty("allow_speed")
    private Boolean allowSpeed;

    @JsonProperty("allow_safety_identifier")
    private Boolean allowSafetyIdentifier;

    @JsonProperty("disable_store")
    private Boolean disableStore;

    @JsonProperty("allow_include_obfuscation")
    private Boolean allowIncludeObfuscation;

    @JsonProperty("aws_key_type")
    private String awsKeyType;

    @JsonProperty("upstream_model_update_check_enabled")
    private Boolean upstreamModelUpdateCheckEnabled;

    @JsonProperty("upstream_model_update_auto_sync_enabled")
    private Boolean upstreamModelUpdateAutoSyncEnabled;

    @JsonProperty("upstream_model_update_last_check_time")
    private Long upstreamModelUpdateLastCheckTime;

    @JsonProperty("upstream_model_update_last_detected_models")
    private List<String> upstreamModelUpdateLastDetectedModels;

    @JsonProperty("upstream_model_update_last_removed_models")
    private List<String> upstreamModelUpdateLastRemovedModels;

    @JsonProperty("upstream_model_update_ignored_models")
    private List<String> upstreamModelUpdateIgnoredModels;
}
