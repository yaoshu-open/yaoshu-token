package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 日志实体  *
 * @author yaoshu
 */
@Data
@TableName("logs")
public class Log {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Long createdAt;

    private Integer type;
    private String content;
    private String username;

    private String tokenName;

    private String modelName;

    private Integer quota;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer cachedTokens;

    private Integer useTime;

    private Boolean isStream;

    // 字段名 channelId 与 DB 列 channel 不一致（DB 列无 _id 后缀），需显式映射
    @TableField("channel")
    private Integer channelId;

    private String channelName;

    private Integer tokenId;

    @TableField("`group`")
    private String group;

    private String ip;

    private String requestId;

    private String upstreamRequestId;

    private Integer keyIndex;

    private String other;
}
