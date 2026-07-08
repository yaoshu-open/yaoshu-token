-- tokens 表 quota 列 INT→BIGINT
-- 原因：与 users 表（V7 已升级）对齐，消除单 Token 累计消费溢出风险
-- 溢出阈值（INT 上限）：2,147,483,647 quota ≈ $4294（按 quotaPerUnit=500000）
-- 影响列：tokens.used_quota + tokens.remain_quota
-- 类型变更 MySQL MODIFY COLUMN 扩大类型是非破坏性变更，不丢数据
ALTER TABLE `tokens` MODIFY COLUMN `used_quota` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `tokens` MODIFY COLUMN `remain_quota` BIGINT NOT NULL DEFAULT 0;
