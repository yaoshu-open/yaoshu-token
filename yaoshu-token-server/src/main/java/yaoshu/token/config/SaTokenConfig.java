package yaoshu.token.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：注册全局拦截器，替代 AuthFilter 的 URL 路径硬编码鉴权。
 * <p>
 * 拦截规则：
 * <ul>
 * <li>拦截所有 /api/** 路径（除公开端点）</li>
 * <li>公开端点（login/register/logout）不拦截，由 Controller 自行处理</li>
 * <li>/v1/** AI 中转路由不拦截（走 TokenAuthFilter）</li>
 * </ul>
 * <p>
 * 角色校验：Controller 方法通过 @SaCheckRole/@SaCheckPermission 注解声明
 * （Phase 2 响应格式迁移时批量添加，Phase 1 暂不加）
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/api/**")
                // 公开端点清单单一数据源：PublicPaths（与 AuthFilter 共享，避免双份冗余不同步）
                .excludePathPatterns(PublicPaths.excludePatterns());
    }
}
