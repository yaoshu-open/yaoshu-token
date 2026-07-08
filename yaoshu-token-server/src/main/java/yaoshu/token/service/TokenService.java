package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.pojo.entity.Token;

import java.util.List;

/**
 * Token 服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenMapper tokenMapper;

    // ======================== 查询方法 ========================

    /**
     * 根据 ID 获取 Token      */
    public Token getById(Integer id) {
        if (id == null || id == 0) {
            return null;
        }
        return tokenMapper.selectById(id);
    }

    /**
     * 根据 key 获取 Token      */
    public Token getByKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return tokenMapper.selectOne(
                new LambdaQueryWrapper<Token>().eq(Token::getKey, key)
        );
    }

    /**
     * 校验用户 Token 有效性      * 返回 null 表示 Token 无效
     */
    public Token validateToken(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        Token token = getByKey(key);
        if (token == null) {
            return null;
        }
        // 状态检查
        int tokenStatus = token.getStatus() != null ? token.getStatus() : 0;
        if (tokenStatus == 2 || tokenStatus == 3 || tokenStatus != 1) {
            // TokenStatusExhausted=2 / TokenStatusExpired=3 / not TokenStatusEnabled=1
            return null;
        }
        // 过期检查（expiredTime = -1 表示永不过期）
        Long expiredTime = token.getExpiredTime();
        if (expiredTime != null && expiredTime != -1 && expiredTime < System.currentTimeMillis() / 1000) {
            // 非 Redis 模式下更新状态
            Token update = new Token();
            update.setId(token.getId());
            update.setStatus(3); // TokenStatusExpired
            tokenMapper.updateById(update);
            return null;
        }
        // 配额检查（无限配额跳过）
        if (!Boolean.TRUE.equals(token.getUnlimitedQuota())) {
            Long remainQuota = token.getRemainQuota();
            if (remainQuota != null && remainQuota <= 0) {
                Token update = new Token();
                update.setId(token.getId());
                update.setStatus(2); // TokenStatusExhausted
                tokenMapper.updateById(update);
                return null;
            }
        }
        return token;
    }

    /**
     * 根据 ID 和 userId（用户权限）获取 Token      */
    public Token getByIdAndUser(Integer id, Integer userId) {
        if (id == null || id == 0 || userId == null || userId == 0) {
            return null;
        }
        return tokenMapper.selectOne(
                new LambdaQueryWrapper<Token>()
                        .eq(Token::getId, id)
                        .eq(Token::getUserId, userId)
        );
    }

    /**
     * 获取用户所有 Token      */
    public List<Token> getAllByUserId(Integer userId) {
        PageHelper.startPage(ServletUtils.getRequest());
        return tokenMapper.selectList(
                new LambdaQueryWrapper<Token>()
                        .eq(Token::getUserId, userId)
                        .orderByDesc(Token::getId)
        );
    }

    /**
     * 搜索用户 Token      */
    public List<Token> searchByUserId(Integer userId, String keyword, String key) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Token> qw = new LambdaQueryWrapper<Token>()
                .eq(Token::getUserId, userId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(Token::getName, keyword);
        }
        if (key != null && !key.isEmpty()) {
            qw.like(Token::getKey, key);
        }
        qw.orderByDesc(Token::getId);
        return tokenMapper.selectList(qw);
    }

    /**
     * 统计用户 Token 数量      */
    public long countByUserId(Integer userId) {
        return tokenMapper.selectCount(
                new LambdaQueryWrapper<Token>().eq(Token::getUserId, userId)
        );
    }

    // ======================== 写操作 ========================

    /**
     * 创建 Token      */
    @Transactional(rollbackFor = Exception.class)
    public Token create(Token token) {
        tokenMapper.insert(token);
        return token;
    }

    /**
     * 更新 Token      */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Token token) {
        LambdaUpdateWrapper<Token> uw = new LambdaUpdateWrapper<>();
        uw.eq(Token::getId, token.getId());
        if (token.getName() != null) uw.set(Token::getName, token.getName());
        if (token.getStatus() != null) uw.set(Token::getStatus, token.getStatus());
        if (token.getExpiredTime() != null) uw.set(Token::getExpiredTime, token.getExpiredTime());
        if (token.getRemainQuota() != null) uw.set(Token::getRemainQuota, token.getRemainQuota());
        if (token.getUnlimitedQuota() != null) uw.set(Token::getUnlimitedQuota, token.getUnlimitedQuota());
        if (token.getModelLimitsEnabled() != null) uw.set(Token::getModelLimitsEnabled, token.getModelLimitsEnabled());
        if (token.getModelLimits() != null) uw.set(Token::getModelLimits, token.getModelLimits());
        if (token.getAllowIps() != null) uw.set(Token::getAllowIps, token.getAllowIps());
        if (token.getGroup() != null) uw.set(Token::getGroup, token.getGroup());
        if (token.getCrossGroupRetry() != null) uw.set(Token::getCrossGroupRetry, token.getCrossGroupRetry());
        return tokenMapper.update(null, uw) > 0;
    }

    /**
     * 删除 Token      */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Integer id, Integer userId) {
        return tokenMapper.delete(
                new LambdaQueryWrapper<Token>()
                        .eq(Token::getId, id)
                        .eq(Token::getUserId, userId)
        ) > 0;
    }

    /**
     * 根据 ID 列表获取 Token 的 key 列表      */
    public List<Token> getKeysByIds(List<Integer> ids, Integer userId) {
        return tokenMapper.selectList(
                new LambdaQueryWrapper<Token>()
                        .select(Token::getId, Token::getKey)
                        .in(Token::getId, ids)
                        .eq(Token::getUserId, userId)
        );
    }
}
