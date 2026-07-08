package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth 绑定实体  *
 * @author yaoshu
 */
@Data
@TableName("user_oauth_bindings")
public class UserOAuthBinding {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer providerId;

    private String providerUserId;

    private LocalDateTime createdAt;
}
