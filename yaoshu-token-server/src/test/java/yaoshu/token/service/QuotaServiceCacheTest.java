package yaoshu.token.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import yaoshu.token.pojo.dto.GroupRatioInfo;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuotaService 缓存感知计价白盒测试
 * <p>
 * 验证缓存命中时 cacheRatio 折扣正确应用，无缓存时全价计价。
 */
@DisplayName("QuotaService — 缓存感知计价")
class QuotaServiceCacheTest {

    private QuotaService createQuotaService() {
        PreConsumeQuotaService mockPreConsume = org.mockito.Mockito.mock(PreConsumeQuotaService.class);
        TextQuotaService textQuotaService = new TextQuotaService();
        return new QuotaService(mockPreConsume, textQuotaService);
    }

    private RelayInfo createRelayInfo(double modelRatio, double completionRatio,
                                       double cacheRatio, double groupRatio) {
        RelayInfo info = new RelayInfo();
        info.setOriginModelName("test-model");

        PriceData priceData = new PriceData();
        priceData.setModelRatio(modelRatio);
        priceData.setCompletionRatio(completionRatio);
        priceData.setCacheRatio(cacheRatio);
        priceData.setUsePrice(false);

        GroupRatioInfo groupRatioInfo = new GroupRatioInfo(groupRatio, 0, false);
        priceData.setGroupRatioInfo(groupRatioInfo);

        info.setPriceData(priceData);
        return info;
    }

    private Usage createUsage(int promptTokens, int completionTokens, int cachedTokens) {
        Usage usage = new Usage();
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(promptTokens + completionTokens);
        if (cachedTokens > 0) {
            usage.setPromptTokensDetails(new Usage.PromptTokensDetails());
            usage.getPromptTokensDetails().setCachedTokens(cachedTokens);
        }
        return usage;
    }

