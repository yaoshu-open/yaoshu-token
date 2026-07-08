package yaoshu.token;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类 —— 所有 *IT.java 必须继承本类。
 * <p>
 * 使用 TestRestTemplate + RANDOM_PORT 走完整 HTTP 链路（Filter → Controller → Service → Mapper → DB）。
 * Profile 策略：dev + dev-confidential（复用 dev 数据库/Redis）+ test（测试增量配置）。
 * test profile 放在 application-test.yml，只声明测试差异项（禁用 Flyway、关闭 JVM GC 优化等），
 * main 的 server/spring/yue/sa-token 等配置通过 profile 继承生效。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = TokenApplication.class
)
@ActiveProfiles({"dev", "dev-confidential", "test"})
public abstract class BaseIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void baseSetUp() {
        // yue-library 逻辑删除/审计/租户均关闭，无需 AuthContext
    }

    /**
     * 构造本服务完整 URL
     */
    protected String apiUrl(String path) {
        return "http://localhost:" + port + (path.startsWith("/") ? path : "/" + path);
    }

    /** 随机端口，由 @LocalServerPort 自动注入 */
    @LocalServerPort
    protected int port;
}
