package yaoshu.token.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import ai.yue.library.base.exception.ResultException;
import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.pojo.ipo.SetupIPO;
import yaoshu.token.service.SetupService;

/**
 * 系统初始化集成测试
 * <p>
 * 覆盖两条初始化路径（auto / interactive）的核心逻辑：
 * <ol>
 * <li>GET /api/setup 响应结构（HTTP 只读）</li>
 * <li>auto 模式：幂等、创建 root、老库兼容</li>
 * <li>POST /api/setup：创建 root、重复拒绝、密码校验</li>
 * </ol>
 * 改库测试方法加 {@code @Transactional} 自动回滚，不污染 dev 库。
 */
@DisplayName("系统初始化")
public class SetupIT extends BaseIntegrationTest {

    @Autowired
    private SetupService setupService;

    // ======================== HTTP 端点（只读） ========================

    @Test
    @DisplayName("GET /api/setup 返回正确状态结构")
    void getSetupStatusHttp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/setup"), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("GET /api/setup 应返回 2xx").isTrue();
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).containsKeys("status", "rootInit", "databaseType");
        assertThat(data.get("databaseType")).isEqualTo("mysql");
    }

    // ======================== Service 层：幂等与查询 ========================

    @Test
    @DisplayName("isInitialized: 启动后 setups 表有记录，返回 true")
    void isInitializedTrue() {
        // RootUserInitializer 启动时已写入 setups 记录（老库兼容或首次创建）
        assertThat(setupService.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("rootUserExists: 存在 role=3 用户，返回 true")
    void rootUserExistsTrue() {
        assertThat(setupService.rootUserExists()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("initializeAuto 幂等: 已初始化时不重复创建 root")
    void initializeAutoIdempotent() {
        Integer rootBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 3", Integer.class);
        Integer setupBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM setups", Integer.class);

        setupService.initializeAuto(); // 已初始化，应直接返回

        Integer rootAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 3", Integer.class);
        Integer setupAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM setups", Integer.class);
        assertThat(rootAfter).isEqualTo(rootBefore);
        assertThat(setupAfter).isEqualTo(setupBefore);
    }

    // ======================== Service 层：auto 模式创建（@Transactional 回滚） ========================

    @Test
    @Transactional
    @DisplayName("initializeAuto: 未初始化 + 无 root → 创建 root + 写 setups")
    void initializeAutoCreatesRoot() {
        // 清空 setups + root 用户（事务内，测试后回滚）
        jdbcTemplate.update("DELETE FROM setups");
        jdbcTemplate.update("DELETE FROM users WHERE role = 3");

        setupService.initializeAuto();

        Integer rootCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 3", Integer.class);
        assertThat(rootCount).as("应创建 1 个 root 用户").isEqualTo(1);
        Integer setupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM setups", Integer.class);
        assertThat(setupCount).as("应写入 1 条 setups 记录").isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("initializeAuto 老库兼容: 已有 root 但无 setups → 仅补写记录")
    void initializeAutoLegacyCompat() {
        // 保留 root 用户，仅清空 setups
        jdbcTemplate.update("DELETE FROM setups");
        Integer rootBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 3", Integer.class);
        assertThat(rootBefore).as("前置：dev 库应有 root 用户").isGreaterThan(0);

        setupService.initializeAuto();

        Integer rootAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 3", Integer.class);
        assertThat(rootAfter).as("老库兼容不应新增 root").isEqualTo(rootBefore);
        Integer setupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM setups", Integer.class);
        assertThat(setupCount).as("应补写 setups 记录").isEqualTo(1);
    }

    // ======================== Service 层：POST /api/setup 逻辑（@Transactional 回滚） ========================

    @Test
    @DisplayName("postSetup: 已初始化 → 抛 ResultException 拒绝")
    void postSetupAlreadyInitializedRejected() {
        // 启动后 setups 已有记录（非 @Transactional，读取已提交状态）
        SetupIPO ipo = buildValidIpo("blocked_by_init", "validPass123");
        assertThatThrownBy(() -> setupService.postSetup(ipo))
                .isInstanceOf(ResultException.class);
    }

    @Test
    @Transactional
    @DisplayName("postSetup: 未初始化 → 创建 root + 写 options + 写 setups")
    void postSetupCreatesRoot() {
        jdbcTemplate.update("DELETE FROM setups");
        jdbcTemplate.update("DELETE FROM users WHERE role = 3");

        SetupIPO ipo = buildValidIpo("test_setup_admin", "setupPass123");
        ipo.setSelfUseModeEnabled(true);
        ipo.setDemoSiteEnabled(false);
        setupService.postSetup(ipo);

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT username, role, status FROM users WHERE username = 'test_setup_admin'");
        assertThat(user.get("role")).isEqualTo(3);
        assertThat(user.get("status")).isEqualTo(1);
        assertThat(setupService.isInitialized()).isTrue();
        // options 应写入
        String selfUse = jdbcTemplate.queryForObject(
                "SELECT value FROM options WHERE `key` = 'SelfUseModeEnabled'", String.class);
        assertThat(selfUse).isEqualTo("true");
    }

    @Test
    @Transactional
    @DisplayName("postSetup: 密码不一致 → 抛 ResultException")
    void postSetupPasswordMismatchRejected() {
        jdbcTemplate.update("DELETE FROM setups");
        jdbcTemplate.update("DELETE FROM users WHERE role = 3");

        SetupIPO ipo = buildValidIpo("mismatch_user", "passwordAaaa");
        ipo.setConfirmPassword("passwordBbbb"); // 不一致
        assertThatThrownBy(() -> setupService.postSetup(ipo))
                .isInstanceOf(ResultException.class);
    }

    // ======================== 辅助方法 ========================

    private SetupIPO buildValidIpo(String username, String password) {
        SetupIPO ipo = new SetupIPO();
        ipo.setUsername(username);
        ipo.setPassword(password);
        ipo.setConfirmPassword(password);
        return ipo;
    }
}
