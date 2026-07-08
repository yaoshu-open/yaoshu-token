package yaoshu.token.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import yaoshu.token.pojo.entity.CustomOAuthProvider;

/**
 * 自定义 OAuth Provider 数据访问。
 */
@Mapper
public interface CustomOAuthProviderMapper extends BaseMapper<CustomOAuthProvider> {
}
