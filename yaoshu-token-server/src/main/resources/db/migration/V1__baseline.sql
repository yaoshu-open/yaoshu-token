-- ============================================================================
-- V1__baseline.sql — 初始基线
-- 从 Go model/*.go 的 GORM 结构体反推完整 DDL（26 张表）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. users（用户表）
-- Go: model/user.go User
-- ----------------------------------------------------------------------------
CREATE TABLE `users` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(20)  NOT NULL,
    `password`        VARCHAR(255) NOT NULL,
    `display_name`    VARCHAR(20)  DEFAULT NULL,
    `role`            INT          NOT NULL DEFAULT 1,
    `status`          INT          NOT NULL DEFAULT 1,
    `email`           VARCHAR(50)  DEFAULT NULL,
    `github_id`       VARCHAR(128) DEFAULT NULL,
    `discord_id`      VARCHAR(128) DEFAULT NULL,
    `oidc_id`         VARCHAR(128) DEFAULT NULL,
    `wechat_id`       VARCHAR(128) DEFAULT NULL,
    `telegram_id`     VARCHAR(128) DEFAULT NULL,
    `access_token`    CHAR(32)     DEFAULT NULL,
    `quota`           INT          NOT NULL DEFAULT 0,
    `used_quota`      INT          NOT NULL DEFAULT 0,
    `request_count`   INT          NOT NULL DEFAULT 0,
    `group`           VARCHAR(64)  NOT NULL DEFAULT 'default',
    `aff_code`        VARCHAR(32)  DEFAULT NULL,
    `aff_count`       INT          NOT NULL DEFAULT 0,
    `aff_quota`       INT          NOT NULL DEFAULT 0,
    `aff_history`     INT          NOT NULL DEFAULT 0,
    `inviter_id`      INT          DEFAULT NULL,
    `linux_do_id`     VARCHAR(128) DEFAULT NULL,
    `setting`         TEXT         DEFAULT NULL,
    `remark`          VARCHAR(255) DEFAULT NULL,
    `stripe_customer` VARCHAR(64)  DEFAULT NULL,
    `created_at`      BIGINT       NOT NULL DEFAULT 0,
    `last_login_at`   BIGINT       NOT NULL DEFAULT 0,
    `deleted_at`      DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_users_username` (`username`),
    UNIQUE INDEX `idx_users_access_token` (`access_token`),
    UNIQUE INDEX `idx_users_aff_code` (`aff_code`),
    INDEX `idx_users_display_name` (`display_name`),
    INDEX `idx_users_email` (`email`),
    INDEX `idx_users_github_id` (`github_id`),
    INDEX `idx_users_discord_id` (`discord_id`),
    INDEX `idx_users_oidc_id` (`oidc_id`),
    INDEX `idx_users_wechat_id` (`wechat_id`),
    INDEX `idx_users_telegram_id` (`telegram_id`),
    INDEX `idx_users_inviter_id` (`inviter_id`),
    INDEX `idx_users_linux_do_id` (`linux_do_id`),
    INDEX `idx_users_stripe_customer` (`stripe_customer`),
    INDEX `idx_users_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 2. tokens（令牌表）
-- Go: model/token.go Token
-- ----------------------------------------------------------------------------
CREATE TABLE `tokens` (
    `id`                   INT          NOT NULL AUTO_INCREMENT,
    `user_id`              INT          NOT NULL,
    `key`                  VARCHAR(128) NOT NULL,
    `status`               INT          NOT NULL DEFAULT 1,
    `name`                 VARCHAR(128) DEFAULT NULL,
    `created_time`         BIGINT       NOT NULL DEFAULT 0,
    `accessed_time`        BIGINT       NOT NULL DEFAULT 0,
    `expired_time`         BIGINT       NOT NULL DEFAULT -1,
    `remain_quota`         INT          NOT NULL DEFAULT 0,
    `unlimited_quota`      TINYINT(1)   NOT NULL DEFAULT 0,
    `model_limits_enabled` TINYINT(1)   NOT NULL DEFAULT 0,
    `model_limits`         TEXT         DEFAULT NULL,
    `allow_ips`            VARCHAR(255) DEFAULT '',
    `used_quota`           INT          NOT NULL DEFAULT 0,
    `group`                VARCHAR(64)  NOT NULL DEFAULT '',
    `cross_group_retry`    TINYINT(1)   NOT NULL DEFAULT 0,
    `deleted_at`           DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_tokens_key` (`key`),
    INDEX `idx_tokens_user_id` (`user_id`),
    INDEX `idx_tokens_name` (`name`),
    INDEX `idx_tokens_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 3. channels（渠道表）
-- Go: model/channel.go Channel
-- ----------------------------------------------------------------------------
CREATE TABLE `channels` (
    `id`                   INT           NOT NULL AUTO_INCREMENT,
    `type`                 INT           NOT NULL DEFAULT 0,
    `key`                  TEXT          NOT NULL,
    `openai_organization`  VARCHAR(255)  DEFAULT NULL,
    `test_model`           VARCHAR(128)  DEFAULT NULL,
    `status`               INT           NOT NULL DEFAULT 1,
    `name`                 VARCHAR(128)  NOT NULL DEFAULT '',
    `weight`               INT UNSIGNED  DEFAULT 0,
    `created_time`         BIGINT        NOT NULL DEFAULT 0,
    `test_time`            BIGINT        NOT NULL DEFAULT 0,
    `response_time`        INT           NOT NULL DEFAULT 0,
    `base_url`             VARCHAR(512)  DEFAULT '',
    `other`                TEXT          DEFAULT NULL,
    `balance`              DOUBLE        NOT NULL DEFAULT 0,
    `balance_updated_time` BIGINT        NOT NULL DEFAULT 0,
    `models`               TEXT          DEFAULT NULL,
    `group`                VARCHAR(64)   NOT NULL DEFAULT 'default',
    `used_quota`           BIGINT        NOT NULL DEFAULT 0,
    `model_mapping`        TEXT          DEFAULT NULL,
    `status_code_mapping`  VARCHAR(1024) DEFAULT '',
    `priority`             BIGINT        DEFAULT 0,
    `auto_ban`             INT           DEFAULT 1,
    `other_info`           TEXT          DEFAULT NULL,
    `tag`                  VARCHAR(128)  DEFAULT NULL,
    `setting`              TEXT          DEFAULT NULL,
    `param_override`       TEXT          DEFAULT NULL,
    `header_override`      TEXT          DEFAULT NULL,
    `remark`               VARCHAR(255)  DEFAULT NULL,
    `channel_info`         JSON          DEFAULT NULL,
    `settings`             TEXT          DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_channels_name` (`name`),
    INDEX `idx_channels_tag` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 4. options（系统配置表）
-- Go: model/option.go Option
-- ----------------------------------------------------------------------------
CREATE TABLE `options` (
    `key`   VARCHAR(128) NOT NULL,
    `value` TEXT         DEFAULT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 5. logs（日志表）
-- Go: model/log.go Log
-- ----------------------------------------------------------------------------
CREATE TABLE `logs` (
    `id`                   INT          NOT NULL AUTO_INCREMENT,
    `user_id`              INT          NOT NULL,
    `created_at`           BIGINT       NOT NULL,
    `type`                 INT          NOT NULL DEFAULT 0,
    `content`              TEXT         DEFAULT NULL,
    `username`             VARCHAR(64)  NOT NULL DEFAULT '',
    `token_name`           VARCHAR(128) NOT NULL DEFAULT '',
    `channel_id`           INT          DEFAULT NULL,
    `model_name`           VARCHAR(128) NOT NULL DEFAULT '',
    `quota`                INT          NOT NULL DEFAULT 0,
    `prompt_tokens`        INT          NOT NULL DEFAULT 0,
    `completion_tokens`    INT          NOT NULL DEFAULT 0,
    `cached_tokens`        INT          NOT NULL DEFAULT 0,
    `use_time`             INT          NOT NULL DEFAULT 0,
    `is_stream`            TINYINT(1)   NOT NULL DEFAULT 0,
    `channel`              INT          DEFAULT NULL,
    `channel_name`         VARCHAR(128) DEFAULT NULL,
    `token_id`             INT          NOT NULL DEFAULT 0,
    `group`                VARCHAR(64)  DEFAULT NULL,
    `ip`                   VARCHAR(64)  NOT NULL DEFAULT '',
    `request_id`           VARCHAR(64)  NOT NULL DEFAULT '',
    `upstream_request_id`  VARCHAR(128) NOT NULL DEFAULT '',
    `key_index`            INT          DEFAULT NULL,
    `other`                TEXT         DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_logs_user_id_id` (`user_id`, `id`),
    INDEX `idx_logs_created_at_id` (`created_at`, `id`),
    INDEX `idx_logs_created_at_type` (`created_at`, `type`),
    INDEX `idx_logs_username` (`username`),
    INDEX `idx_logs_token_name` (`token_name`),
    INDEX `idx_logs_model_name` (`model_name`),
    INDEX `idx_logs_idx_username_model_name` (`username`, `model_name`),
    INDEX `idx_logs_channel` (`channel`),
    INDEX `idx_logs_token_id` (`token_id`),
    INDEX `idx_logs_group` (`group`),
    INDEX `idx_logs_ip` (`ip`),
    INDEX `idx_logs_request_id` (`request_id`),
    INDEX `idx_logs_upstream_request_id` (`upstream_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 6. abilities（渠道能力表）
-- Go: model/ability.go Ability
-- ----------------------------------------------------------------------------
CREATE TABLE `abilities` (
    `group`      VARCHAR(64)  NOT NULL,
    `model`      VARCHAR(255) NOT NULL,
    `channel_id` INT          NOT NULL,
    `enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
    `priority`   BIGINT       DEFAULT 0,
    `weight`     INT UNSIGNED NOT NULL DEFAULT 0,
    `tag`        VARCHAR(128) DEFAULT NULL,
    PRIMARY KEY (`group`, `model`, `channel_id`),
    INDEX `idx_abilities_channel_id` (`channel_id`),
    INDEX `idx_abilities_priority` (`priority`),
    INDEX `idx_abilities_weight` (`weight`),
    INDEX `idx_abilities_tag` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 7. redemptions（兑换码表）
-- Go: model/redemption.go Redemption
-- ----------------------------------------------------------------------------
CREATE TABLE `redemptions` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `user_id`       INT          NOT NULL DEFAULT 0,
    `key`           CHAR(32)     NOT NULL,
    `status`        INT          NOT NULL DEFAULT 1,
    `name`          VARCHAR(128) DEFAULT NULL,
    `quota`         INT          NOT NULL DEFAULT 100,
    `created_time`  BIGINT       NOT NULL DEFAULT 0,
    `redeemed_time` BIGINT       NOT NULL DEFAULT 0,
    `used_user_id`  INT          NOT NULL DEFAULT 0,
    `expired_time`  BIGINT       NOT NULL DEFAULT 0,
    `deleted_at`    DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_redemptions_key` (`key`),
    INDEX `idx_redemptions_name` (`name`),
    INDEX `idx_redemptions_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 8. top_ups（充值记录表）
-- Go: model/topup.go TopUp
-- ----------------------------------------------------------------------------
CREATE TABLE `top_ups` (
    `id`               INT          NOT NULL AUTO_INCREMENT,
    `user_id`          INT          NOT NULL,
    `amount`           BIGINT       NOT NULL DEFAULT 0,
    `money`            DOUBLE       NOT NULL DEFAULT 0,
    `trade_no`         VARCHAR(255) NOT NULL,
    `payment_method`   VARCHAR(50)  DEFAULT NULL,
    `payment_provider` VARCHAR(50)  NOT NULL DEFAULT '',
    `create_time`      BIGINT       NOT NULL DEFAULT 0,
    `complete_time`    BIGINT       NOT NULL DEFAULT 0,
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'pending',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_top_ups_trade_no` (`trade_no`),
    INDEX `idx_top_ups_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 9. models（模型元数据表）
-- Go: model/model_meta.go Model
-- ----------------------------------------------------------------------------
CREATE TABLE `models` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `model_name`    VARCHAR(128) NOT NULL,
    `description`   TEXT         DEFAULT NULL,
    `icon`          VARCHAR(128) DEFAULT NULL,
    `tags`          VARCHAR(255) DEFAULT NULL,
    `vendor_id`     INT          DEFAULT NULL,
    `endpoints`     TEXT         DEFAULT NULL,
    `status`        INT          NOT NULL DEFAULT 1,
    `sync_official` INT          NOT NULL DEFAULT 1,
    `created_time`  BIGINT       NOT NULL DEFAULT 0,
    `updated_time`  BIGINT       NOT NULL DEFAULT 0,
    `name_rule`     INT          NOT NULL DEFAULT 0,
    `deleted_at`    DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_model_name_delete_at` (`model_name`, `deleted_at`),
    INDEX `idx_models_vendor_id` (`vendor_id`),
    INDEX `idx_models_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 10. vendors（供应商表）
-- Go: model/vendor_meta.go Vendor
-- ----------------------------------------------------------------------------
CREATE TABLE `vendors` (
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(128) NOT NULL,
    `description`  TEXT         DEFAULT NULL,
    `icon`         VARCHAR(128) DEFAULT NULL,
    `status`       INT          NOT NULL DEFAULT 1,
    `created_time` BIGINT       NOT NULL DEFAULT 0,
    `updated_time` BIGINT       NOT NULL DEFAULT 0,
    `deleted_at`   DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_vendor_name_delete_at` (`name`, `deleted_at`),
    INDEX `idx_vendors_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 11. checkins（签到表）
-- Go: model/checkin.go Checkin
-- ----------------------------------------------------------------------------
CREATE TABLE `checkins` (
    `id`            INT         NOT NULL AUTO_INCREMENT,
    `user_id`       INT         NOT NULL,
    `checkin_date`  VARCHAR(10) NOT NULL,
    `quota_awarded` INT         NOT NULL DEFAULT 0,
    `created_at`    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_user_checkin_date` (`user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 12. midjourneys（Midjourney 任务表）
-- Go: model/midjourney.go Midjourney
-- ----------------------------------------------------------------------------
CREATE TABLE `midjourneys` (
    `id`          INT          NOT NULL AUTO_INCREMENT,
    `code`        INT          NOT NULL DEFAULT 0,
    `user_id`     INT          NOT NULL,
    `action`      VARCHAR(40)  DEFAULT NULL,
    `mj_id`       VARCHAR(128) DEFAULT NULL,
    `prompt`      TEXT         DEFAULT NULL,
    `prompt_en`   TEXT         DEFAULT NULL,
    `description` TEXT         DEFAULT NULL,
    `state`       VARCHAR(64)  DEFAULT NULL,
    `submit_time` BIGINT       NOT NULL DEFAULT 0,
    `start_time`  BIGINT       NOT NULL DEFAULT 0,
    `finish_time` BIGINT       NOT NULL DEFAULT 0,
    `image_url`   TEXT         DEFAULT NULL,
    `video_url`   TEXT         DEFAULT NULL,
    `video_urls`  TEXT         DEFAULT NULL,
    `status`      VARCHAR(20)  DEFAULT NULL,
    `progress`    VARCHAR(30)  DEFAULT NULL,
    `fail_reason` TEXT         DEFAULT NULL,
    `channel_id`  INT          NOT NULL DEFAULT 0,
    `quota`       INT          NOT NULL DEFAULT 0,
    `buttons`     TEXT         DEFAULT NULL,
    `properties`  TEXT         DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_mj_user_id` (`user_id`),
    INDEX `idx_mj_action` (`action`),
    INDEX `idx_mj_mj_id` (`mj_id`),
    INDEX `idx_mj_submit_time` (`submit_time`),
    INDEX `idx_mj_start_time` (`start_time`),
    INDEX `idx_mj_finish_time` (`finish_time`),
    INDEX `idx_mj_status` (`status`),
    INDEX `idx_mj_progress` (`progress`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 13. tasks（视频/音频任务表）
-- Go: model/task.go Task
-- ----------------------------------------------------------------------------
CREATE TABLE `tasks` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`   BIGINT       NOT NULL DEFAULT 0,
    `updated_at`   BIGINT       NOT NULL DEFAULT 0,
    `task_id`      VARCHAR(191) DEFAULT NULL,
    `platform`     VARCHAR(30)  DEFAULT NULL,
    `user_id`      INT          NOT NULL DEFAULT 0,
    `group`        VARCHAR(50)  DEFAULT NULL,
    `channel_id`   INT          NOT NULL DEFAULT 0,
    `quota`        INT          NOT NULL DEFAULT 0,
    `action`       VARCHAR(40)  DEFAULT NULL,
    `status`       VARCHAR(20)  DEFAULT NULL,
    `fail_reason`  TEXT         DEFAULT NULL,
    `submit_time`  BIGINT       NOT NULL DEFAULT 0,
    `start_time`   BIGINT       NOT NULL DEFAULT 0,
    `finish_time`  BIGINT       NOT NULL DEFAULT 0,
    `progress`     VARCHAR(20)  DEFAULT NULL,
    `properties`   JSON         DEFAULT NULL,
    `private_data` JSON         DEFAULT NULL,
    `data`         JSON         DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_tasks_created_at` (`created_at`),
    INDEX `idx_tasks_task_id` (`task_id`),
    INDEX `idx_tasks_platform` (`platform`),
    INDEX `idx_tasks_user_id` (`user_id`),
    INDEX `idx_tasks_channel_id` (`channel_id`),
    INDEX `idx_tasks_action` (`action`),
    INDEX `idx_tasks_status` (`status`),
    INDEX `idx_tasks_submit_time` (`submit_time`),
    INDEX `idx_tasks_start_time` (`start_time`),
    INDEX `idx_tasks_finish_time` (`finish_time`),
    INDEX `idx_tasks_progress` (`progress`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 14. quota_data（配额数据/看板）
-- Go: model/usedata.go QuotaData
-- ----------------------------------------------------------------------------
CREATE TABLE `quota_data` (
    `id`         INT         NOT NULL AUTO_INCREMENT,
    `user_id`    INT         NOT NULL,
    `username`   VARCHAR(64) NOT NULL DEFAULT '',
    `model_name` VARCHAR(64) NOT NULL DEFAULT '',
    `created_at` BIGINT      NOT NULL DEFAULT 0,
    `token_used` INT         NOT NULL DEFAULT 0,
    `count`      INT         NOT NULL DEFAULT 0,
    `quota`      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_qdt_user_id` (`user_id`),
    INDEX `idx_qdt_model_user_name` (`model_name`, `username`),
    INDEX `idx_qdt_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 15. subscription_plans（订阅计划表）
-- Go: model/subscription.go SubscriptionPlan
-- ----------------------------------------------------------------------------
CREATE TABLE `subscription_plans` (
    `id`                       INT          NOT NULL AUTO_INCREMENT,
    `title`                    VARCHAR(128) NOT NULL,
    `subtitle`                 VARCHAR(255) NOT NULL DEFAULT '',
    `price_amount`             DECIMAL(10,6) NOT NULL DEFAULT 0,
    `currency`                 VARCHAR(8)   NOT NULL DEFAULT 'USD',
    `duration_unit`            VARCHAR(16)  NOT NULL DEFAULT 'month',
    `duration_value`           INT          NOT NULL DEFAULT 1,
    `custom_seconds`           BIGINT       NOT NULL DEFAULT 0,
    `enabled`                  TINYINT(1)   NOT NULL DEFAULT 1,
    `sort_order`               INT          NOT NULL DEFAULT 0,
    `allow_balance_pay`        TINYINT(1)   DEFAULT 1,
    `stripe_price_id`          VARCHAR(128) NOT NULL DEFAULT '',
    `creem_product_id`         VARCHAR(128) NOT NULL DEFAULT '',
    `waffo_pancake_product_id` VARCHAR(128) NOT NULL DEFAULT '',
    `max_purchase_per_user`    INT          NOT NULL DEFAULT 0,
    `upgrade_group`            VARCHAR(64)  NOT NULL DEFAULT '',
    `total_amount`             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 16. user_subscriptions（用户订阅表）
-- Go: model/subscription.go UserSubscription
-- ----------------------------------------------------------------------------
CREATE TABLE `user_subscriptions` (
    `id`              INT         NOT NULL AUTO_INCREMENT,
    `user_id`         INT         NOT NULL,
    `plan_id`         INT         NOT NULL,
    `amount_total`    BIGINT      NOT NULL DEFAULT 0,
    `amount_used`     BIGINT      NOT NULL DEFAULT 0,
    `start_time`      BIGINT      NOT NULL DEFAULT 0,
    `end_time`        BIGINT      NOT NULL DEFAULT 0,
    `status`          VARCHAR(32) NOT NULL DEFAULT 'active',
    `source`          VARCHAR(32) NOT NULL DEFAULT 'order',
    `last_reset_time` BIGINT      NOT NULL DEFAULT 0,
    `next_reset_time` BIGINT      NOT NULL DEFAULT 0,
    `upgrade_group`   VARCHAR(64) NOT NULL DEFAULT '',
    `prev_user_group` VARCHAR(64) NOT NULL DEFAULT '',
    `created_at`      BIGINT      NOT NULL DEFAULT 0,
    `updated_at`      BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_user_sub_user_id` (`user_id`),
    INDEX `idx_user_sub_plan_id` (`plan_id`),
    INDEX `idx_user_sub_end_time` (`end_time`),
    INDEX `idx_user_sub_status` (`status`),
    INDEX `idx_user_sub_active` (`user_id`, `status`, `end_time`),
    INDEX `idx_user_sub_next_reset_time` (`next_reset_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 17. subscription_orders（订阅订单表）
-- Go: model/subscription.go SubscriptionOrder
-- ----------------------------------------------------------------------------
CREATE TABLE `subscription_orders` (
    `id`               INT          NOT NULL AUTO_INCREMENT,
    `user_id`          INT          NOT NULL,
    `plan_id`          INT          NOT NULL,
    `money`            DOUBLE       NOT NULL DEFAULT 0,
    `trade_no`         VARCHAR(255) NOT NULL,
    `payment_method`   VARCHAR(50)  DEFAULT NULL,
    `payment_provider` VARCHAR(50)  NOT NULL DEFAULT '',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'pending',
    `create_time`      BIGINT       NOT NULL DEFAULT 0,
    `complete_time`    BIGINT       NOT NULL DEFAULT 0,
    `provider_payload` TEXT         DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_sub_orders_trade_no` (`trade_no`),
    INDEX `idx_sub_orders_user_id` (`user_id`),
    INDEX `idx_sub_orders_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 18. subscription_pre_consume_records（订阅预消费记录表）
-- Go: model/subscription.go SubscriptionPreConsumeRecord
-- ----------------------------------------------------------------------------
CREATE TABLE `subscription_pre_consume_records` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `user_subscription_id`  INT          NOT NULL,
    `user_id`               INT          NOT NULL,
    `plan_id`               INT          NOT NULL,
    `amount`                BIGINT       NOT NULL DEFAULT 0,
    `model`                 VARCHAR(128) DEFAULT NULL,
    `token_id`              INT          NOT NULL DEFAULT 0,
    `request_id`            VARCHAR(64)  DEFAULT NULL,
    `created_at`            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_spr_user_sub_id` (`user_subscription_id`),
    INDEX `idx_spr_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 19. prefill_groups（可复用预设组表）
-- Go: model/prefill_group.go PrefillGroup
-- ----------------------------------------------------------------------------
CREATE TABLE `prefill_groups` (
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(64)  NOT NULL,
    `type`         VARCHAR(32)  NOT NULL,
    `items`        JSON         DEFAULT NULL,
    `description`  VARCHAR(255) DEFAULT NULL,
    `created_time` BIGINT       NOT NULL DEFAULT 0,
    `updated_time` BIGINT       NOT NULL DEFAULT 0,
    `deleted_at`   DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_prefill_name` (`name`),
    INDEX `idx_prefill_type` (`type`),
    INDEX `idx_prefill_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 20. passkey_credentials（Passkey 凭据表）
-- Go: model/passkey.go PasskeyCredential
-- ----------------------------------------------------------------------------
CREATE TABLE `passkey_credentials` (
    `id`               INT          NOT NULL AUTO_INCREMENT,
    `user_id`          INT          NOT NULL,
    `credential_id`    VARCHAR(512) NOT NULL,
    `public_key`       TEXT         NOT NULL,
    `attestation_type` VARCHAR(255) DEFAULT NULL,
    `aaguid`           VARCHAR(512) DEFAULT NULL,
    `sign_count`       INT UNSIGNED NOT NULL DEFAULT 0,
    `clone_warning`    TINYINT(1)   NOT NULL DEFAULT 0,
    `user_present`     TINYINT(1)   NOT NULL DEFAULT 0,
    `user_verified`    TINYINT(1)   NOT NULL DEFAULT 0,
    `backup_eligible`  TINYINT(1)   NOT NULL DEFAULT 0,
    `backup_state`     TINYINT(1)   NOT NULL DEFAULT 0,
    `transports`       TEXT         DEFAULT NULL,
    `attachment`       VARCHAR(32)  DEFAULT NULL,
    `last_used_at`     DATETIME(3)  DEFAULT NULL,
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at`       DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_passkey_user_id` (`user_id`),
    UNIQUE INDEX `idx_passkey_credential_id` (`credential_id`),
    INDEX `idx_passkey_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 21. two_fas（双因素认证表）
-- Go: model/twofa.go TwoFA
-- ----------------------------------------------------------------------------
CREATE TABLE `two_fas` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `user_id`         INT          NOT NULL,
    `secret`          VARCHAR(255) NOT NULL,
    `is_enabled`      TINYINT(1)   NOT NULL DEFAULT 0,
    `failed_attempts` INT          NOT NULL DEFAULT 0,
    `locked_until`    DATETIME(3)  DEFAULT NULL,
    `last_used_at`    DATETIME(3)  DEFAULT NULL,
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at`      DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_two_fas_user_id` (`user_id`),
    INDEX `idx_two_fas_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 22. two_fa_backup_codes（双因素备份码表）
-- Go: model/twofa.go TwoFABackupCode
-- ----------------------------------------------------------------------------
CREATE TABLE `two_fa_backup_codes` (
    `id`         INT          NOT NULL AUTO_INCREMENT,
    `user_id`    INT          NOT NULL,
    `code_hash`  VARCHAR(255) NOT NULL,
    `is_used`    TINYINT(1)   NOT NULL DEFAULT 0,
    `used_at`    DATETIME(3)  DEFAULT NULL,
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `deleted_at` DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_two_fa_bc_user_id` (`user_id`),
    INDEX `idx_two_fa_bc_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 23. user_oauth_bindings（用户 OAuth 绑定表）
-- Go: model/user_oauth_binding.go UserOAuthBinding
-- ----------------------------------------------------------------------------
CREATE TABLE `user_oauth_bindings` (
    `id`               INT          NOT NULL AUTO_INCREMENT,
    `user_id`          INT          NOT NULL,
    `provider_id`      INT          NOT NULL,
    `provider_user_id` VARCHAR(256) NOT NULL,
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE INDEX `ux_user_provider` (`user_id`, `provider_id`),
    UNIQUE INDEX `ux_provider_userid` (`provider_id`, `provider_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 24. custom_oauth_providers（自定义 OAuth 供应商表）
-- Go: model/custom_oauth_provider.go CustomOAuthProvider
-- ----------------------------------------------------------------------------
CREATE TABLE `custom_oauth_providers` (
    `id`                      INT          NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(64)  NOT NULL,
    `slug`                    VARCHAR(64)  NOT NULL,
    `icon`                    VARCHAR(128) NOT NULL DEFAULT '',
    `enabled`                 TINYINT(1)   NOT NULL DEFAULT 0,
    `client_id`               VARCHAR(256) DEFAULT NULL,
    `client_secret`           VARCHAR(512) DEFAULT NULL,
    `authorization_endpoint`  VARCHAR(512) DEFAULT NULL,
    `token_endpoint`          VARCHAR(512) DEFAULT NULL,
    `user_info_endpoint`      VARCHAR(512) DEFAULT NULL,
    `access_policy`           TEXT         DEFAULT NULL,
    `access_denied_message`   VARCHAR(512) DEFAULT NULL,
    `created_at`              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_oauth_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 25. setups（系统初始化状态表）
-- Go: model/setup.go Setup
-- ----------------------------------------------------------------------------
CREATE TABLE `setups` (
    `id`             INT         NOT NULL AUTO_INCREMENT,
    `version`        VARCHAR(50) NOT NULL,
    `initialized_at` BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 26. perf_metrics（性能指标表）
-- Go: model/perf_metric.go PerfMetric
-- ----------------------------------------------------------------------------
CREATE TABLE `perf_metrics` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `model_name`      VARCHAR(128) NOT NULL DEFAULT '',
    `group`           VARCHAR(64)  NOT NULL DEFAULT '',
    `bucket_ts`       BIGINT       NOT NULL DEFAULT 0,
    `request_count`   INT          NOT NULL DEFAULT 0,
    `error_count`     INT          NOT NULL DEFAULT 0,
    `total_latency_ms` BIGINT      NOT NULL DEFAULT 0,
    `avg_latency_ms`  DOUBLE       NOT NULL DEFAULT 0,
    `max_latency_ms`  BIGINT       NOT NULL DEFAULT 0,
    `success_count`   INT          NOT NULL DEFAULT 0,
    `avg_ttft_ms`     DOUBLE       NOT NULL DEFAULT 0,
    `max_ttft_ms`     BIGINT       NOT NULL DEFAULT 0,
    `avg_tpot_ms`     DOUBLE       NOT NULL DEFAULT 0,
    `max_tpot_ms`     BIGINT       NOT NULL DEFAULT 0,
    `avg_token_per_s` DOUBLE       NOT NULL DEFAULT 0,
    `max_token_per_s` DOUBLE       NOT NULL DEFAULT 0,
    `created_at`      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_perf_ts` (`bucket_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