    @Test
    @DisplayName("无缓存命中：全价计价（与修复前行为一致）")
    void noCacheHit_fullPrice() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.5, 1.0);

        // promptTokens=1000, completionTokens=500, cachedTokens=0
        Usage usage = createUsage(1000, 500, 0);
        int quota = quotaService.calculateTextQuotaWithCache(info, usage);

        // 期望：(1000 + 500*2.0) * 2.5 * 1.0 = 5000
        assertEquals(5000, quota);
    }

    @Test
    @DisplayName("缓存命中：缓存部分按 cacheRatio 折扣计价")
    void cacheHit_discountedPrice() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.5, 1.0);

        // promptTokens=1000（含 800 缓存命中）, completionTokens=500
        Usage usage = createUsage(1000, 500, 800);
        int quota = quotaService.calculateTextQuotaWithCache(info, usage);

        // 期望：非缓存输入=200, 缓存输入=800*0.5=400, 输出=500*2.0=1000
        // total = (200 + 400 + 1000) * 2.5 * 1.0 = 4000
        assertEquals(4000, quota);
    }

    @Test
    @DisplayName("缓存命中率 100%：全部输入按 cacheRatio 折扣")
    void allCacheHit_allDiscounted() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.1, 1.0);

        // promptTokens=1000（全部是缓存命中）, completionTokens=0
        Usage usage = createUsage(1000, 0, 1000);
        int quota = quotaService.calculateTextQuotaWithCache(info, usage);

        // 期望：非缓存输入=0, 缓存输入=1000*0.1=100, 输出=0
        // total = (0 + 100 + 0) * 2.5 * 1.0 = 250
        assertEquals(250, quota);
    }

    @Test
    @DisplayName("缓存命中 vs 无缓存：缓存命中计价更低")
    void cacheHit_cheaperThanNoCache() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.5, 1.0);

        Usage noCacheUsage = createUsage(1000, 500, 0);
        Usage withCacheUsage = createUsage(1000, 500, 800);

        int noCacheQuota = quotaService.calculateTextQuotaWithCache(info, noCacheUsage);
        int withCacheQuota = quotaService.calculateTextQuotaWithCache(info, withCacheUsage);

        // 缓存命中时用户付费应更低
        assertTrue(withCacheQuota < noCacheQuota,
                "缓存命中计价(" + withCacheQuota + ")应低于无缓存计价(" + noCacheQuota + ")");
    }

    @Test
    @DisplayName("cacheRatio=0：缓存命中部分免费")
    void cacheRatioZero_cacheFree() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.0, 1.0);

        // promptTokens=1000（含 800 缓存命中）, completionTokens=500
        Usage usage = createUsage(1000, 500, 800);
        int quota = quotaService.calculateTextQuotaWithCache(info, usage);

        // 期望：非缓存输入=200, 缓存输入=800*0=0, 输出=500*2.0=1000
        // total = (200 + 0 + 1000) * 2.5 * 1.0 = 3000
        assertEquals(3000, quota);
    }

    @Test
    @DisplayName("usage=null：返回 0")
    void nullUsage_returnZero() {
        QuotaService quotaService = createQuotaService();
        RelayInfo info = createRelayInfo(2.5, 2.0, 0.5, 1.0);

        assertEquals(0, quotaService.calculateTextQuotaWithCache(info, null));
    }

    // ======================== OpenRouter 缓存反推（calcOpenRouterCacheCreateTokens） ========================

    /**
     * 通过反射调用 private 方法 calcOpenRouterCacheCreateTokens
     */
    private int invokeCalcOpenRouterCacheCreateTokens(QuotaService service, Usage usage, PriceData priceData) throws Exception {
        java.lang.reflect.Method method = QuotaService.class.getDeclaredMethod(
                "calcOpenRouterCacheCreateTokens", Usage.class, PriceData.class);
        method.setAccessible(true);
        return (int) method.invoke(service, usage, priceData);
    }

    private PriceData createOpenRouterPriceData() {
        PriceData priceData = new PriceData();
        priceData.setModelRatio(2.0);
        priceData.setCacheCreationRatio(1.25);
        priceData.setCacheRatio(0.1);
        priceData.setCompletionRatio(0.5);
        priceData.setUsePrice(false);
        return priceData;
    }

    @Test
    @DisplayName("OpenRouter 缓存反推：正常场景反推 cacheCreationTokens")
    void openRouterCacheCreate_normalReverseCalc() throws Exception {
        QuotaService quotaService = createQuotaService();
        PriceData priceData = createOpenRouterPriceData();

        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setPromptTokensDetails(new Usage.PromptTokensDetails());
        usage.getPromptTokensDetails().setCachedTokens(20);
        // quotaPrice=2/500000=0.000004, denominator=0.000005-0.000004=0.000001
        // cost 反推公式使结果=30:
        // cost = 100*0.000004 - 20*(0.000004-0.0000004) + 50*0.000002 + 30*0.000001 = 0.000458
        usage.setCost(0.000458);

        int result = invokeCalcOpenRouterCacheCreateTokens(quotaService, usage, priceData);
        assertEquals(30, result, "正常反推应返回 30 个 cacheCreationTokens");
    }

    @Test
    @DisplayName("OpenRouter 缓存反推：cost 不足时返回负数（调用方负数保护过滤）")
    void openRouterCacheCreate_negativeWhenCostInsufficient() throws Exception {
        QuotaService quotaService = createQuotaService();
        PriceData priceData = createOpenRouterPriceData();

        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setPromptTokensDetails(new Usage.PromptTokensDetails());
        usage.getPromptTokensDetails().setCachedTokens(20);
        usage.setCost(0.0001); // 远低于正常值，反推结果为负

        int result = invokeCalcOpenRouterCacheCreateTokens(quotaService, usage, priceData);
        assertTrue(result < 0,
                "cost 不足时反推应返回负数（实际: " + result + "），调用方 if(maybe>=0) 负数保护过滤");
    }

    @Test
    @DisplayName("OpenRouter 缓存反推：cacheCreationRatio=1 时返回 0（无缓存创建溢价）")
    void openRouterCacheCreate_noCacheCreationPremium() throws Exception {
        QuotaService quotaService = createQuotaService();
        PriceData priceData = createOpenRouterPriceData();
        priceData.setCacheCreationRatio(1.0); // 无溢价

        Usage usage = new Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(50);
        usage.setCost(0.001);

        int result = invokeCalcOpenRouterCacheCreateTokens(quotaService, usage, priceData);
        assertEquals(0, result, "cacheCreationRatio=1 时应直接返回 0，无需反推");
    }
}
