package yaoshu.token.config;

import ai.yue.library.data.redis.client.Redis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yaoshu.token.middleware.*;
import yaoshu.token.service.ChannelService;
import yaoshu.token.service.UserService;
import yaoshu.token.service.TokenService;
import yaoshu.token.service.OptionService;
import yaoshu.token.spi.ChannelSelector;

/**
 * Filter 注册与顺序配置  * <p>
 * Filter 执行顺序 = setOrder() 数字越小越先执行。整体链序：
 * <ol>
 *   <li>RequestIdFilter (10) — 生成请求 ID</li>
 *   <li>I18nFilter (20) — 语言解析</li>
 *   <li>LoggerFilter (30) — 请求日志</li>
 *   <li>HeaderNavFilter (40) — 导航头</li>
 *   <li>DisableCacheFilter (50) — 禁用缓存（敏感操作）</li>
 *   <li>CacheFilter (60) — 静态资源缓存</li>
 *   <li>RateLimitFilter (100) — 全局限流 /api/*</li>
 *   <li>TurnstileCheckFilter (110) — 人机验证（注册/登录）</li>
 *   <li>EmailRateLimitFilter (120) — 邮箱验证码限流</li>
 *   <li>AuthFilter-UserAuth (200) — 普通用户认证 /api/user/self/*</li>
 *   <li>AuthFilter-AdminAuth (210) — 管理员认证 /api/channel/* 等</li>
 *   <li>AuthFilter-RootAuth (220) — 根用户认证 /api/option/* 等</li>
 *   <li>TryUserAuthFilter (230) — 可选认证</li>
 *   <li>TokenAuthFilter (300) — API Key 认证 /v1/*</li>
 *   <li>TokenAuthReadOnlyFilter (320) — 只读 Token 认证</li>
 *   <li>SecureVerificationFilter (400) — 安全验证</li>
 *   <li>ModelRateLimitFilter (410) — 模型级限流</li>
 *   <li>DistributorFilter (500) — 渠道分发 /v1/*</li>
 *   <li>JimengAdapterFilter (510) — 即梦适配器</li>
 *   <li>KlingAdapterFilter (520) — Kling 适配器</li>
 *   <li>PerformanceFilter (900) — 性能监控</li>
 *   <li>StatsFilter (910) — 访问统计</li>
 *   <li>BodyCleanupFilter (999) — 请求体清理（最后执行，try-finally 语义）</li>
 * </ol>
 */
@Configuration
public class FilterConfig {

    private final UserService userService;
    private final TokenService tokenService;
    private final ChannelService channelService;
    private final Redis redis;
    private final OptionService optionService;
    private final ChannelSelector channelSelector;

