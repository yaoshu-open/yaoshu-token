package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yaoshu.token.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * User Mapper
 *
 * @author yaoshu
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 原子增加配额 */
    @Update("UPDATE users SET quota = quota + #{quota} WHERE id = #{userId}")
    int increaseQuota(@Param("userId") Integer userId, @Param("quota") int quota);

    /** 原子减少配额 */
    @Update("UPDATE users SET quota = quota - #{quota} WHERE id = #{userId}")
    int decreaseQuota(@Param("userId") Integer userId, @Param("quota") int quota);

    /**
     * 批量聚合更新用户配额、已用配额、请求次数。      */
    @Update("UPDATE users SET quota = quota + #{quota}, used_quota = used_quota + #{usedQuota}, "
            + "request_count = request_count + #{requestCount} WHERE id = #{userId}")
    int batchUpdateUserQuota(@Param("userId") Integer userId, @Param("quota") int quota,
                             @Param("usedQuota") int usedQuota, @Param("requestCount") int requestCount);

    /** 按 affCode 查找用户 */
    @Select("SELECT * FROM users WHERE aff_code = #{affCode} AND status = 1 AND deleted_at IS NULL LIMIT 1")
    User selectByAffCode(@Param("affCode") String affCode);
}
