package yaoshu.token.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

/**
 * 排行榜集成测试。
 * <p>
 * 覆盖场景：各统计周期（today/week/month/year/all）+ 无效周期。
 * RankingsController 无显式鉴权检查，读写已有生产数据。
 * <p>
 * 无需前置数据准备——排行榜基于 quota_data 表已有数据聚合。
 */
public class RankingsIT extends BaseIntegrationTest {

    /**
     * 默认周期（week），无需鉴权。
     */
    @Test
    void getRankingsDefaultPeriod() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
        assertThat(result.get("data")).isNotNull();
    }

    @Test
    void getRankingsToday() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=today"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getRankingsWeek() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=week"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getRankingsMonth() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=month"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getRankingsYear() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=year"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getRankingsAll() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=all"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).isEqualTo(true);
    }

    @Test
    void getRankingsInvalidPeriod() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/rankings?period=invalid"), Map.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();
        // 无效 period 也应返回 flag=true（Go 源码 fallback 到 week）
        assertThat(result.get("flag")).isEqualTo(true);
    }
}
