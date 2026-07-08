-- V4: 补全 perf_metrics 表在 V1__baseline.sql 中遗漏的列
-- Go 源码 model/perf_metric.go 中有对应字段，INSERT/UPDATE 使用，此处补全

ALTER TABLE `perf_metrics`
    ADD COLUMN `output_tokens` INT NOT NULL DEFAULT 0 AFTER `total_latency_ms`,
    ADD COLUMN `generation_ms` BIGINT NOT NULL DEFAULT 0 AFTER `output_tokens`,
    ADD COLUMN `ttft_sum_ms` BIGINT NOT NULL DEFAULT 0 AFTER `success_count`,
    ADD COLUMN `ttft_count` INT NOT NULL DEFAULT 0 AFTER `ttft_sum_ms`;
