package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.pojo.entity.Option;
import yaoshu.token.pojo.ipo.OptionIPO;
import yaoshu.token.service.ChannelAffinityService;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.WaffoPancakeService;
import yaoshu.token.service.payment.waffopancake.client.WaffoPancakePairException;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OptionController 白盒测试，覆盖非静态 Service 方法 + channel_affinity 静态分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OptionController — 系统设置 R.success / ResultException 分支")
class OptionControllerTest {

    @Mock
    private OptionService optionService;

    @Mock
    private yaoshu.token.service.WaffoPancakeService waffoPancakeService;

    @InjectMocks
    private OptionController controller;

    @Test
    @DisplayName("getAll 返回过滤敏感 key 后的设置 + CompletionRatioMeta（R.success）")
    void getAllSuccess() {
        Option normal = new Option();
        normal.setKey("SystemName");
        normal.setValue("yaoshu");
        // 敏感 key 应被过滤
        Option secret = new Option();
        secret.setKey("StripeSecret");
        secret.setValue("sk_xxx");
        when(optionService.getAll()).thenReturn(List.of(normal, secret));

        Result<?> result = controller.getAll();

        assertTrue(result.isFlag());
        assertEquals(200, result.getCode());
        List<?> data = (List<?>) result.getData();
        // 过滤敏感 + 追加 meta → 2 条
        assertEquals(2, data.size());
    }

    @Test
    @DisplayName("update 保存设置（R.success）")
    void updateSuccess() {
        OptionIPO.Update ipo = new OptionIPO.Update();
        ipo.setKey("SystemName");
        ipo.setValue("new-name");

        Result<?> result = controller.update(ipo);

        assertTrue(result.isFlag());
        verify(optionService).saveOrUpdate("SystemName", "new-name");
    }

    @Test
    @DisplayName("confirmPaymentCompliance 未确认抛 ResultException（code=600）")
    void confirmPaymentComplianceNotConfirmed() {
        OptionIPO.PaymentCompliance ipo = new OptionIPO.PaymentCompliance();
        ipo.setConfirmed(false);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.confirmPaymentCompliance(mock(HttpServletRequest.class), ipo));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("confirmPaymentCompliance 已确认成功，调用 saveOrUpdateRaw 绕过校验（R.success）")
    void confirmPaymentComplianceSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("id")).thenReturn(7);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        OptionIPO.PaymentCompliance ipo = new OptionIPO.PaymentCompliance();
        ipo.setConfirmed(true);

        Result<?> result = controller.confirmPaymentCompliance(request, ipo);

        assertTrue(result.isFlag());
        assertNotNull(result.getData());
        // 验证合规字段通过 saveOrUpdateRaw 写入（绕过通用校验），不经过 saveOrUpdate
        verify(optionService).saveOrUpdateRaw("payment_setting.compliance_confirmed", "true");
        verify(optionService, never()).saveOrUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("resetModelRatio 重置为默认值（R.success）")
    void resetModelRatioSuccess() {
        Result<?> result = controller.resetModelRatio();

        assertTrue(result.isFlag());
        verify(optionService).saveOrUpdate("ModelRatio", "{}");
    }

    @Test
    @DisplayName("createWaffoPancakePair 凭证未配置抛 ResultException（code=600）")
    void pairNoCredsThrows() {
        when(optionService.getValue("WaffoPancakeMerchantID")).thenReturn(null);
        when(optionService.getValue("WaffoPancakePrivateKey")).thenReturn(null);

        ResultException ex = assertThrows(ResultException.class,
                () -> controller.createWaffoPancakePair(new OptionIPO.WaffoPair()));
        assertEquals(600, ex.getResult().getCode());
    }

    @Test
    @DisplayName("createWaffoPancakePair orphan store 返回 R.errorPrompt（flag=false code=600）")
    void pairOrphanStoreReturnsErrorPrompt() {
        OptionIPO.WaffoPair ipo = new OptionIPO.WaffoPair();
        ipo.setMerchantId("m");
        ipo.setPrivateKey("k");
        WaffoPancakePairResult partial = mock(WaffoPancakePairResult.class);
        when(partial.getStoreId()).thenReturn("s1");
        when(partial.getStoreName()).thenReturn("store");
        when(waffoPancakeService.createPrimaryPair("m", "k", ""))
                .thenThrow(new WaffoPancakePairException("product失败", partial));

        Result<?> result = controller.createWaffoPancakePair(ipo);

        // orphan 对齐 Go message:"error" → R.errorPrompt("error", data) flag=false
        assertFalse(result.isFlag());
        assertEquals(600, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("createWaffoPancakePair 成功返回 store/product（R.success）")
    void pairSuccess() {
        OptionIPO.WaffoPair ipo = new OptionIPO.WaffoPair();
        ipo.setMerchantId("m");
        ipo.setPrivateKey("k");
        WaffoPancakePairResult pairResult = mock(WaffoPancakePairResult.class);
        when(pairResult.getStoreId()).thenReturn("s1");
        when(pairResult.getStoreName()).thenReturn("store");
        when(pairResult.getProductId()).thenReturn("p1");
        when(pairResult.getProductName()).thenReturn("prod");
        when(waffoPancakeService.createPrimaryPair("m", "k", "")).thenReturn(pairResult);

        Result<?> result = controller.createWaffoPancakePair(ipo);

        assertTrue(result.isFlag());
    }

    @Test
    @DisplayName("clearChannelAffinityCache all=true 清空全部（R.success，mockStatic）")
    void clearChannelAffinityCacheAll() {
        try (MockedStatic<ChannelAffinityService> mocked = mockStatic(ChannelAffinityService.class)) {
            mocked.when(ChannelAffinityService::clearAllAffinityCache).thenReturn(5);

            Result<?> result = controller.clearChannelAffinityCache("true", null);

            assertTrue(result.isFlag());
        }
    }

    @Test
    @DisplayName("clearChannelAffinityCache 缺参数抛 ResultException（code=600）")
    void clearChannelAffinityCacheMissingParamThrows() {
        ResultException ex = assertThrows(ResultException.class,
                () -> controller.clearChannelAffinityCache(null, null));
        assertEquals(600, ex.getResult().getCode());
    }
}
