ALTER TABLE `logs`
    ADD COLUMN `cached_tokens` INT NOT NULL DEFAULT 0 AFTER `completion_tokens`,
    ADD COLUMN `key_index` INT DEFAULT NULL AFTER `upstream_request_id`;
