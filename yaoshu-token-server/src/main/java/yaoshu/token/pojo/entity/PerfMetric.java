package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 性能指标实体  *
 * @author yaoshu
 */
@Data
@TableName("perf_metrics")
public class PerfMetric {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String modelName;

    @TableField("`group`")
    private String group;

    private Long bucketTs;

    private Long requestCount;

    private Long successCount;

    private Long totalLatencyMs;

    private Long ttftSumMs;

    private Long ttftCount;

    private Long outputTokens;

    private Long generationMs;
}
