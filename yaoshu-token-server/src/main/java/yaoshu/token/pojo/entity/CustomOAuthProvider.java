package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth 提供者实体  *
 * @author yaoshu
 */
@Data
@TableName("custom_oauth_providers")
public class CustomOAuthProvider {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
    private String slug;
    private String icon;
    private Boolean enabled;

    private String clientId;

    private String clientSecret;

    private String authorizationEndpoint;

    private String tokenEndpoint;

    private String userInfoEndpoint;

    private String scopes;

    private String userIdField;

    private String usernameField;

    private String displayNameField;

    private String emailField;

    private String wellKnown;

    private Integer authStyle;

    private String accessPolicy;

    private String accessDeniedMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
