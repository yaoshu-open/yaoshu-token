package yaoshu.token.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ai.yue.library.base.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.service.OptionService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Turnstile 人机验证中间件  * <p>
 * Cloudflare Turnstile 验证，用于登录/注册等公开接口的防机器人。
 * <p>
 * 流程：
 * <ol>
 * <li>Turnstile 未启用则直接放行</li>
 * <li>session 中已有 turnstile 通过标记则放行</li>
 * <li>从 query 参数获取 turnstile token，POST 到 Cloudflare siteverify 校验</li>
 * <li>校验通过则在 session 中标记</li>
 * </ol>
 */
@Slf4j
public class TurnstileCheckFilter implements Filter {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final OptionService optionService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TurnstileCheckFilter(OptionService optionService) {
        this.optionService = optionService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Turnstile 未启用则直接放行
        if (!isTurnstileEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // session 中已有通过标记则放行
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object turnstileChecked = session.getAttribute("turnstile");
            if (turnstileChecked != null) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 从 query 参数获取 turnstile token
        String turnstileToken = request.getParameter("turnstile");
        if (turnstileToken == null || turnstileToken.isEmpty()) {
            sendError(response, I18nUtils.get("turnstile.token_empty"));
            return;
        }

        // POST 到 Cloudflare 校验
        String secretKey = getTurnstileSecretKey();
        String clientIP = ai.yue.library.web.util.ServletUtils.getClientIP(request);
        String formData = "secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8) +
                "&response=" + URLEncoder.encode(turnstileToken, StandardCharsets.UTF_8) +
                "&remoteip=" + URLEncoder.encode(clientIP, StandardCharsets.UTF_8);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SITEVERIFY_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // 解析响应
            com.alibaba.fastjson2.JSONObject result =
                    ai.yue.library.base.convert.Convert.toJSONObject(httpResponse.body());
            boolean success = result.getBooleanValue("success");

            if (!success) {
                sendError(response, I18nUtils.get("turnstile.verification_failed"));
                return;
            }

            // 校验通过，标记 session
            if (session == null) {
                session = request.getSession(true);
            }
            session.setAttribute("turnstile", true);
        } catch (Exception e) {
            log.error("Turnstile 校验请求失败: {}", e.getMessage());
            sendError(response, e.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private boolean isTurnstileEnabled() {
        return "true".equalsIgnoreCase(optionService.getValue("TurnstileCheckEnabled"));
    }

    private String getTurnstileSecretKey() {
        String key = optionService.getValue("TurnstileSecretKey");
        return key != null ? key : "";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
