package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 2FA 备用码实体  *
 * @author yaoshu
 */
@Data
@TableName("two_fa_backup_codes")
public class TwoFaBackupCode {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String codeHash;

    private Boolean isUsed;

    private Long usedAt;

    private Long createdAt;

    private Long deletedAt;
}
