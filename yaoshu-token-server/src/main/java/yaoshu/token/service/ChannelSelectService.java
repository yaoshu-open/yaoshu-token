package yaoshu.token.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.pojo.entity.Channel;

import java.util.List;

/**
 * 渠道选择服务（带重试与自动分组）  * <p>
 * 核心方法 {@link #cacheGetRandomSatisfiedChannel(RetryParam)}：
 * <ul>
 *   <li>非 auto 分组：直接委托 {@link ChannelService#getRandomSatisfiedChannel(String, String, int)}</li>
 *   <li>auto 分组：按用户分组列表依次遍历，每个分组用完所有优先级后切换下一分组</li>
 * </ul>
 * <p>
 * 依赖：ChannelService（渠道缓存与加权选择）、GroupService（用户自动分组）。
 * ChannelService 由 service/channel.go 翻译提供，GroupService 由 service/group.go 翻译提供。
 */
@Slf4j
public final class ChannelSelectService {

    private ChannelSelectService() {
    }

    /**
     * 尝试获取一个满足要求的渠道（优先选择未使用的）      * <p>
     * auto 分组流程示例（2 个分组，每分组 2 个优先级，RetryTimes=3）：
     * <pre>
     * Retry=0: GroupA, priority0
     * Retry=1: GroupA, priority1
     * Retry=2: GroupA 用完 → GroupB, priority0 (SetRetry(0) + ResetRetryNextTry)
     * Retry=3: GroupB, priority1
     * </pre>
     *
     * @param param 重试参数（含 tokenGroup、modelName、retry 计数器）
     * @return [渠道对象, 实际使用的分组名称]；渠道为 null 表示未找到
     */
    public static Object[] cacheGetRandomSatisfiedChannel(RetryParam param, ChannelService channelService) {
        String selectGroup = param.getTokenGroup();
        HttpServletRequest request = param.getRequest();
        String userGroup = getContextString(request, ContextKeyConstants.USER_GROUP);

        if ("auto".equals(param.getTokenGroup())) {
            // auto 分组模式：按用户分组列表依次遍历
            List<String> autoGroups = getAutoGroups();
            if (autoGroups == null || autoGroups.isEmpty()) {
                return new Object[]{null, selectGroup};
            }

            List<String> userAutoGroups = getUserAutoGroup(userGroup);

            // 从上下文恢复分组索引（上次重试所在的分组位置）
            int startGroupIndex = getContextInt(request, ContextKeyConstants.AUTO_GROUP_INDEX);
            boolean crossGroupRetry = getContextBool(request, ContextKeyConstants.TOKEN_CROSS_GROUP_RETRY);

            for (int i = startGroupIndex; i < userAutoGroups.size(); i++) {
                String autoGroup = userAutoGroups.get(i);

                // 计算当前分组内的 priorityRetry
                int priorityRetry = param.getRetry();
                if (i > startGroupIndex) {
                    // 切换到新分组，重置 priorityRetry
                    priorityRetry = 0;
                }
                log.debug("Auto selecting group: {}, priorityRetry: {}", autoGroup, priorityRetry);

                Channel channel = channelService.getRandomSatisfiedChannel(autoGroup, param.getModelName(), priorityRetry);
                if (channel == null) {
                    // 当前分组没有该模型的可用渠道，尝试下一个分组
                    log.debug("No available channel in group {} for model {} at priorityRetry {}, trying next group",
                            autoGroup, param.getModelName(), priorityRetry);
                    setContextInt(request, ContextKeyConstants.AUTO_GROUP_INDEX, i + 1);
                    setContextInt(request, ContextKeyConstants.AUTO_GROUP_RETRY_INDEX, 0);
                    param.setRetry(0);
                    continue;
                }

                // 找到渠道，记录当前分组信息
                setContextString(request, ContextKeyConstants.AUTO_GROUP, autoGroup);
                selectGroup = autoGroup;
                log.debug("Auto selected group: {}", autoGroup);

                // 为下一次重试准备状态
                if (crossGroupRetry && priorityRetry >= CommonConstants.retryTimes) {
                    // 当前分组已用完所有重试次数，准备切换到下一分组
                    log.debug("Current group {} retries exhausted (priorityRetry={} >= RetryTimes={}), preparing switch to next group",
                            autoGroup, priorityRetry, CommonConstants.retryTimes);
                    setContextInt(request, ContextKeyConstants.AUTO_GROUP_INDEX, i + 1);
                    param.setRetry(0);
                    param.resetRetryNextTry();
                } else {
                    // 保持在当前分组
                    setContextInt(request, ContextKeyConstants.AUTO_GROUP_INDEX, i);
                }
                break;
            }
        } else {
            // 非 auto 分组：直接委托
            Channel channel = channelService.getRandomSatisfiedChannel(
                    param.getTokenGroup(), param.getModelName(), param.getRetry());
            if (channel == null) {
                return new Object[]{null, param.getTokenGroup()};
            }
            return new Object[]{channel, selectGroup};
        }

        // 此时 channel 可能为 null（auto 分组全部遍历完仍无渠道）
        return new Object[]{null, selectGroup};
    }

    // ======================== 上下文辅助方法（stub） ========================

    /**
     * 从 HttpServletRequest attribute 获取字符串值
     */
    private static String getContextString(HttpServletRequest request, String key) {
        if (request == null || key == null) {
            return "";
        }
        Object value = request.getAttribute(key);
        return value != null ? value.toString() : "";
    }

    /**
     * 从 HttpServletRequest attribute 获取 int 值
     */
    private static int getContextInt(HttpServletRequest request, String key) {
        if (request == null || key == null) {
            return 0;
        }
        Object value = request.getAttribute(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return 0;
    }

    /**
     * 从 HttpServletRequest attribute 获取 boolean 值
     */
    private static boolean getContextBool(HttpServletRequest request, String key) {
        if (request == null || key == null) {
            return false;
        }
        Object value = request.getAttribute(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * 设置 HttpServletRequest attribute（整数）
     */
    private static void setContextInt(HttpServletRequest request, String key, int value) {
        if (request != null) {
            request.setAttribute(key, value);
        }
    }

    /**
     * 设置 HttpServletRequest attribute（字符串）
     */
    private static void setContextString(HttpServletRequest request, String key, String value) {
        if (request != null) {
            request.setAttribute(key, value);
        }
    }

    // ======================== 分组依赖项（委托 GroupService / AutoGroupConfig） ========================

    /**
     * 获取用户自动分组列表。      * <p>
     * 委托 {@link GroupService#getUserAutoGroup(String)}：用户可用分组 ∩ 全局自动分组。
     */
    private static List<String> getUserAutoGroup(String userGroup) {
        return GroupService.getUserAutoGroup(userGroup);
    }

    /**
     * 获取全局自动分组列表。      * <p>
     * 委托 {@link yaoshu.token.config.AutoGroupConfig#getAutoGroups()}。
     */
    private static List<String> getAutoGroups() {
        return yaoshu.token.config.AutoGroupConfig.getAutoGroups();
    }
}
