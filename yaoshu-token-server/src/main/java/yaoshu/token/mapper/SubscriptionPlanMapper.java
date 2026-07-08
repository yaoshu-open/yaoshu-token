package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yaoshu.token.pojo.entity.SubscriptionPlan;

/**
 * 订阅计划 Mapper  */
@Mapper
public interface SubscriptionPlanMapper extends BaseMapper<SubscriptionPlan> {
}
