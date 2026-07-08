package yaoshu.token.entity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import yaoshu.token.BaseIntegrationTest;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 3 E08：验证删除冗余 @TableField 后 MyBatis-Plus 自动驼峰映射正确性。
 * <p>
 * 对项目所有 BaseMapper（yaoshu.token 包下）执行 selectList(LIMIT 1)，验证生成的 SQL 列名
 * 全部存在（驼峰自动映射结果与 DB 列名一致，或保留的 @TableField 显式映射正确）。
 * 排除 yue-library 等非项目 Mapper。直接使用 Mapper（不经 Controller/Jackson），精准验证持久层映射。
 */
class EntityAutoMappingIT extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @SuppressWarnings("rawtypes")
    @Test
    void allEntityColumnMappingValid() {
        Map<String, BaseMapper> mappers = applicationContext.getBeansOfType(BaseMapper.class);
        assertThat(mappers).as("应至少注册一个 BaseMapper bean").isNotEmpty();

        mappers.forEach((beanName, mapper) -> {
            // 只验证项目包下的 Mapper，排除 yue-library 等框架自带 Mapper
            boolean isProjectMapper = Arrays.stream(mapper.getClass().getInterfaces())
                .anyMatch(iface -> iface.getName().startsWith("yaoshu.token."));
            if (!isProjectMapper) {
                return;
            }

            QueryWrapper wrapper = new QueryWrapper();
            wrapper.last("LIMIT 1");
            assertThatCode(() -> mapper.selectList(wrapper))
                .as(beanName + " 表自动驼峰映射").doesNotThrowAnyException();
        });
    }
}
