-- 用户订阅新增自动续期标志
-- 订阅到期时由 SubscriptionResetTaskService 判定：auto_renew=1 且套餐启用且余额充足 → 自动扣费续期；否则标记 expired
ALTER TABLE user_subscriptions
    ADD COLUMN auto_renew TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否自动续期：1=自动续期，0=已关闭续期（到期不续）';
