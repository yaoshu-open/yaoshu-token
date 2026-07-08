package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yaoshu.token.pojo.entity.TopUp;

/**
 * 充值记录 Mapper  */
@Mapper
public interface TopUpMapper extends BaseMapper<TopUp> {
}
