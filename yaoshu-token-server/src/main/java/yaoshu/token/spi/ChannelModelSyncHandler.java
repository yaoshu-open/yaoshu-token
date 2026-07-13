package yaoshu.token.spi;

import java.util.List;

/**
 * 渠道模型同步处理器 — 渠道模型变更后通知下游模块的钩子。
 * <p>
 * 默认实现为 NoOp（不执行任何操作）。商业版可通过 SPI 扩展点覆盖此 Bean，
 * 在渠道模型应用上游变更后自动同步至渠道成本表，消除运营手动重复录入。
 */
public interface ChannelModelSyncHandler {

    /**
     * 渠道模型变更后回调（abilities 重建后触发）。
     *
     * @param channelId  渠道 ID
     * @param modelNames 该渠道当前全部模型名列表
     */
    void onChannelModelsApplied(Integer channelId, List<String> modelNames);
}
