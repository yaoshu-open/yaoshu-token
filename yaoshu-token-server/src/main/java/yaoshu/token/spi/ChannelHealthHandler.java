package yaoshu.token.spi;

import yaoshu.token.relay.common.RelayInfo;

/**
 * 渠道健康处理 — 渠道级错误发生后的处理回调。
 * 开源默认实现与 Go {@code processChannelError()} 等价（自动禁用渠道 + 错误日志落库）。
 * 调用方已保证在事务内执行。
 */
public interface ChannelHealthHandler {

    /** 渠道错误后处理（已在事务内），可操作 DB */
    void onChannelError(RelayInfo info, Exception error);
}
