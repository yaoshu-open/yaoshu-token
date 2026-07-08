package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import yaoshu.token.pojo.entity.TwoFaBackupCode;

import java.util.List;

/**
 * 2FA 备用码 Mapper
 */
@Mapper
public interface TwoFaBackupCodeMapper extends BaseMapper<TwoFaBackupCode> {

    /** 按用户 ID 查询未使用的备用码 */
    @Select("SELECT * FROM two_fa_backup_codes WHERE user_id = #{userId} AND is_used = 0 AND deleted_at IS NULL")
    List<TwoFaBackupCode> getUnusedCodesByUserId(@Param("userId") Integer userId);

    /** 标记备用码为已使用 */
    @Update("UPDATE two_fa_backup_codes SET is_used = 1, used_at = NOW(3) WHERE id = #{id}")
    int markAsUsed(@Param("id") Integer id);

    /** 按用户 ID 删除旧备用码（软删除） */
    @Update("UPDATE two_fa_backup_codes SET deleted_at = NOW(3) WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Integer userId);
}
