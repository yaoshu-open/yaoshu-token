-- 控制台扩展内容默认引导数据（apiInfo + faq）
-- 来源：工单 [协作请求→yaoshu-token-后端] 控制台API地址与Passkey状态
-- 策略：INSERT IGNORE 保证幂等——已存在的 key 保留管理员配置不覆盖
-- 管理员可通过 系统设置 > 内容 在前端覆盖这些默认值
-- 数据结构对应前端 ApiInfoPanel（ApiInfoItem: route/url/description）与 FaqPanel（FaqItem: id/question/answer）

-- API 信息面板默认端点说明（相对路径，前端拼接 Base URL 展示）
INSERT IGNORE INTO `options` (`key`, `value`) VALUES
('console_setting.api_info', '[{"route":"Chat","url":"/v1/chat/completions","description":"对话补全接口（OpenAI 兼容）"},{"route":"Models","url":"/v1/models","description":"模型列表接口（OpenAI 兼容）"},{"route":"Embeddings","url":"/v1/embeddings","description":"文本向量化接口（OpenAI 兼容）"}]');

-- FAQ 面板默认引导（常见问题）
INSERT IGNORE INTO `options` (`key`, `value`) VALUES
('console_setting.faq', '[{"id":1,"question":"如何获取 API Key？","answer":"登录后前往「令牌」页面，点击「添加令牌」创建一个新的 API Key，复制后即可用于调用 API。"},{"id":2,"question":"支持哪些模型？","answer":"前往「定价」页面查看当前可用的模型列表与计费倍率。模型可用性取决于管理员配置的渠道。"},{"id":3,"question":"如何充值余额？","answer":"前往「钱包」页面，选择充值金额并完成支付。支持的支付方式取决于管理员配置。"}]');
