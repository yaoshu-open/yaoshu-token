package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yaoshu.token.pojo.entity.Token;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Token Mapper
 *
 * @author yaoshu
 */
@Mapper
public interface TokenMapper extends BaseMapper<Token> {

    /** 原子减少 Token 配额*/
    @Update("UPDATE tokens SET remain_quota = remain_quota - #{quota} WHERE id = #{id}")
    int decreaseRemainQuota(@Param("id") Integer id, @Param("quota") long quota);

    /** 原子增加 Token 已用配额 */
    @Update("UPDATE tokens SET used_quota = used_quota + #{quota} WHERE id = #{id}")
    int increaseUsedQuota(@Param("id") Integer id, @Param("quota") long quota);

    /** 原子增加 Token 剩余配额（防负值） */
    @Update("UPDATE tokens SET remain_quota = remain_quota + #{quota} WHERE id = #{id} AND remain_quota + #{quota} >= 0")
    int increaseRemainQuotaSafe(@Param("id") Integer id, @Param("quota") long quota);

    /**
     * 批量更新 Token 配额（remain_quota += quota, used_quota -= quota, accessed_time 刷新），
     */
    @Update("UPDATE tokens SET remain_quota = remain_quota + #{quota}, used_quota = used_quota - #{quota}, "
            + "accessed_time = #{accessedTime} WHERE id = #{id}")
    int batchUpdateTokenQuota(@Param("id") Integer id, @Param("quota") long quota,
                              @Param("accessedTime") long accessedTime);
}
