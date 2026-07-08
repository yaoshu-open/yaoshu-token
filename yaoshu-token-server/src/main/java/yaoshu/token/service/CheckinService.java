package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.config.operation.CheckinSettingConfig;
import yaoshu.token.mapper.CheckinMapper;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.Checkin;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.User;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 签到服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinMapper checkinMapper;
    private final UserMapper userMapper;
    private final LogMapper logMapper;
    private final OptionService optionService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** options 表中签到配置的 key*/
    private static final String OPTION_KEY = "checkin_setting";
    /** 系统日志类型*/
    private static final int LOG_TYPE_SYSTEM = 4;

    // ======================== 设置访问 ========================

    /**
     * 获取签到设置。      * <p>
     * 从 options 表 key="checkin_setting" 读取 JSON，解析失败或不存在时返回默认值（功能关闭）。
     */
    public CheckinSettingConfig getSetting() {
        String json = optionService.getValue(OPTION_KEY);
        if (json == null || json.isBlank()) {
            return new CheckinSettingConfig();
        }
        try {
            return Convert.toJavaBean(json, CheckinSettingConfig.class);
        } catch (Exception e) {
            log.warn("解析签到配置失败，使用默认值: {}", e.getMessage());
            return new CheckinSettingConfig();
        }
    }

    // ======================== 查询 ========================

    /**
     * 获取用户签到统计      *
     * @param userId 用户ID
     * @param month  月份（yyyy-MM）
     */
    public Map<String, Object> getUserCheckinStats(int userId, String month) {
        String startDate = month + "-01";
        String endDate = month + "-31";

        LambdaQueryWrapper<Checkin> wrapper = new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId, userId)
                .ge(Checkin::getCheckinDate, startDate)
                .le(Checkin::getCheckinDate, endDate)
                .orderByDesc(Checkin::getCheckinDate);

        List<Checkin> records = checkinMapper.selectList(wrapper);

        // 转换为不包含敏感字段的记录列表
        List<Map<String, Object>> recordList = records.stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("checkin_date", r.getCheckinDate());
                    item.put("quota_awarded", r.getQuotaAwarded());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("records", recordList);
        stats.put("total_checkins", records.size());
        stats.put("total_quota", records.stream().mapToInt(Checkin::getQuotaAwarded).sum());

        return stats;
    }

    // ======================== 签到执行 ========================

    /**
     * 检查用户今天是否已签到      */
    private boolean hasCheckedInToday(int userId) {
        String today = LocalDate.now().format(DATE_FMT);
        Long count = checkinMapper.selectCount(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getUserId, userId)
                        .eq(Checkin::getCheckinDate, today));
        return count != null && count > 0;
    }

    /**
     * 执行签到      * <p>
     * 使用事务保证原子性：创建签到记录 + 增加用户额度
     *
     * @param userId 用户ID
     * @return 签到记录（quota_awarded / checkin_date）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> doCheckin(int userId) {
        CheckinSettingConfig setting = getSetting();
        if (!setting.isEnabled()) {
            throw new RuntimeException(I18nUtils.get("checkin.disabled"));
        }

        // 检查今日是否已签到
        if (hasCheckedInToday(userId)) {
            throw new RuntimeException(I18nUtils.get("checkin.already_today"));
        }

        // 计算随机额度奖励，对应 Go：MinQuota + rand(MaxQuota - MinQuota + 1)
        int quotaAwarded = setting.getMinQuota();
        if (setting.getMaxQuota() > setting.getMinQuota()) {
            quotaAwarded = setting.getMinQuota()
                    + ThreadLocalRandom.current().nextInt(setting.getMaxQuota() - setting.getMinQuota() + 1);
        }
        String today = LocalDate.now().format(DATE_FMT);

        // 步骤1：创建签到记录
        Checkin checkin = new Checkin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setQuotaAwarded(quotaAwarded);
        checkin.setCreatedAt(System.currentTimeMillis() / 1000);
        int inserted = checkinMapper.insert(checkin);
        if (inserted <= 0) {
            throw new RuntimeException(I18nUtils.get("checkin.failed"));
        }

        // 步骤2：在事务中增加用户额度
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException(I18nUtils.get("checkin.user_not_exists"));
        }
        user.setQuota(user.getQuota() + quotaAwarded);
        userMapper.updateById(user);

        // 记录系统日志
        Log logEntry = new Log();
        logEntry.setUserId(userId);
        logEntry.setType(LOG_TYPE_SYSTEM);
        logEntry.setContent("用户签到，获得额度 " + quotaAwarded);
        logEntry.setCreatedAt(System.currentTimeMillis() / 1000);
        logMapper.insert(logEntry);

        Map<String, Object> result = new HashMap<>();
        result.put("quota_awarded", quotaAwarded);
        result.put("checkin_date", today);
        return result;
    }
}
