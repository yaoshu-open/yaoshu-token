package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yaoshu.token.pojo.entity.PasskeyCredential;

/**
 * Passkey 凭证 Mapper  */
public interface PasskeyMapper extends BaseMapper<PasskeyCredential> {

    /** 按用户 ID 查询 */
    @Select("SELECT * FROM passkey_credentials WHERE user_id = #{userId}")
    PasskeyCredential selectByUserId(@Param("userId") int userId);

    /** 按凭证 ID 查询 */
    @Select("SELECT * FROM passkey_credentials WHERE credential_id = #{credentialId}")
    PasskeyCredential selectByCredentialId(@Param("credentialId") String credentialId);

    /** 按用户 ID 删除 */
    @Delete("DELETE FROM passkey_credentials WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);
}
