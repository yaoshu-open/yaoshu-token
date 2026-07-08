package yaoshu.token.pojo.dto;

import lombok.Data;

/**
 * 任务私有数据  * <p>
 * 禁止返回给用户，内部可能包含 key 等隐私信息。以 JSON 存入 tasks.private_data 列。
 */
@Data
public class TaskPrivateData {

    /** 渠道密钥（Gemini/Vertex 场景） */
    private String key;

    /** 上游真实 task ID */
    private String upstreamTaskId;

    /** 任务成功后的结果 URL（视频地址等） */
    private String resultUrl;

    /** 计费来源："wallet" 或 "subscription" */
    private String billingSource;

    /** 订阅 ID，用于订阅退款 */
    private Integer subscriptionId;

    /** 令牌 ID，用于令牌额度退款 */
    private Integer tokenId;

    /** 计费参数快照（用于轮询阶段重新计算） */
    private TaskBillingContext billingContext;
}
