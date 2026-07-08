package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * 渠道实体  *
 * @author yaoshu
 */
@Data
@TableName("channels")
public class Channel {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer type;

    @TableField("`key`")
    private String key;

    private String openaiOrganization;

    private String testModel;

    private Integer status;
    private String name;
    private Integer weight;

    private Long createdTime;

    private Long testTime;

    private Integer responseTime;

    private String baseUrl;

    private String other;
    private Double balance;

    private Long balanceUpdatedTime;

    private String models;

    @TableField("`group`")
    private String group;

    private Long usedQuota;

    private String modelMapping;

    private String statusCodeMapping;

    private Long priority;

    private Integer autoBan;

    private String otherInfo;

    private String tag;
    private String setting;

    private String paramOverride;

    private String headerOverride;

    /**
     * UA 覆盖模式：AUTO（默认，由 SPI 实现判定）/ FORCE_IDE（强制 IDE 类 UA）/ OFF（跳过替换）
     * 供 RelayRequestInterceptor SPI 扩展点消费，开源默认实现不消费此字段
     */
    private String uaOverrideMode;

    private String remark;

    private String channelInfo;

    // 字段名 otherSettings 与 DB 列 settings 不一致，需显式映射
    @TableField("settings")
    private String otherSettings;

    /** 已解析的 Key 列表（运行时缓存，不持久化）*/
    @TableField(exist = false)
    private List<String> keys;
}
