ALTER TABLE `custom_oauth_providers`
    ADD COLUMN IF NOT EXISTS `scopes` VARCHAR(256) NOT NULL DEFAULT 'openid profile email' AFTER `user_info_endpoint`,
    ADD COLUMN IF NOT EXISTS `user_id_field` VARCHAR(128) NOT NULL DEFAULT 'sub' AFTER `scopes`,
    ADD COLUMN IF NOT EXISTS `username_field` VARCHAR(128) NOT NULL DEFAULT 'preferred_username' AFTER `user_id_field`,
    ADD COLUMN IF NOT EXISTS `display_name_field` VARCHAR(128) NOT NULL DEFAULT 'name' AFTER `username_field`,
    ADD COLUMN IF NOT EXISTS `email_field` VARCHAR(128) NOT NULL DEFAULT 'email' AFTER `display_name_field`,
    ADD COLUMN IF NOT EXISTS `well_known` VARCHAR(512) NULL DEFAULT NULL AFTER `email_field`,
    ADD COLUMN IF NOT EXISTS `auth_style` INT NOT NULL DEFAULT 0 AFTER `well_known`;
