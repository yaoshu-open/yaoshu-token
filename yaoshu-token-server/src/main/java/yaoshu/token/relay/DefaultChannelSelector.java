package yaoshu.token.relay;

import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.spi.ChannelSelector;

import java.util.List;

/**
 * 渠道选择默认实现 — 候选列表由开源侧完成加权初筛后传入，默认取第一个。
 * <p>
 * 可通过 SPI 扩展点覆盖此 Bean 实现自定义选择策略（如最低延迟优先、加权轮询）。
 */
public class DefaultChannelSelector implements ChannelSelector {

    @Override
    public Channel select(List<Channel> candidates, RelayInfo info) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // 候选列表已由 ChannelService 完成加权优先级排序，直接取第一个
        return candidates.get(0);
    }
}
