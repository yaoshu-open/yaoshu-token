package yaoshu.token.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置，用于统一处理尾部斜杠路径映射
 * <p>
 * Go Gin 框架自动兼容 `/self` 和 `/self/` 两种路径格式，
 * Spring MVC 默认严格区分。通过配置此选项实现兼容。
 * <p>
 */
@Configuration
public class PathMatchConfig implements WebMvcConfigurer {

    /**
     * 启用尾部斜杠匹配（Trailing Slash Matching）
     * <p>
     * 配置后，以下路径将等效：
     * - /api/data/self     == /api/data/self/
     * - /api/log/self      == /api/log/self/
     * - /api/user/self     == /api/user/self/
     * <p>
     * 注意：此配置仅对路径末尾的单个斜杠生效（`/` 或空），
     * 多个斜杠（如 `//`）不在兼容范围内。
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 配置路径匹配器支持尾部斜杠
        configurer.setUseTrailingSlashMatch(true);
    }
}