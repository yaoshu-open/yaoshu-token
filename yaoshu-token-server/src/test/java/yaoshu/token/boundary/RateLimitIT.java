package yaoshu.token.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.yue.library.data.redis.client.Redis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.constant.CommonConstants;

/**
 * 全局限流边界测试 —— RateLimitFilter 的触发与恢复场景。
 * <p>
 * 使用 @DirtiesContext 确保独享 Spring 上下文，避免其他测试类写入了 RateLimitFilter 内存存储。
 * 每个测试方法前清理 Redis 限流 key，避免方法间状态污染（4 个方法共享 clientIP 127.0.0.1，同一 Redis key）。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class RateLimitIT extends BaseIntegrationTest {

    private static final String TEST_URL = "/api/rankings?period=today";

    @Autowired
    private Redis redis;

    private boolean originalEnable;
    private int originalNum;
    private long originalDuration;

    @BeforeEach
    void setUp() {
        originalEnable = CommonConstants.globalApiRateLimitEnable;
        originalNum = CommonConstants.globalApiRateLimitNum;
        originalDuration = CommonConstants.globalApiRateLimitDuration;
        // 清理 Redis 限流状态，确保每个测试方法有干净起点
        // 注：4 个测试方法共享 clientIP，@DirtiesContext(BEFORE_CLASS) 不隔离方法间 Redis 状态，
        // 不清理会导致消耗限额的方法污染后续方法（JUnit 5 随机方法顺序下偶发失败）
        redis.getRedisson().getKeys().deleteByPattern("rateLimit:GA*");
    }

    @AfterEach
    void tearDown() {
        CommonConstants.globalApiRateLimitEnable = originalEnable;
        CommonConstants.globalApiRateLimitNum = originalNum;
        CommonConstants.globalApiRateLimitDuration = originalDuration;
    }

    // ======================== 辅助方法 ========================

    private ResponseEntity<String> getRankings() {
        return restTemplate.exchange(apiUrl(TEST_URL), HttpMethod.GET, null, String.class);
    }

    /** 发送 N 个请求，返回状态码数组。 */
    private int[] rapidRequests(int count) {
        int[] codes = new int[count];
        for (int i = 0; i < count; i++) {
            codes[i] = getRankings().getStatusCode().value();
        }
        return codes;
    }

    // ======================== 测试用例 ========================

    @Test
    void rateLimitDisabledShouldPass() {
        CommonConstants.globalApiRateLimitEnable = false;

        ResponseEntity<String> resp = getRankings();
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("限流关闭时应正常返回")
                .isTrue();
    }

    @Test
    void rateLimitEnabledShouldTrigger429Eventually() {
        CommonConstants.globalApiRateLimitEnable = true;
        CommonConstants.globalApiRateLimitNum = 2;
        CommonConstants.globalApiRateLimitDuration = 60;

        // 发送 10 个请求，前几个应通过，后面应出现 429
        int[] codes = rapidRequests(10);

        // 验证至少 1 个请求正常通过
        boolean hasOk = false;
        boolean has429 = false;
        for (int code : codes) {
            if (code == 200) hasOk = true;
            if (code == 429) has429 = true;
        }

        assertThat(hasOk).as("应有至少 1 个请求正常通过").isTrue();
        assertThat(has429).as("应有 429 限流响应").isTrue();

        // 出现 429 后不应再有 200
        boolean seen429 = false;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i] == 429) {
                seen429 = true;
            } else if (seen429) {
                assertThat(codes[i]).as("请求 %d 在 429 之后不应返回成功", i).isNotEqualTo(200);
            }
        }
    }

    @Test
    void rateLimitShouldRecoverAfterWindowExpiry() throws Exception {
        // 短窗口便于快速测试恢复
        // 注：duration=5 + 10 请求确保远程 Redis 网络往返下稳定触发 429（duration=2 + 5 请求在秒级时间戳精度下偶发全部放行）
        CommonConstants.globalApiRateLimitEnable = true;
        CommonConstants.globalApiRateLimitNum = 2;
        CommonConstants.globalApiRateLimitDuration = 5;

        // 发送足够多的请求以触发限流
        int[] codes = rapidRequests(10);
        boolean triggered429 = false;
        for (int code : codes) {
            if (code == 429) { triggered429 = true; break; }
        }
        assertThat(triggered429).as("短窗口内应触发 429").isTrue();

        // 等待窗口过期 + 额外缓冲（duration=5s，sleep=7s 确保 oldest 时间戳出窗）
        Thread.sleep(7000);

        // 窗口过期后应立即恢复
        int recoveredCode = getRankings().getStatusCode().value();
        assertThat(recoveredCode).as("窗口过期后应恢复 200").isEqualTo(200);
    }

    @Test
    void rateLimitWithDifferentWindowShouldNotInterfere() throws Exception {
        // 先设置短窗口消耗限额
        CommonConstants.globalApiRateLimitEnable = true;
        CommonConstants.globalApiRateLimitNum = 2;
        CommonConstants.globalApiRateLimitDuration = 5;

        // 消耗限额并验证限流触发
        // 注：duration=5 + 10 请求确保远程 Redis 网络往返下稳定触发 429（duration=2 + 少量请求在秒级时间戳精度下偶发全部放行），
        // 采用"任一 429"断言避免时序敏感
        int[] codes = rapidRequests(10);
        int blockedCode = getRankings().getStatusCode().value();
        boolean has429 = (blockedCode == 429);
        for (int code : codes) {
            if (code == 429) { has429 = true; break; }
        }
        assertThat(has429).as("短窗口内应触发至少一次 429 限流").isTrue();

        // 等待窗口过期（duration=5s，sleep=6s 确保窗口过期；后半段 num 增大至 5 使 listLength<num 直接放行，双重保障恢复 200）
        Thread.sleep(6000);

        // 修改为更大的窗口和限额
        CommonConstants.globalApiRateLimitNum = 5;
        CommonConstants.globalApiRateLimitDuration = 60;

        // 新配置下应可正常请求
        int recoveredCode = getRankings().getStatusCode().value();
        assertThat(recoveredCode).as("窗口过期+新配置下应正常返回 200").isEqualTo(200);
    }
}
