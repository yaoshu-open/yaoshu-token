-- ============================================================================
-- V2: 订阅预消费记录表补全字段（Session-16）
-- 对齐 Go model/subscription.go SubscriptionPreConsumeRecord 结构
-- ============================================================================

-- 1) 补全 pre_consumed / status / updated_at 字段
ALTER TABLE `subscription_pre_consume_records`
    ADD COLUMN `pre_consumed` BIGINT NOT NULL DEFAULT 0 AFTER `user_subscription_id`,
    ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'consumed' AFTER `pre_consumed`,
    ADD COLUMN `updated_at` BIGINT NOT NULL DEFAULT 0 AFTER `created_at`;

-- 2) request_id 添加唯一索引（幂等键）
ALTER TABLE `subscription_pre_consume_records`
    ADD UNIQUE INDEX `uk_spr_request_id` (`request_id`);

-- 3) status 添加普通索引（查询 refunded/consumed 状态）
ALTER TABLE `subscription_pre_consume_records`
    ADD INDEX `idx_spr_status` (`status`);

-- 4) updated_at 添加索引（清理旧记录用）
ALTER TABLE `subscription_pre_consume_records`
    ADD INDEX `idx_spr_updated_at` (`updated_at`);
