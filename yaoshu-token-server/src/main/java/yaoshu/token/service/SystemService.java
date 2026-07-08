package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.mapper.MidjourneyMapper;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.mapper.UserMapper;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 系统服务  * <p>
 * 系统配置从 OptionService（options 表）读取，布尔配置存储为 "true"/"false" 字符串。
 * LegalSettings（用户协议/隐私政策）以 JSON 存储在 options 表的 "legal" key 下。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;
    private final TokenMapper tokenMapper;
    private final MidjourneyMapper midjourneyMapper;
    private final OptionService optionService;

    private final LocalDateTime startTime = LocalDateTime.now();

    public LocalDateTime getStartTime() { return startTime; }
    public long getUptime() { return Duration.between(startTime, LocalDateTime.now()).getSeconds(); }

    public long getChannelCount() { return channelMapper.selectCount(null); }
    public long getUserCount() { return userMapper.selectCount(null); }
    public long getTokenCount() { return tokenMapper.selectCount(null); }
    public long getMidjourneyCount() { return midjourneyMapper.selectCount(null); }

    /**
     * 邮箱验证是否启用      * <p>
     * options key: "EmailVerificationEnabled"
     */
    public boolean isEmailEnabled() {
        return getBoolOption("EmailVerificationEnabled");
    }

    /**
     * Midjourney 转发 URL 是否启用      * <p>
     * options key: "MjForwardUrlEnabled"
     */
    public boolean isMjForwardEnabled() {
        return getBoolOption("MjForwardUrlEnabled");
    }

    /**
     * GitHub OAuth 是否启用      * <p>
     * options key: "GitHubOAuthEnabled"
     */
    public boolean isGithubOAuthEnabled() {
        return getBoolOption("GitHubOAuthEnabled");
    }

    /**
     * 获取用户协议      * <p>
     * options key: "legal"，value 为 JSON {"user_agreement":"...","privacy_policy":"..."}
     */
    public String getUserAgreement() {
        return getLegalField("user_agreement");
    }

    /**
     * 获取隐私政策      */
    public String getPrivacyPolicy() {
        return getLegalField("privacy_policy");
    }

    /**
     * 从 OptionMap 内存缓存读取选项值      * <p>
     * 使用读锁保护，与 Go OptionMapRWMutex.RLock() 一致。
     */
    public String getOptionValue(String key) {
        yaoshu.token.constant.CommonConstants.optionMapLock.readLock().lock();
        try {
            if (yaoshu.token.constant.CommonConstants.optionMap == null) {
                return null;
            }
            return yaoshu.token.constant.CommonConstants.optionMap.get(key);
        } finally {
            yaoshu.token.constant.CommonConstants.optionMapLock.readLock().unlock();
        }
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 从 options 表读取布尔配置，默认 false
     */
    private boolean getBoolOption(String key) {
        String value = optionService.getValue(key);
        return "true".equalsIgnoreCase(value);
    }

    /**
     * 从 options 表的 "legal" JSON 配置中提取指定字段
     *
     * @param fieldName JSON 字段名（user_agreement / privacy_policy）
     */
    private String getLegalField(String fieldName) {
        String legalJson = optionService.getValue("legal");
        if (legalJson == null || legalJson.isEmpty()) {
            return "";
        }
        try {
            com.alibaba.fastjson2.JSONObject obj = ai.yue.library.base.convert.Convert.toJSONObject(legalJson);
            String value = obj.getString(fieldName);
            return value != null ? value : "";
        } catch (Exception e) {
            log.warn("解析 legal 配置失败: {}", e.getMessage());
            return "";
        }
    }
}
