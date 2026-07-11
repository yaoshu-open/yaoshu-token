package yaoshu.token.pojo.ipo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 渠道模块入参对象（IPO），对应 ChannelController 各端点请求体。
 * <p>
 * @Size 上限对齐 channels 表 DDL 列长：name varchar(128)、base_url varchar(512)、
 * group varchar(64)、tag varchar(128)、openai_organization varchar(255)、
 * test_model varchar(128)、status_code_mapping varchar(1024)、ua_override_mode varchar(20)、
 * remark varchar(255)。text 类型字段（key/models/model_mapping/setting/param_override/
 * header_override/settings/other/other_info）给合理业务上限防超长输入。
 */
public class ChannelIPO {

    /**
     * 渠道创建请求（嵌套结构），对应前端 {mode, multi_key_mode, batch_add_set_key_prefix_2_name, channel:{...}}
     * <p>
     * 支持三种创建模式：
     * <ul>
     *   <li>single: 单 key 创建单个渠道</li>
     *   <li>multi_to_single: 多 key 合并为一个渠道（key 用 \n 连接），标记 isMultiKey</li>
     *   <li>batch: 多 key 拆分为多个渠道批量创建，可选追加 key 前缀到名称</li>
     * </ul>
     */
    @Data
    public static class AddRequest {

        /** 创建模式：single / multi_to_single / batch */
        @NotBlank(message = "mode 不能为空")
        @Pattern(regexp = "single|multi_to_single|batch", message = "mode 仅支持 single/multi_to_single/batch")
        private String mode;

        /** 多 key 模式标识（multi_to_single 模式下写入 channelInfo） */
        @Size(max = 64, message = "multiKeyMode 长度不能超过 64 字符")
        private String multiKeyMode;

        /** batch 模式下是否将 key 前 8 位追加到渠道名称 */
        private Boolean batchAddSetKeyPrefix2Name;

        /** 渠道详情（嵌套） */
        @NotNull(message = "channel 不能为空")
        @Valid
        private ChannelCreate channel;
    }

    /**
     * 渠道创建详情（嵌套于 AddRequest.channel），覆盖 Channel 实体的前端可设置业务字段。
     */
    @Data
    public static class ChannelCreate {

        @NotBlank(message = "渠道名称不能为空")
        @Size(max = 128, message = "渠道名称长度不能超过 128 字符")
        private String name;

        private Integer type;

        // key 为 text 类型，多 key 合并场景用 \n 连接，限定合理上限防超长输入
        @Size(max = 10000, message = "渠道密钥长度不能超过 10000 字符")
        private String key;

        private Integer status;

        @Size(max = 512, message = "base_url 长度不能超过 512 字符")
        private String baseUrl;

        @Size(max = 10000, message = "models 长度不能超过 10000 字符")
        private String models;

        @JsonProperty("group")
        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        private Long priority;

        private Double balance;

        private Integer weight;

        private Integer autoBan;

        @Size(max = 128, message = "test_model 长度不能超过 128 字符")
        private String testModel;

        @Size(max = 10000, message = "model_mapping 长度不能超过 10000 字符")
        private String modelMapping;

        @Size(max = 10000, message = "setting 长度不能超过 10000 字符")
        private String setting;

        @JsonProperty("settings")
        @Size(max = 10000, message = "settings 长度不能超过 10000 字符")
        private String otherSettings;

        @Size(max = 10000, message = "param_override 长度不能超过 10000 字符")
        private String paramOverride;

        @Size(max = 10000, message = "header_override 长度不能超过 10000 字符")
        private String headerOverride;

        @Size(max = 128, message = "tag 长度不能超过 128 字符")
        private String tag;

        @Size(max = 2000, message = "other 长度不能超过 2000 字符")
        private String other;

        @Size(max = 2000, message = "other_info 长度不能超过 2000 字符")
        private String otherInfo;

        @Size(max = 255, message = "remark 长度不能超过 255 字符")
        private String remark;

