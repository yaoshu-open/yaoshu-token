-- V6: 补充 subscription_plans 表遗漏的列
-- Go 原版 model/main.go:407-408 和 model/subscription.go:178-182 均定义了这些列，
-- V1__baseline.sql 翻译时遗漏。字段含义：
--   quota_reset_period         订阅配额重置周期（never/daily/weekly/monthly/custom）
--   quota_reset_custom_seconds  自定义重置周期秒数（quota_reset_period=custom 时生效）
--   created_at                 创建时间（时间戳）
--   updated_at                 更新时间（时间戳）

ALTER TABLE `subscription_plans`
    ADD COLUMN `quota_reset_period` varchar(16) DEFAULT 'never',
    ADD COLUMN `quota_reset_custom_seconds` bigint DEFAULT 0,
    ADD COLUMN `created_at` bigint DEFAULT 0,
    ADD COLUMN `updated_at` bigint DEFAULT 0;
