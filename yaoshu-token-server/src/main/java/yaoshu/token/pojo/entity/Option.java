package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统选项实体  *
 * @author yaoshu
 */
@Data
@TableName("options")
public class Option {

    @TableId("`key`")
    private String key;

    private String value;
}
