package yaoshu.token.pojo.dto;

import lombok.Data;

/**
 * 任务属性  * <p>
 * 存储任务的输入、上游/原始模型名称等信息，以 JSON 存入 tasks.properties 列。
 */
@Data
public class TaskProperties {

    private String input;

    /** 上游模型名称（模型映射后的真实模型） */
    private String upstreamModelName;

    /** 原始模型名称（用户请求的模型） */
    private String originModelName;
}
