package yaoshu.token.relay;

import yaoshu.token.spi.ChannelModelSyncHandler;

import java.util.List;

/**
 * 渠道模型同步处理器默认实现 — 空实现。
 * <p>
 * 可通过 SPI 扩展点覆盖此 Bean 实现自动同步策略（如商业版渠道成本自动补齐）。
 */
public class NoOpChannelModelSyncHandler implements ChannelModelSyncHandler {

    @Override
    public void onChannelModelsApplied(Integer channelId, List<String> modelNames) {
        // NoOp
    }
}
