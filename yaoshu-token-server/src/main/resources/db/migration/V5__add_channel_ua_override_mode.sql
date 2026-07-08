-- V5: 渠道表新增 UA 覆盖模式字段
-- 用途：支持 SPI 扩展点 (RelayRequestInterceptor) 根据渠道级配置定制 User-Agent 行为
-- 枚举值：AUTO（默认，由 SPI 实现判定）/ FORCE_IDE（强制 IDE 类 UA）/ OFF（跳过替换）
-- 此字段为中性通用字段，开源默认实现 (NoOpRelayRequestInterceptor) 不消费，自定义 SPI 实现可消费

ALTER TABLE `channels`
    ADD COLUMN `ua_override_mode` VARCHAR(20) NOT NULL DEFAULT 'AUTO' AFTER `header_override`;
