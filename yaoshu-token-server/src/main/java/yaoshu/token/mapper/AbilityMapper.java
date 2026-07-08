package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yaoshu.token.pojo.dto.AbilityWithChannel;
import yaoshu.token.pojo.entity.Ability;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Ability Mapper  *
 * @author yaoshu
 */
@Mapper
public interface AbilityMapper extends BaseMapper<Ability> {

    /**
     * 获取所有启用能力及其渠道类型      */
    @Select("SELECT abilities.*, channels.type AS channel_type "
            + "FROM abilities "
            + "LEFT JOIN channels ON abilities.channel_id = channels.id "
            + "WHERE abilities.enabled = true")
    List<AbilityWithChannel> getAllEnableAbilityWithChannels();

    /**
     * 判断指定渠道在某分组下是否启用了该模型      */
    @Select("SELECT COUNT(*) FROM abilities "
            + "WHERE `group` = #{group} AND model = #{model} AND channel_id = #{channelId} AND enabled = true")
    long countEnabledForGroupModelChannel(@Param("group") String group,
                                          @Param("model") String model,
                                          @Param("channelId") int channelId);

    /**
     * 查询某分组+模型下所有启用的能力（含 priority/weight/channel_id），按优先级降序，
     */
    @Select("SELECT * FROM abilities "
            + "WHERE `group` = #{group} AND model = #{model} AND enabled = true "
            + "ORDER BY priority DESC, weight DESC")
    List<Ability> selectEnabledAbilities(@Param("group") String group, @Param("model") String model);
}
