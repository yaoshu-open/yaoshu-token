package yaoshu.token.publicapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import yaoshu.token.BaseIntegrationTest;

/**
 * 公共系统端点集成测试 — 无需认证的公开端点。
 * <p>
 * 覆盖场景：UC-50~57。验证系统初始化、状态、公告、协议等公开端点的可达性。
 * 响应格式为 yue-library Result {code, msg, flag, trace_id, data}。
 */
@DisplayName("公共系统端点")
public class SystemPublicIT extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        // 公开端点无需鉴权准备
    }

    @Test
    @DisplayName("UC-50: 系统初始状态 — 200 + 响应可达")
    void uc50Setup() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/setup"), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-50 /api/setup 应返回 2xx").isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("data")).isNotNull();
    }

    @Test
    @DisplayName("UC-51: 运行状态 — 200 + success:true")
    void uc51Status() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/status"), Map.class);
        assertSuccess(resp, "UC-51");
        assertThat(resp.getBody().get("data")).isNotNull();
    }

    @Test
    @DisplayName("UC-52: Uptime 探活 — 200 + 返回字符串")
    void uc52Uptime() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                apiUrl("/api/uptime/status"), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-52 /api/uptime/status 应返回 2xx").isTrue();
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    @DisplayName("UC-53: 系统公告 — 200 + success:true")
    void uc53Notice() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/notice"), Map.class);
        assertSuccess(resp, "UC-53");
    }

    @Test
    @DisplayName("UC-54: 关于信息 — 200 + success:true")
    void uc54About() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(apiUrl("/api/about"), Map.class);
        assertSuccess(resp, "UC-54");
    }

    @Test
    @DisplayName("UC-55: 首页内容 — 200 + success:true")
    void uc55HomePageContent() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/home_page_content"), Map.class);
        assertSuccess(resp, "UC-55");
    }

    @Test
    @DisplayName("UC-56: 用户协议 — 200 + success:true")
    void uc56UserAgreement() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/legal/user_agreement"), Map.class);
        assertSuccess(resp, "UC-56");
    }

    @Test
    @DisplayName("UC-57: 隐私政策 — 200 + flag:true")
    void uc57PrivacyPolicy() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/legal/privacy_policy"), Map.class);
        assertSuccess(resp, "UC-57");
    }

    @Test
    @DisplayName("UC-66: 倍率配置 — 无需认证可达（200，非 401）")
    void uc66RatioConfigNoAuth() {
        // Bug-002 核心验证：PublicPaths 已放行 /api/ratio_config，无 token 访问不应被鉴权拦截（HTTP 2xx，非 401）
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                apiUrl("/api/ratio_config"), Map.class);
        assertThat(resp.getStatusCode().value())
                .as("UC-66 /api/ratio_config 无 token 应可达（2xx），不应被鉴权拦截（401）")
                .isNotEqualTo(401);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("UC-66 /api/ratio_config 应返回 2xx").isTrue();
    }

    // ======================== 辅助方法 ====================

    private void assertSuccess(ResponseEntity<Map> resp, String label) {
        Map<String, Object> result = resp.getBody();
        assertThat(result.get("flag")).as("%s flag 应为 true", label).isEqualTo(true);
    }
}
