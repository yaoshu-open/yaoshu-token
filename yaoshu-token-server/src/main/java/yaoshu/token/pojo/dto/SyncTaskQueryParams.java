package yaoshu.token.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务查询参数  * <p>
 * 用于管理后台和用户端的任务列表筛选。
 */
@Data
public class SyncTaskQueryParams {

    private String platform;
    private String channelId;
    private String taskId;
    private String userId;
    private String action;
    private String status;
    private Long startTimestamp;
    private Long endTimestamp;
    private List<Integer> userIds;
}
