package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import yaoshu.token.pojo.entity.TwoFa;

/**
 * 2FA 实体 Mapper
 */
@Mapper
public interface TwoFaMapper extends BaseMapper<TwoFa> {

    /** 按用户 ID 查询 2FA 记录 */
    @Select("SELECT * FROM two_fas WHERE user_id = #{userId} AND deleted_at IS NULL LIMIT 1")
    TwoFa getByUserId(@Param("userId") Integer userId);

    /** 禁用 2FA（软删除） */
    @Update("UPDATE two_fas SET is_enabled = 0, deleted_at = NOW(3) WHERE user_id = #{userId}")
    int disableByUserId(@Param("userId") Integer userId);
}
