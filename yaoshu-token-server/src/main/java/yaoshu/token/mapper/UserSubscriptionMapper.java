package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yaoshu.token.pojo.entity.UserSubscription;

/**
 * 用户订阅 Mapper  */
@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {
}
