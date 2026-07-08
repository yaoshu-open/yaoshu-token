package yaoshu.token.config.operation;

/**
 * 渠道亲和性设置 POJO  * <p>
 * 被 ChannelAffinityService 消费。完整类型定义在 ChannelAffinityService 中已内嵌，
 * 此处提供独立 POJO 以满足模块间引用需要。
 */
public final class ChannelAffinitySettingConfig {

    private ChannelAffinitySettingConfig() {
    }

    // 由 ChannelAffinityService.ChannelAffinitySetting 承载完整类型定义
    // 此文件作为配置模块的独立入口点存在，避免循环依赖
}
