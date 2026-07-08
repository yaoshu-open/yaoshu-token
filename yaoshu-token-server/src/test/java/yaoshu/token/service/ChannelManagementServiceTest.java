package yaoshu.token.service;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.dto.ChannelInfoDTO;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.ipo.ChannelIPO;
import yaoshu.token.service.CodexOAuthService.CodexOAuthAuthorizationFlow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChannelManagementService 白盒测试。
 *
 * <p>TRACK-2 防回归：断言管理端响应 Map 的 key 为 camelCase，避免再次回退到 snake_case 导致前端
 * MultiKeyManageDialog / codex 凭证刷新路径字段读取失败。</p>
 *
 * <p>覆盖方法：
 * <ul>
 *   <li>{@code startCodexOAuth} — authorizeUrl</li>
 *   <li>{@code getMultiKeyStatus}（private，反射调用）— disabledTime / keyPreview / pageSize /
 *       totalPages / enabledCount / manualDisabledCount / autoDisabledCount</li>
 * </ul>
 * </p>
 *
 * <p>其余 5 个方法（completeCodexOAuth / refreshCodexCredential / getCodexUsage /
 * applyUpstreamModelUpdates / detectAllUpstreamModelUpdates）由 ChannelIT 集成测试覆盖，
 * 本类不重复 mock 复杂依赖链。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelManagementService — TRACK-2 Map key camelCase 防回归")
class ChannelManagementServiceTest {

    @Mock
    private CodexOAuthService codexOAuthService;

    @InjectMocks
    private ChannelManagementService service;

    // ======================== startCodexOAuth — authorizeUrl ========================

    @Test
    @DisplayName("startCodexOAuth 返回 Map key 为 authorizeUrl（非 authorize_url）")
    void startCodexOAuthReturnsCamelCaseKey() {
        HttpSession session = mock(HttpSession.class);
        CodexOAuthAuthorizationFlow flow = new CodexOAuthAuthorizationFlow(
                "state-xyz", "verifier-xyz", "challenge-xyz", "https://auth.openai.com/oauth/authorize");
        when(codexOAuthService.createAuthorizationFlow()).thenReturn(flow);

        Map<String, Object> result = service.startCodexOAuth(0, session);

        assertNotNull(result);
        assertEquals("https://auth.openai.com/oauth/authorize", result.get("authorizeUrl"));
        assertNull(result.get("authorize_url"),
                "TRACK-2 防回归：authorize_url snake_case 已废弃，前端 types.ts 期望 authorizeUrl");
    }

    // ======================== getMultiKeyStatus — 反射调用断言 7 个 key ========================

    @Test
    @DisplayName("getMultiKeyStatus 返回 Map key 全部为 camelCase（反射调用 private 方法）")
    void getMultiKeyStatusReturnsCamelCaseKeys() throws Exception {
        // 构造 Channel：3 个 key（JSON 数组格式，与 parseKeys 兼容）
        Channel channel = new Channel();
        channel.setKey("[\"sk-key1\",\"sk-key2\",\"sk-key3\"]");

        // 构造 ChannelInfoDTO：key0=ENABLED, key1=MANUALLY_DISABLED, key2=AUTO_DISABLED
        ChannelInfoDTO info = new ChannelInfoDTO();
        info.setMultiKeyStatusList(Map.of(
                0, CommonConstants.CHANNEL_STATUS_ENABLED,
                1, CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED,
                2, CommonConstants.CHANNEL_STATUS_AUTO_DISABLED));
        info.setMultiKeyDisabledTime(Map.of(1, 1000L, 2, 2000L));
        info.setMultiKeyDisabledReason(Map.of(1, "manual ban", 2, "auto ban: quota exceeded"));

        // 构造 IPO：page=1, pageSize=10（不传 status filter，返回全部 3 个 key）
        ChannelIPO.MultiKeyManage ipo = new ChannelIPO.MultiKeyManage();
        ipo.setPage(1);
        ipo.setPageSize(10);

        Map<String, Object> result = invokeGetMultiKeyStatus(ipo, channel, info);

        assertNotNull(result);
        // 顶层分页统计字段断言（5 个 camelCase）
        assertEquals(3, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(10, result.get("pageSize"));
        assertEquals(1, result.get("totalPages"));
        assertEquals(1, result.get("enabledCount"));
        assertEquals(1, result.get("manualDisabledCount"));
        assertEquals(1, result.get("autoDisabledCount"));
        // snake_case 反向断言（防回退）
        assertNull(result.get("page_size"), "TRACK-2 防回归：page_size 已废弃");
        assertNull(result.get("total_pages"), "TRACK-2 防回归：total_pages 已废弃");
        assertNull(result.get("enabled_count"), "TRACK-2 防回归：enabled_count 已废弃");
        assertNull(result.get("manual_disabled_count"), "TRACK-2 防回归：manual_disabled_count 已废弃");
        assertNull(result.get("auto_disabled_count"), "TRACK-2 防回归：auto_disabled_count 已废弃");

        // keys 数组中的 item 字段断言（disabledTime / keyPreview 2 个 camelCase）
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> keys =
                (java.util.List<Map<String, Object>>) result.get("keys");
        assertNotNull(keys);
        assertEquals(3, keys.size());

        // key1（MANUALLY_DISABLED）— 验证 disabledTime / keyPreview
        Map<String, Object> key1Item = keys.get(1);
        assertEquals(CommonConstants.CHANNEL_STATUS_MANUALLY_DISABLED, key1Item.get("status"));
        assertEquals(1000L, key1Item.get("disabledTime"));
        assertEquals("manual ban", key1Item.get("reason"));
        assertNotNull(key1Item.get("keyPreview"));
        assertNull(key1Item.get("disabled_time"), "TRACK-2 防回归：disabled_time 已废弃");
        assertNull(key1Item.get("key_preview"), "TRACK-2 防回归：key_preview 已废弃");

        // key2（AUTO_DISABLED）— 验证 disabledTime
        Map<String, Object> key2Item = keys.get(2);
        assertEquals(CommonConstants.CHANNEL_STATUS_AUTO_DISABLED, key2Item.get("status"));
        assertEquals(2000L, key2Item.get("disabledTime"));
        assertEquals("auto ban: quota exceeded", key2Item.get("reason"));
    }

    /** 通过反射调用 private getMultiKeyStatus，避免 mock 整条依赖链。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGetMultiKeyStatus(
            ChannelIPO.MultiKeyManage ipo, Channel channel, ChannelInfoDTO info) throws Exception {
        Method method = ChannelManagementService.class.getDeclaredMethod(
                "getMultiKeyStatus", ChannelIPO.MultiKeyManage.class, Channel.class, ChannelInfoDTO.class);
        method.setAccessible(true);
        try {
            return (Map<String, Object>) method.invoke(service, ipo, channel, info);
        } catch (InvocationTargetException e) {
            // 解包反射目标异常，暴露真实根因
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error er) {
                throw er;
            }
            throw e;
        }
    }
}
