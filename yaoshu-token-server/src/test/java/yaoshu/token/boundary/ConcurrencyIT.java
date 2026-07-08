package yaoshu.token.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 并发边界测试 —— 并发请求下的系统稳定性与数据一致性。
 * <p>
 * 覆盖场景：
 * <ol>
 *   <li>多线程并发认证（TokenAuthFilter）—— 不同 Token 同时认证，无跨线程干扰</li>
 *   <li>多线程并发公开只读 —— /api/rankings 并发访问无异常</li>
 *   <li>并发配额更新一致性 —— 多线程对同一用户配额增量更新，最终值正确</li>
 *   <li>并发 Token 请求属性隔离 —— 相同 token 并发请求时属性不串线</li>
 * </ol>
 */
public class ConcurrencyIT extends BaseIntegrationTest {

    private static final int CONCURRENT_THREADS = 8;
    private static final String TEST_USERNAME = "test_conc_user";
    private static final String TEST_PWD_HASH = "$2a$10$test_conc_hash_placeholder";
    private static final String TOKEN_KEY_PREFIX = "testconctoken";

    private Integer userId;
    private final List<String> tokenKeys = new ArrayList<>();

    @BeforeEach
    void setUpTestData() {
        // 清理
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testconctoken%'");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);

        // 用户
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, quota) " +
                "VALUES (?, ?, 'Concurrency User', 1, 1, 1000000)",
                TEST_USERNAME, TEST_PWD_HASH);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USERNAME);

        long now = System.currentTimeMillis() / 1000;

        // 创建 CONCURRENT_THREADS 个 Token，每个有独立 key
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            String key = "sk-" + TOKEN_KEY_PREFIX + i + "aaaaaaaaaaaaaaaa";
            tokenKeys.add(key);
            jdbcTemplate.update(
                    "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                    "VALUES (?, ?, 1, 5000, 0, -1, ?, ?, ?)",
                    key, userId, now, now, "Concurrency Token " + i);
        }

        // 独立 Token 用于属性隔离测试（所有线程共用同一个 token）
        String sharedKey = "sk-" + TOKEN_KEY_PREFIX + "sharedaaaaaaaaaa";
        jdbcTemplate.update(
                "INSERT INTO tokens (`key`, user_id, status, remain_quota, unlimited_quota, expired_time, created_time, accessed_time, name) " +
                "VALUES (?, ?, 1, 5000, 0, -1, ?, ?, 'Shared Token')",
                sharedKey, userId, now, now);
        tokenKeys.add(sharedKey);
    }

    @AfterEach
    void tearDownTestData() {
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` LIKE 'sk-testconctoken%'");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);
    }

    // ======================== 辅助方法 ========================

    private Map<String, Object> minimalRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-3.5-turbo");
        body.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        return body;
    }

    /**
     * 发送带指定 Bearer Token 的 /v1/chat/completions 请求，返回 HTTP 状态码。
     */
    private int v1ChatStatusCode(String tokenKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + tokenKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(minimalRequestBody(), headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);
            return resp.getStatusCode().value();
        } catch (Exception e) {
            return -1; // 连接异常
        }
    }

    // ======================== 测试用例 ========================

    @Test
    void concurrentAuthShouldNotInterfere() throws Exception {
        // 多线程用不同 Token 同时认证 —— 每个线程应通过认证（非 401）
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int idx = i;
            futures.add(CompletableFuture.supplyAsync(() ->
                    v1ChatStatusCode(tokenKeys.get(idx)), executor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(30, TimeUnit.SECONDS);

        int passCount = 0;
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            int statusCode = futures.get(i).get();
            assertThat(statusCode)
                    .as("线程 %d 不应返回 401（应通过认证）", i)
                    .isNotEqualTo(401);
            assertThat(statusCode)
                    .as("线程 %d 不应连接异常", i)
                    .isNotEqualTo(-1);
            if (statusCode != 401 && statusCode != -1) passCount++;
        }

        assertThat(passCount)
                .as("所有 %d 个线程均应通过认证", CONCURRENT_THREADS)
                .isEqualTo(CONCURRENT_THREADS);

        executor.shutdown();
    }

    @Test
    void concurrentPublicReadsShouldNotFail() throws Exception {
        // 并发 GET /api/rankings（公开端点）
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ResponseEntity<String> resp = restTemplate.exchange(
                            apiUrl("/api/rankings?period=today"),
                            HttpMethod.GET, null, String.class);
                    return resp.getStatusCode().is2xxSuccessful();
                } catch (Exception e) {
                    return false;
                }
            }, executor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(30, TimeUnit.SECONDS);

        int successCount = 0;
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            if (Boolean.TRUE.equals(futures.get(i).get())) successCount++;
        }

        assertThat(successCount)
                .as("所有 %d 个并发公开读请求均应成功", CONCURRENT_THREADS)
                .isEqualTo(CONCURRENT_THREADS);

        executor.shutdown();
    }

    @Test
    void concurrentQuotaUpdateShouldBeConsistent() throws Exception {
        // 初始化用户配额
        jdbcTemplate.update("UPDATE users SET quota = 1000, used_quota = 0 WHERE id = ?", userId);

        int incrementPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                // 每次增加配额（使用 UPDATE SET quota = quota + N 保证原子性）
                jdbcTemplate.update(
                        "UPDATE users SET quota = quota + ?, used_quota = used_quota + ? WHERE id = ?",
                        incrementPerThread, incrementPerThread, userId);
            }, executor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(30, TimeUnit.SECONDS);

        // 验证最终配额 = 初始值 + CONCURRENT_THREADS * incrementPerThread
        Integer finalQuota = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?", Integer.class, userId);
        Integer finalUsedQuota = jdbcTemplate.queryForObject(
                "SELECT used_quota FROM users WHERE id = ?", Integer.class, userId);

        int expectedQuota = 1000 + CONCURRENT_THREADS * incrementPerThread;
        int expectedUsedQuota = CONCURRENT_THREADS * incrementPerThread;

        assertThat(finalQuota)
                .as("并发配额增量更新应一致：期望 %d，实际 %d", expectedQuota, finalQuota)
                .isEqualTo(expectedQuota);
        assertThat(finalUsedQuota)
                .as("并发已用配额增量应一致：期望 %d，实际 %d", expectedUsedQuota, finalUsedQuota)
                .isEqualTo(expectedUsedQuota);

        executor.shutdown();
    }

    @Test
    void concurrentSameTokenRequestsShouldNotCrossInterfere() throws Exception {
        // 多个线程共用同一个 Token 并发请求，验证无串线（如请求属性互相污染）
        String sharedKey = tokenKeys.get(CONCURRENT_THREADS); // 最后一个 = shared key
        int threadCount = 4; // 略小的并发数避免上游限流

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    v1ChatStatusCode(sharedKey), executor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(30, TimeUnit.SECONDS);

        for (int i = 0; i < threadCount; i++) {
            int statusCode = futures.get(i).get();
            // 相同 Token 并发请求不应返回 401（请求属性不应串线到不同线程）
            assertThat(statusCode)
                    .as("共享 Token 并发线程 %d 不应返回 401（属性串线检测）", i)
                    .isNotEqualTo(401);
            assertThat(statusCode)
                    .as("共享 Token 并发线程 %d 不应连接异常", i)
                    .isNotEqualTo(-1);
        }

        executor.shutdown();
    }
}
