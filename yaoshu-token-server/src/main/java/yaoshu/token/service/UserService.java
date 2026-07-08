package yaoshu.token.service;

import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.data.redis.client.Redis;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 用户服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final Redis redis;

    /** accessToken Redis 缓存 key 前缀（Cache-Aside，对应设计文档 §3.6） */
    private static final String ACCESS_TOKEN_CACHE_PREFIX = "access_token:";
    /** accessToken 缓存 TTL */
    private static final Duration ACCESS_TOKEN_CACHE_TTL = Duration.ofHours(1);

    // ======================== 查询方法 ========================

    /**
     * 根据 ID 获取用户      */
    public User getById(Integer id, boolean selectAll) {
        if (id == null || id == 0) {
            return null;
        }
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>().eq(User::getId, id);
        if (!selectAll) {
            qw.select(User.class, f -> !"password".equals(f.getProperty()));
        }
        return userMapper.selectOne(qw);
    }

    /**
     * 根据 username 或 email 查找用户（含密码），用于登录校验      */
    public User findByUsernameOrEmail(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .and(w -> w.eq(User::getUsername, username).or().eq(User::getEmail, username))
        );
    }

    /**
     * 校验密码      */
    public boolean validatePassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        String sha256Hex = sha256(rawPassword);
        String salted = sha256Hex + getPasswordSalt();
        return sha256(salted).equals(hashedPassword);
    }

    /**
     * 密码哈希      */
    public String hashPassword(String rawPassword) {
        String sha256Hex = sha256(rawPassword);
        String salted = sha256Hex + getPasswordSalt();
        return sha256(salted);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 密码盐值      */
    private String getPasswordSalt() {
        return "liaozhou666!";
    }

    /**
     * 根据 access_token 查找用户（Redis Cache-Aside 加速）      * <p>
     * 缓存策略：access_token:{token} → userId，TTL 1 小时。命中后按 userId 取完整 User，未命中查 DB 并回填。
     */
    public User findByAccessToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String cacheKey = ACCESS_TOKEN_CACHE_PREFIX + token;
        // 1. 先查 Redis 缓存
        Object cachedUserId = redis.get(cacheKey);
        if (cachedUserId != null) {
            try {
                Integer userId = Integer.valueOf(String.valueOf(cachedUserId));
                User cached = getById(userId, false);
                if (cached != null) {
                    return cached;
                }
            } catch (NumberFormatException ignored) {
                // 缓存值非法，回退查 DB
            }
        }
        // 2. 未命中查 DB
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccessToken, token)
        );
        // 3. 回填缓存（仅缓存命中的有效 token）
        if (user != null) {
            redis.set(cacheKey, user.getId(), ACCESS_TOKEN_CACHE_TTL);
        }
        return user;
    }

    /** 按 affCode 查找用户 */
    public User findByAffCode(String affCode) {
        if (affCode == null || affCode.isEmpty()) return null;
        return userMapper.selectByAffCode(affCode);
    }

    /**
     * 检查用户是否存在或已删除      */
    public boolean checkExistOrDeleted(String username, String email) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (email != null && !email.isEmpty()) {
            qw.and(w -> w.eq(User::getUsername, username).or().eq(User::getEmail, email));
        } else {
            qw.eq(User::getUsername, username);
        }
        return userMapper.selectCount(qw) > 0;
    }

    /**
     * 获取用户配额      */
    public long getUserQuota(Integer userId) {
        User user = getById(userId, true);
        return user != null && user.getQuota() != null ? user.getQuota() : 0;
    }

    /**
     * 获取用户已使用配额      */
    public long getUserUsedQuota(Integer userId) {
        User user = getById(userId, true);
        return user != null && user.getUsedQuota() != null ? user.getUsedQuota() : 0;
    }

    /**
     * 获取用户名      */
    public String getUsernameById(Integer userId) {
        User user = getById(userId, false);
        return user != null ? user.getUsername() : "";
    }

    /**
     * 获取用户分组      */
    public String getUserGroup(Integer userId) {
        User user = getById(userId, false);
        return user != null && user.getGroup() != null ? user.getGroup() : "default";
    }

    /**
     * 检查是否为管理员      */
    public boolean isAdmin(Integer userId) {
        if (userId == null || userId == 0) {
            return false;
        }
        User user = getById(userId, false);
        return user != null && user.getRole() != null && user.getRole() >= 2; // RoleAdminUser = 2
    }

    /**
     * 获取根用户      */
    public User getRootUser() {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getRole, 3) // RoleRootUser = 3
        );
    }

    /**
     * 查询所有管理员用户（role >= ROLE_ADMIN）      */
    public List<User> listAdminUsers() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User.class, f -> !"password".equals(f.getProperty()))
                        .ge(User::getRole, 2) // RoleAdminUser = 2
        );
    }

    // ======================== 分页/搜索 ========================

    /**
     * 分页获取所有用户      */
    public List<User> getAllUsers() {
        PageHelper.startPage(ServletUtils.getRequest());
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User.class, f -> !"password".equals(f.getProperty()))
                        .orderByDesc(User::getId)
        );
    }

    /**
     * 获取用户总数
     */
    public long countAll() {
        return userMapper.selectCount(null);
    }

    /**
     * 搜索用户      */
    public List<User> searchUsers(String keyword, String group, Integer role, Integer status) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getDisplayName, keyword));
            // 尝试按 ID 搜索
            try {
                int id = Integer.parseInt(keyword);
                qw.or().eq(User::getId, id);
            } catch (NumberFormatException ignored) {
            }
        }
        if (group != null && !group.isEmpty()) {
            qw.eq(User::getGroup, group);
        }
        if (role != null) {
            qw.eq(User::getRole, role);
        }
        if (status != null) {
            qw.eq(User::getStatus, status);
        }
        qw.select(User.class, f -> !"password".equals(f.getProperty()))
                .orderByDesc(User::getId);
        return userMapper.selectList(qw);
    }

    // ======================== 写操作 ========================

    /**
     * 创建用户      */
    @Transactional(rollbackFor = Exception.class)
    public User createUser(User user) {
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(System.currentTimeMillis() / 1000);
        }
        if (user.getLastLoginAt() == null) {
            user.setLastLoginAt(0L);
        }
        userMapper.insert(user);
        return user;
    }

    /**
     * 更新用户      */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user, boolean updatePassword) {
        // accessToken 变更时删除旧缓存 key（Cache-Aside 写时失效，对应设计文档 §3.6）
        if (user.getAccessToken() != null && user.getId() != null) {
            User existing = userMapper.selectById(user.getId());
            if (existing != null && existing.getAccessToken() != null
                    && !existing.getAccessToken().equals(user.getAccessToken())) {
                redis.delete(ACCESS_TOKEN_CACHE_PREFIX + existing.getAccessToken());
            }
        }
        if (!updatePassword) {
            user.setPassword(null);
        }
        return userMapper.updateById(user) > 0;
    }

    /**
     * 软删除用户      */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Integer userId) {
        return userMapper.deleteById(userId) > 0;
    }

    /**
     * 增加用户配额（原子操作）      */
    @Transactional(rollbackFor = Exception.class)
    public void increaseUserQuota(Integer userId, int quota) {
        if (quota < 0) {
            throw new IllegalArgumentException(I18nUtils.get("quota.cannot_be_negative"));
        }
        // 原子 SQL 更新：quota = quota + ?
        userMapper.increaseQuota(userId, quota);
    }

    /**
     * 减少用户配额（原子操作）      */
    @Transactional(rollbackFor = Exception.class)
    public void decreaseUserQuota(Integer userId, int quota) {
        if (quota < 0) {
            throw new IllegalArgumentException(I18nUtils.get("quota.cannot_be_negative"));
        }
        // 原子 SQL 更新：quota = quota - ?
        userMapper.decreaseQuota(userId, quota);
    }

    /**
     * Delta 更新用户配额      */
    @Transactional(rollbackFor = Exception.class)
    public void deltaUpdateQuota(Integer userId, int delta) {
        if (delta > 0) {
            increaseUserQuota(userId, delta);
        } else if (delta < 0) {
            decreaseUserQuota(userId, -delta);
        }
    }

    /**
     * 更新用户最后登录时间      */
    public void updateLastLoginAt(Integer userId) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(System.currentTimeMillis() / 1000);
        userMapper.updateById(update);
    }

    // ======================== OAuth 绑定检查 ========================

    public boolean isEmailTaken(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    public boolean isGitHubIdTaken(String githubId) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getGithubId, githubId)) > 0;
    }

    public boolean isWeChatIdTaken(String wechatId) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getWechatId, wechatId)) > 0;
    }
}