        @Size(max = 255, message = "openai_organization 长度不能超过 255 字符")
        private String openaiOrganization;

        @Size(max = 1024, message = "status_code_mapping 长度不能超过 1024 字符")
        private String statusCodeMapping;

        @Size(max = 20, message = "ua_override_mode 长度不能超过 20 字符")
        private String uaOverrideMode;

        // channel_info 为 json 类型，限定合理上限防超长 JSON
        @Size(max = 10000, message = "channel_info 长度不能超过 10000 字符")
        private String channelInfo;
    }

    @Data
    public static class Update {
        @NotNull(message = "渠道ID不能为空")
        private Integer id;

        @Size(max = 128, message = "渠道名称长度不能超过 128 字符")
        private String name;

        private Integer type;

        @Size(max = 10000, message = "渠道密钥长度不能超过 10000 字符")
        private String key;

        private Integer status;

        @Size(max = 512, message = "base_url 长度不能超过 512 字符")
        private String baseUrl;

        @Size(max = 10000, message = "models 长度不能超过 10000 字符")
        private String models;

        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        private Long priority;

        private Double balance;
    }

    @Data
    public static class BatchDelete {
        @NotEmpty(message = "ids不能为空")
        private List<Integer> ids;
    }

    @Data
    public static class OllamaAction {
        @NotNull(message = "channelId不能为空")
        @Min(value = 1, message = "channelId无效")
        private Integer channelId;

        @NotBlank(message = "modelName不能为空")
        @Size(max = 128, message = "modelName 长度不能超过 128 字符")
        private String modelName;
    }

    @Data
    public static class TagAction {
        @NotBlank(message = "标签不能为空")
        @Size(max = 128, message = "标签长度不能超过 128 字符")
        private String tag;
    }

    @Data
    public static class TagEdit {
        @NotBlank(message = "标签不能为空")
        @Size(max = 128, message = "标签长度不能超过 128 字符")
        private String tag;

        @Size(max = 128, message = "newTag 长度不能超过 128 字符")
        private String newTag;

        @Size(max = 10000, message = "model_mapping 长度不能超过 10000 字符")
        private String modelMapping;

        @Size(max = 10000, message = "models 长度不能超过 10000 字符")
        private String models;

        @Size(max = 512, message = "groups 长度不能超过 512 字符")
        private String groups;

        private Long priority;

        private Integer weight;

        @Size(max = 10000, message = "param_override 长度不能超过 10000 字符")
        private String paramOverride;

        @Size(max = 10000, message = "header_override 长度不能超过 10000 字符")
        private String headerOverride;
    }

    @Data
    public static class FetchModels {
        @NotNull(message = "type不能为空")
        private Integer type;

        @Size(max = 512, message = "base_url 长度不能超过 512 字符")
        private String baseUrl;

        @Size(max = 10000, message = "key 长度不能超过 10000 字符")
        private String key;
    }

    @Data
    public static class BatchSetTag {
        @NotEmpty(message = "ids不能为空")
        private List<Integer> ids;

        @Size(max = 128, message = "标签长度不能超过 128 字符")
        private String tag;
    }

    @Data
    public static class BatchTest {
        @NotEmpty(message = "ids不能为空")
        private List<Integer> ids;
    }

    @Data
    public static class MultiKeyManage {
        @NotNull(message = "channelId不能为空")
        private Integer channelId;

        @NotBlank(message = "action不能为空")
        @Size(max = 64, message = "action 长度不能超过 64 字符")
        private String action;

        private Integer page;

        private Integer pageSize;

        private Integer status;

        private Integer keyIndex;
    }

    @Data
    public static class CodexOAuthComplete {
        @Size(max = 10000, message = "input 长度不能超过 10000 字符")
        private String input;
    }

    @Data
    public static class UpstreamUpdates {
        @NotNull(message = "渠道ID不能为空")
        @Min(value = 1, message = "渠道ID无效")
        private Integer id;

        private List<String> addModels;

        private List<String> ignoreModels;

        private List<String> removeModels;
    }
}
