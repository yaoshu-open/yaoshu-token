package yaoshu.token.config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色接口实现。
 * <p>
 * 角色映射：users.role → Sa-Token 角色列表
 * <ul>
 * <li>1 (CommonUser) → 空列表</li>
 * <li>2 (AdminUser) → ["admin"]</li>
 * <li>3 (RootUser) → ["root"]</li>
 * </ul>
 * <p>
 * 权限列表：本项目不使用 Sa-Token 权限粒度，返回空列表。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // loginId = userId（StpUtil.login(userId) 传入的）
        Integer userId = Integer.valueOf(loginId.toString());
        User user = userService.getById(userId, false);
        if (user == null || user.getRole() == null) {
            return Collections.emptyList();
        }

        List<String> roles = new ArrayList<>();
        if (user.getRole() >= 3) {
            roles.add("root");
        }
        if (user.getRole() >= 2) {
            roles.add("admin");
        }
        return roles;
    }
}
