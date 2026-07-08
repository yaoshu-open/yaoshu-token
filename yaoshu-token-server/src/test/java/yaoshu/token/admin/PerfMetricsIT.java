package yaoshu.token.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

/**
 * 性能指标集成测试。
 * <p>
 * 覆盖场景：sumary 汇总查询 + 单模型详情查询 + 无 model 参数校验。
 * PerfMetricsController 无显式鉴权检查（HeaderNavModulePublicOrUserAuth）。
 * <p>
 * 测试数据通过 JdbcTemplate 直接插入 perf_metrics 表（管理后台无对应写入 API，符合规范 §二例外）。
 */
public class PerfMetricsIT extends BaseIntegrationTest {

    private static final String TEST_MODEL = "test_metrics_model";
    private static final String TEST_GROUP = "auto";

    @BeforeEach
    void insertTestData() {
        long now = System.currentTimeMillis() / 1000;
        // 插入 2 个时间桶的测试数据
        for (int i = 0; i < 2; i++) {
            jdbcTemplate.update(
                    "INSERT INTO perf_metrics (model_name, `group`, bucket_ts, request_count, " +
                    "error_count, total_latency_ms, avg_latency_ms, max_latency_ms, " +
                    "success_count, ttft_sum_ms, ttft_count, " +
                    "avg_ttft_ms, max_ttft_ms, avg_tpot_ms, max_tpot_ms, " +
                    "avg_token_per_s, max_token_per_s, output_tokens, generation_ms, created_at) " +
                    "VALUES (?, ?, ?, 10, 1, 5000, 500.0, 800, 9, 2000, 10, 200.0, 300, 100.0, 200, 50.0, 60.0, 1000, 3000, ?)",
                    TEST_MODEL, TEST_GROUP, now - i * 1800, now);
        }
    }

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM perf_metrics WHERE model_name = ?", TEST_MODEL);
    }

    @Test
    void getSummary() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/perf-metrics/summary?hours=24"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
        assertThat(result.get("data")).isNotNull();
    }

    @Test
    void getPerfMetricsByModel() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/perf-metrics?model=" + TEST_MODEL + "&group=" + TEST_GROUP + "&hours=24"),
                Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getPerfMetricsMissingModel() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/perf-metrics"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        // 无 model 参数应返回 flag=false
        assertThat(result.get("flag")).isEqualTo(false);
    }

    @Test
    void getPerfMetricsDefaultHours() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/perf-metrics?model=" + TEST_MODEL),
                Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }
}
