-- V7: 用户表 quota/used_quota 列 INT→BIGINT
-- 原因：Go int 在 64 位系统为 64 位，MySQL INT(32位) 存在溢出风险
-- 影响：users.quota + users.used_quota
ALTER TABLE `users` MODIFY COLUMN `quota` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `users` MODIFY COLUMN `used_quota` BIGINT NOT NULL DEFAULT 0;
