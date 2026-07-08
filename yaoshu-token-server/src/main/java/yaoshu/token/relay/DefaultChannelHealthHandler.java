package yaoshu.token.relay;

import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.ChannelManagementService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.spi.ChannelHealthHandler;

/**
 * 渠道健康处理默认实现 — 与 Go {@code processChannelError()} 等价（自动禁用渠道 + 错误日志落库）。
 * <p>
 * 可通过 SPI 扩展点覆盖此 Bean 实现自定义健康处理（如渐变恢复、健康评分）。
 */
public class DefaultChannelHealthHandler implements ChannelHealthHandler {

    private final ChannelService channelService;
    private final ChannelManagementService channelManagementService;

    public DefaultChannelHealthHandler(ChannelService channelService,
                                       ChannelManagementService channelManagementService) {
        this.channelService = channelService;
        this.channelManagementService = channelManagementService;
    }

    @Override
    public void onChannelError(RelayInfo info, Exception error) {
        if (!(error instanceof RelayException apiError)) {
            return;
        }
        // 自动禁用失败渠道
        if (info.getChannelId() > 0) {
            var channel = channelService.getById(info.getChannelId());
            if (channel != null) {
                channelManagementService.disableChannelForRelayError(channel, apiError);
            }
        }
    }
}
