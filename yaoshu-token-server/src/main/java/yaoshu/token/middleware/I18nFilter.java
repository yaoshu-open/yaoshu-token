package yaoshu.token.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 国际化中间件  * <p>
 * 从请求中解析语言偏好，注入 LocaleContextHolder，使 I18nUtils.get() 自动获取当前请求语言。
 * <p>
 * 解析优先级：
 * <ol>
 * <li>用户设置（由认证中间件注入的 user_setting.language）</li>
 * <li>Accept-Language Header</li>
 * <li>Cookie "lang"</li>
 * <li>默认语言 "en"</li>
 * </ol>
 */
public class I18nFilter extends OncePerRequestFilter {

    /** 支持的语言列表 */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "zh-CN", "zh-TW");

    /** 默认语言*/
    private static final String DEFAULT_LANG = "en";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String lang = detectLanguage(request);
        request.setAttribute("lang", lang);
        Locale locale = toLocale(lang);
        LocaleContextHolder.setLocale(locale);
        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * 检测语言偏好
     */
    private String detectLanguage(HttpServletRequest request) {
        // 1. 用户设置（由认证中间件注入）
        Object userSettingLang = request.getAttribute("user_setting_language");
        if (userSettingLang instanceof String s && !s.isEmpty() && isSupported(s)) {
            return s;
        }

        // 2. Accept-Language Header
        String acceptLang = request.getHeader("Accept-Language");
        if (acceptLang != null && !acceptLang.isEmpty()) {
            String parsed = parseAcceptLanguage(acceptLang);
            if (parsed != null) {
                return parsed;
            }
        }

        // 3. Cookie "lang"
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("lang".equals(cookie.getName()) && cookie.getValue() != null) {
                    String cookieLang = cookie.getValue();
                    if (isSupported(cookieLang)) {
                        return cookieLang;
                    }
                }
            }
        }

        // 4. 默认语言
        return DEFAULT_LANG;
    }

    /**
     * 解析 Accept-Language Header
     */
    private String parseAcceptLanguage(String acceptLang) {
        String[] parts = acceptLang.split(",");
        for (String part : parts) {
            String lang = part.trim().split(";")[0].trim();
            if (isSupported(lang)) {
                return lang;
            }
            if (lang.startsWith("zh")) {
                return "zh-CN";
            }
            if (lang.startsWith("en")) {
                return "en";
            }
        }
        return null;
    }

    private boolean isSupported(String lang) {
        return SUPPORTED_LANGUAGES.contains(lang);
    }

    /**
     * 语言代码 → Locale
     */
    private static Locale toLocale(String lang) {
        return switch (lang) {
            case "zh-CN" -> new Locale("zh", "CN");
            case "zh-TW" -> new Locale("zh", "TW");
            default -> new Locale("en");
        };
    }
}
