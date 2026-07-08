package yaoshu.token.middleware;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import yaoshu.token.config.PublicPaths;

/**
 * 认证中间件（Sa-Token 通道）  * <p>
 * 仅负责 /api/* 管理端点的 Sa-Token 认证与上下文注入：
 * <ol>
 * <li>Sa-Token 登录校验：{@code StpUtil.isLogin()}，从 SaSession 获取用户信息</li>
 * <li>2FA 半登录态拦截：含 {@code 2fa_pending} 标记的会话仅允许访问 2FA 验证端点</li>
 * <li>yaoshu-user-id Header 一致性校验（跨服务调用时验证用户身份一致性）</li>
 * <li>封禁检查（status != 1 拒绝）</li>
 * <li>注入 request 上下文属性（id/role/username），供 Controller 使用</li>
 * </ol>
 * <p>
 * 角色检查不在本 Filter 中做，由 Controller 方法上的 {@code @SaCheckRole}/@SaCheckLogin 注解
 * 在 AOP 层（SaInterceptor）承担。
 * <p>
 * 大模型端点 /v1/* 使用独立的 AccessToken 通道（TokenAuthFilter），与本 Filter 无关。
 * <p>
 * 认证失败时抛出 ResultException，由 yue-library 内置 ResultErrorController（Filter 层异常）/ ResultExceptionHandler（Controller 层异常）统一转为 Result 响应。
 */
@Slf4j
public class AuthFilter implements jakarta.servlet.Filter {

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                          jakarta.servlet.ServletResponse servletResponse,
                          FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 0. 跳过公开端点（无需登录）— 公开端点清单单一数据源：PublicPaths
        String requestUri = request.getRequestURI();
        if (PublicPaths.isPublic(requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Sa-Token 登录校验（未登录 → 401，由 yue-library ResultErrorController 统一转为 HTTP 401）
        if (!StpUtil.isLogin()) {
            throw new ResultException(R.unauthorized());
        }

        Integer userId = StpUtil.getLoginIdAsInt();
        SaSession session = StpUtil.getSession();

        // 2. 2FA 半登录态拦截：pending 会话仅允许访问 2FA 验证端点（该端点不走 AuthFilter），其余受保护端点一律拒绝
        if (Boolean.TRUE.equals(session.get("2fa_pending"))) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("auth.require_2fa")));
        }

        String username = (String) session.get("username");
        Integer role = (Integer) session.get("role");
        Integer status = (Integer) session.get("status");

        // 3. yaoshu-user-id Header 校验（可选，用于跨服务调用时验证用户身份一致性）
        String apiUserIdStr = request.getHeader("yaoshu-user-id");
        if (apiUserIdStr != null && !apiUserIdStr.isEmpty()) {
            try {
                int apiUserId = Integer.parseInt(apiUserIdStr);
                if (userId != apiUserId) {
                    throw new ResultException(R.errorPrompt(I18nUtils.get("auth.user_id_mismatch")));
                }
            } catch (NumberFormatException e) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("auth.user_id_format_error")));
            }
        }

        // 4. 状态检查（封禁检查）
        if (status != null && status != 1) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("auth.user_banned")));
        }

        // 5. 校验 username/role 合法性
        if (username == null || username.trim().isEmpty() || role == null || role < 1 || role > 3) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("auth.user_info_invalid")));
        }

        // 6. 注入请求上下文
        response.setHeader("Auth-Version", "864b7076dbcd0a3c01b5520316720ebf");
        request.setAttribute("username", username);
        request.setAttribute("role", role);
        request.setAttribute("id", userId);
        // 注入 group（Playground 的 DistributorFilter 依赖 USING_GROUP="group" 进行渠道分发）
        Object group = session.get("group");
        if (group != null) {
            request.setAttribute("group", group);
            request.setAttribute("user_group", group);
        }

        chain.doFilter(request, response);
    }
}
