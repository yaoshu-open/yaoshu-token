package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.RedemptionMapper;
import yaoshu.token.pojo.entity.Redemption;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 兑换码管理服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedemptionService {

    private final RedemptionMapper redemptionMapper;

    public List<Redemption> getAll() {
        PageHelper.startPage(ServletUtils.getRequest());
        return redemptionMapper.selectList(new LambdaQueryWrapper<Redemption>()
                .orderByDesc(Redemption::getId));
    }

    public List<Redemption> search(String keyword) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Redemption> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Redemption::getName, keyword).or().like(Redemption::getKey, keyword));
        }
        qw.orderByDesc(Redemption::getId);
        return redemptionMapper.selectList(qw);
    }

    public Redemption getById(int id) {
        return redemptionMapper.selectById(id);
    }

    /**
     * 批量创建兑换码      * <p>
     * 生成 count 个 UUID key，校验过期时间，逐个插入数据库。部分失败时返回已生成的 key 列表。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> add(int userId, Redemption redemption) {
        validateRedemption(redemption);
        long now = System.currentTimeMillis() / 1000;

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < redemption.getCount(); i++) {
            // 去掉横线生成 32 字符纯十六进制，匹配 DB 列 key CHAR(32)（与 VerificationService 一致）
            String key = UUID.randomUUID().toString().replace("-", "");
            Redemption r = new Redemption();
            r.setUserId(userId);
            r.setName(redemption.getName());
            r.setKey(key);
            r.setCreatedTime(now);
            r.setQuota(redemption.getQuota());
            r.setExpiredTime(redemption.getExpiredTime());
            r.setStatus(1); // 1 = 未使用
            try {
                redemptionMapper.insert(r);
                keys.add(key);
            } catch (Exception e) {
                log.error("创建兑换码失败", e);
                throw new RuntimeException(I18nUtils.get("redemption.create_failed_generic"));
            }
        }
        return keys;
    }

    /**
     * 更新兑换码      * <p>
     * status_only 模式只更新状态；否则更新 name/quota/expired_time。
     */
    @Transactional(rollbackFor = Exception.class)
    public Redemption update(Redemption redemption, boolean statusOnly) {
        Redemption existing = requireRedemption(redemption.getId());
        if (!statusOnly) {
            validateExpiredTime(redemption.getExpiredTime());
            existing.setName(redemption.getName());
            existing.setQuota(redemption.getQuota());
            existing.setExpiredTime(redemption.getExpiredTime());
        }
        existing.setStatus(redemption.getStatus());
        redemptionMapper.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(int id) {
        redemptionMapper.deleteById(id);
    }

    /**
     * 清理无效/过期的兑换码      */
    @Transactional(rollbackFor = Exception.class)
    public long deleteInvalid() {
        long now = System.currentTimeMillis() / 1000;
        // 删除已过期的兑换码
        LambdaUpdateWrapper<Redemption> uw = new LambdaUpdateWrapper<>();
        uw.eq(Redemption::getExpiredTime, 0L).or().lt(Redemption::getExpiredTime, now);
        return redemptionMapper.delete(uw);
    }

    private void validateRedemption(Redemption r) {
        if (r.getName() == null || r.getName().isBlank() || r.getName().length() > 20) {
            throw new IllegalArgumentException(I18nUtils.get("redemption.name_length"));
        }
        if (r.getCount() <= 0) {
            throw new IllegalArgumentException(I18nUtils.get("redemption.count_positive"));
        }
        if (r.getCount() > 100) {
            throw new IllegalArgumentException(I18nUtils.get("redemption.count_max"));
        }
        validateExpiredTime(r.getExpiredTime());
    }

    private void validateExpiredTime(Long expiredTime) {
        if (expiredTime != null && expiredTime != 0 && expiredTime < System.currentTimeMillis() / 1000) {
            throw new IllegalArgumentException(I18nUtils.get("redemption.expire_time_invalid"));
        }
    }

    private Redemption requireRedemption(int id) {
        Redemption r = redemptionMapper.selectById(id);
        if (r == null) {
            throw new IllegalArgumentException(I18nUtils.get("redemption.not_exists"));
        }
        return r;
    }
}
