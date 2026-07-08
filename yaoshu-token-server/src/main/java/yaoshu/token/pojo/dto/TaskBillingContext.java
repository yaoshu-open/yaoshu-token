package yaoshu.token.pojo.dto;

import lombok.Data;

import java.util.Map;

/**
 * 任务计费上下文  * <p>
 * 记录任务提交时的计费参数快照，供轮询阶段重新计算额度。
 */
@Data
public class TaskBillingContext {

    /** 模型单价 */
    private Double modelPrice;

    /** 分组倍率 */
    private Double groupRatio;

    /** 模型倍率 */
    private Double modelRatio;

    /** 附加倍率（时长、分辨率等） */
    private Map<String, Double> otherRatios;

    /** 模型名称（必须为 OriginModelName） */
    private String originModelName;

    /** 按次计费：跳过轮询阶段的差额结算 */
    private Boolean perCallBilling;
}
