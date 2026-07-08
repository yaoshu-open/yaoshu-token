package yaoshu.token.pojo.vo;

import lombok.Data;

/**
 * GET /api/setup 响应数据  */
@Data
public class SetupStatusVO {

    /** 系统是否已完成初始化（setups 表有记录 = true） */
    private boolean status;

    /** 是否已存在 root 用户（仅 status=false 时有意义，前端据此决定是否显示账号填写步骤） */
    private boolean rootInit;

    /** 当前数据库类型 */
    private String databaseType;
}
