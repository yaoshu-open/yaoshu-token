package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yaoshu.token.pojo.entity.UserOAuthBinding;

/**
 * 用户 OAuth 绑定数据访问。
 */
@Mapper
public interface UserOAuthBindingMapper extends BaseMapper<UserOAuthBinding> {
}
