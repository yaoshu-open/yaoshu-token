package yaoshu.token.middleware;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 可选认证中间件  * <p>
 * 尝试从 Sa-Token 获取当前登录用户 ID，不强制要求登录。用于不需要认证但希望获取用户上下文的接口。
 */
@Slf4j
public class TryUserAuthFilter implements jakarta.servlet.Filter {

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                          jakarta.servlet.ServletResponse servletResponse,
                          FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        // 尝试从 Sa-Token 获取登录用户 ID（不强制登录），命中则注入请求上下文
        if (StpUtil.isLogin()) {
            request.setAttribute("id", StpUtil.getLoginIdAsInt());
        }
        chain.doFilter(request, (HttpServletResponse) servletResponse);
    }
}
