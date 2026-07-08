-- 模型最大上下文窗口（token 数）
-- 可空：null = 未设置（前端降级为仅显示已用 tokens，不显示占比）
-- 数据初始化策略：留空，由运营通过后台模型管理按需填写
ALTER TABLE `models`
    ADD COLUMN `max_context` INT DEFAULT NULL COMMENT '最大上下文窗口token数' AFTER `name_rule`;
