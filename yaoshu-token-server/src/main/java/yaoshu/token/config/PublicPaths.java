package yaoshu.token.config;

import java.util.ArrayList;
import java.util.Set;

/**
 * 公开端点（无需登录）单一数据源。
 * <p>
 * 消除 {@link yaoshu.token.middleware.AuthFilter}（Filter 层）与
 * {@link SaTokenConfig}（Interceptor 层 excludePathPatterns）的双份冗余公开端点列表，
 * 避免两处不同步导致"AuthFilter 放行但 SaInterceptor 拦截"的鉴权不一致问题（Bug-002 根因）。
 * <p>
 * 两个消费方：
 * <ul>
 * <li>{@link yaoshu.token.middleware.AuthFilter}：用 {@link #isPublic(String)}（startsWith 语义）</li>
 * <li>{@link SaTokenConfig}：用 {@link #excludePatterns()}（前缀转 ant {@code /prefix/**}）</li>
 * </ul>
 */
public final class PublicPaths {

    private PublicPaths() {
    }

    /** 公开端点精确匹配（无需登录） */
    public static final Set<String> EXACT = Set.of(
            "/api/status", "/api/setup", "/api/notice", "/api/about",
            "/api/home_page_content", "/api/verification", "/api/reset_password",
            "/api/ratio_config", "/api/pricing", "/api/rankings",
            "/api/user/register", "/api/user/login", "/api/user/logout",
            "/api/user/login/2fa", "/api/user/password/reset",
            "/api/user/epay/notify", "/api/user/groups",
            "/api/stripe/webhook", "/api/creem/webhook", "/api/waffo/webhook",
            "/api/subscription/epay/notify", "/api/subscription/epay/return"
    );

    /** 公开端点前缀匹配（无需登录，AuthFilter 用 startsWith） */
    public static final String[] PREFIXES = {
            "/api/oauth/", "/api/user/oauth/",
            "/api/user/passkey/login/",
            "/api/legal/", "/api/payment/callback/",
            "/api/perf-metrics", "/api/uptime/",
            "/api/waffo-pancake/webhook/",
            "/api/subscription/epay/"
    };

    /**
     * 判断 URI 是否为公开端点（AuthFilter 用，startsWith 语义）。
     */
    public static boolean isPublic(String uri) {
        if (uri == null) {
            return false;
        }
        if (EXACT.contains(uri)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * SaInterceptor excludePathPatterns 用：精确路径 + 前缀转 ant（{@code /prefix/**}）。
     */
    public static String[] excludePatterns() {
        java.util.List<String> all = new ArrayList<>(EXACT);
        for (String prefix : PREFIXES) {
            all.add(prefix + "**");
        }
        return all.toArray(new String[0]);
    }
}