    public FilterConfig(UserService userService, TokenService tokenService, ChannelService channelService,
                        Redis redis, OptionService optionService,
                        @Autowired(required = false) ChannelSelector channelSelector) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.channelService = channelService;
        this.redis = redis;
        this.optionService = optionService;
        this.channelSelector = channelSelector;
    }

    // ==================== 全局 Filter ====================

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestIdFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<I18nFilter> i18nFilter() {
        FilterRegistrationBean<I18nFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new I18nFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(20);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<LoggerFilter> loggerFilter() {
        FilterRegistrationBean<LoggerFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LoggerFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(30);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<HeaderNavFilter> headerNavFilter() {
        FilterRegistrationBean<HeaderNavFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HeaderNavFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(40);
        return bean;
    }

    // ==================== 缓存控制 Filter ====================

    @Bean
    public FilterRegistrationBean<DisableCacheFilter> disableCacheFilter() {
        FilterRegistrationBean<DisableCacheFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new DisableCacheFilter());
        // 敏感操作：渠道密钥查看、安全验证
        bean.addUrlPatterns("/api/channel/*/key");
        bean.setOrder(50);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CacheFilter> cacheFilter() {
        FilterRegistrationBean<CacheFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CacheFilter());
        bean.addUrlPatterns("/static/*", "/assets/*");
        bean.setOrder(60);
        return bean;
    }

    // ==================== 限流 Filter ====================

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RateLimitFilter(redis));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(100);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TurnstileCheckFilter> turnstileCheckFilter() {
        FilterRegistrationBean<TurnstileCheckFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TurnstileCheckFilter(optionService));
        // 公开接口：登录、注册、签到
        bean.addUrlPatterns("/api/user/login", "/api/user/register",
                "/api/user/self/checkin", "/api/verification",
                "/api/reset_password");
        bean.setOrder(110);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<EmailRateLimitFilter> emailRateLimitFilter() {
        FilterRegistrationBean<EmailRateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new EmailRateLimitFilter(redis));
        bean.addUrlPatterns("/api/verification", "/api/reset_password");
        bean.setOrder(120);
        return bean;
    }

    // ==================== 认证 Filter（Sa-Token 通道） ====================

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        FilterRegistrationBean<AuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new AuthFilter());
        // 覆盖所有 /api/* 管理端点，公开端点在 AuthFilter 内部跳过
        // 角色检查由 Controller 上的 @SaCheckRole/@SaCheckLogin 注解承担（AOP 层 SaInterceptor）
        // /pg/* Playground 走 Sa-Token 会话认证，非 API Key 认证
        bean.addUrlPatterns("/api/*", "/pg/*");
        bean.setOrder(200);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TryUserAuthFilter> tryUserAuthFilter() {
        FilterRegistrationBean<TryUserAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TryUserAuthFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(230);
        return bean;
    }

    // ==================== 认证 Filter（Token 模式） ====================

    @Bean
    public FilterRegistrationBean<TokenAuthFilter> tokenAuthFilter() {
        FilterRegistrationBean<TokenAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TokenAuthFilter(tokenService, userService));
        // AI API 中转路由（/pg/* 走 AuthFilter Sa-Token 会话认证，不在此注册）
        bean.addUrlPatterns("/v1/*", "/v1beta/*", "/mj/*",
                "/suno/*");
        bean.setOrder(300);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TokenAuthReadOnlyFilter> tokenAuthReadOnlyFilter() {
        FilterRegistrationBean<TokenAuthReadOnlyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TokenAuthReadOnlyFilter(tokenService, userService));
        // 只读查询接口（模型列表、用量查询等）
        bean.addUrlPatterns("/api/usage/*");
        bean.setOrder(320);
        return bean;
    }

    // ==================== 安全验证 Filter ====================

    @Bean
    public FilterRegistrationBean<SecureVerificationFilter> secureVerificationFilter() {
        FilterRegistrationBean<SecureVerificationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SecureVerificationFilter());
        // 敏感操作
        bean.addUrlPatterns("/api/channel/*/key");
        bean.setOrder(400);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ModelRateLimitFilter> modelRateLimitFilter() {
        FilterRegistrationBean<ModelRateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ModelRateLimitFilter(redis, optionService));
        bean.addUrlPatterns("/v1/chat/completions", "/v1/embeddings");
        bean.setOrder(410);
        return bean;
    }

    // ==================== 核心业务 Filter ====================

    @Bean
    public FilterRegistrationBean<DistributorFilter> distributorFilter() {
        FilterRegistrationBean<DistributorFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new DistributorFilter(channelService, channelSelector));
        // 渠道分发：所有 AI API 中转路由
        bean.addUrlPatterns("/v1/*", "/v1beta/*", "/mj/*",
                "/suno/*", "/pg/*");
        bean.setOrder(500);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JimengAdapterFilter> jimengAdapterFilter() {
        FilterRegistrationBean<JimengAdapterFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JimengAdapterFilter());
        bean.addUrlPatterns("/v1/jimeng/*");
        bean.setOrder(510);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<KlingAdapterFilter> klingAdapterFilter() {
        FilterRegistrationBean<KlingAdapterFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new KlingAdapterFilter());
        bean.addUrlPatterns("/v1/kling/*");
        bean.setOrder(520);
        return bean;
    }

    // ==================== 监控与清理 Filter（末尾） ====================

    @Bean
    public FilterRegistrationBean<PerformanceFilter> performanceFilter() {
        FilterRegistrationBean<PerformanceFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new PerformanceFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(900);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<StatsFilter> statsFilter() {
        FilterRegistrationBean<StatsFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new StatsFilter());
        bean.addUrlPatterns("/v1/*", "/v1beta/*");
        bean.setOrder(910);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<BodyCleanupFilter> bodyCleanupFilter() {
        FilterRegistrationBean<BodyCleanupFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new BodyCleanupFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(999);
        return bean;
    }
}
