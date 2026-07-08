package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yaoshu.token.pojo.entity.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Channel Mapper
 *
 * @author yaoshu
 */
@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

    /** 批量增加渠道已用配额 */
    @Update("UPDATE channels SET used_quota = used_quota + #{quota} WHERE id = #{id}")
    int increaseUsedQuota(@Param("id") Integer id, @Param("quota") int quota);
}
