package yaoshu.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试基础设施自测 —— 验证 Spring 上下文启动成功 + TestRestTemplate 可达。
 * <p>
 * 这个测试是阶段 1 的验收标准：通过则说明数据库/Redis/test 配置全部就绪。
 */
@DisplayName("测试基础设施自测")
class BaseIntegrationTestSelfIT extends BaseIntegrationTest {

    @Test
    @DisplayName("Spring 上下文启动成功")
    void contextLoads() {
        assertNotNull(restTemplate);
        assertNotNull(jdbcTemplate);
        assertTrue(port > 0, "端口应被 @LocalServerPort 注入");
    }

    @Test
    @DisplayName("数据库连接可用")
    void databaseIsReachable() {
        Integer count = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("HTTP 服务可达")
    void httpServerIsReachable() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                apiUrl("/"), String.class);
        assertNotNull(resp.getStatusCode());
        assertTrue(resp.getStatusCode().is2xxSuccessful()
                || resp.getStatusCode() == HttpStatus.NOT_FOUND,
                "服务应正常响应");
    }
}
