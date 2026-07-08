package yaoshu.token.spi;

import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;

/**
 * 渠道选择 — 从候选列表中选择一个渠道。
 * 默认实现与 Go {@code getRandomSatisfiedChannel()} + auto 分组迭代等价。
 * 候选列表由开源侧完成加权初筛后传入，SPI 扩展只需「从哪个里挑」——避免重复实现加权逻辑。
 */
public interface ChannelSelector {

    /** @return 选中的渠道，null 表示无可用渠道 */
    Channel select(List<Channel> candidates, RelayInfo info);
}
