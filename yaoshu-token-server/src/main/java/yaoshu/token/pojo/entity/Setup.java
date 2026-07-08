package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统初始化设置实体  *
 * @author yaoshu
 */
@Data
@TableName("setups")
public class Setup {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String version;

    private Long initializedAt;
}
