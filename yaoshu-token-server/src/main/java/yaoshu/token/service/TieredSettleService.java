package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.billingexpr.BillingExprEngine;
import yaoshu.token.billingexpr.BillingSnapshot;
import yaoshu.token.billingexpr.RequestInput;
import yaoshu.token.billingexpr.TokenParams;
import yaoshu.token.billingexpr.TieredResult;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

import java.util.Set;

/**
 * 分层结算服务  * <p>
 * 当计费模式为 tiered_expr 时，用冻结的 BillingSnapshot 重新执行表达式计算实际配额。
 * 核心流程：BuildTieredTokenParams（归一化 token）→ TryTieredSettle（执行表达式结算）。
 */
@Slf4j
@Service
public class TieredSettleService {

    /**
     * 从 Usage 构造 TokenParams，归一化 P 和 C 为"未被表达式单独定价的 token"。
     * <p>
     * GPT 格式 API 的 prompt_tokens/completion_tokens 是总和（含缓存/图片/音频），
     * Claude 格式 API 的 input_tokens 是纯文本。当表达式引用了子类别变量时，
     * 需要从 P/C 中减去对应子类别 token，避免重复计费。
     *
     * @param usage                 上游返回的 token 使用量
     * @param isClaudeUsageSemantic 是否为 Claude 语义（input_tokens 是纯文本）
     * @param usedVars              表达式实际引用的变量集合（来自 AST introspection）
     * @return 归一化后的 TokenParams
     */
    public TokenParams buildTieredTokenParams(Usage usage, boolean isClaudeUsageSemantic, Set<String> usedVars) {
        double p = usage.getPromptTokens();
        double c = usage.getCompletionTokens();
        double cr = usage.getPromptTokensDetails().getCachedTokens();
        double cc5m = usage.getPromptTokensDetails().getCachedCreationTokens();
        double cc1h = 0;

        if ("anthropic".equals(usage.getUsageSemantic())) {
            cc1h = usage.getClaudeCacheCreation1hTokens();
            cc5m = usage.getClaudeCacheCreation5mTokens();
        }

        double img = usage.getPromptTokensDetails().getImageTokens();
        double ai = usage.getPromptTokensDetails().getAudioTokens();
        double imgO = usage.getCompletionTokenDetails().getImageTokens();
        double ao = usage.getCompletionTokenDetails().getAudioTokens();

        // len = 分层条件判断用的输入上下文总长度
        // 非 Claude: prompt_tokens 已包含一切
        // Claude: input_tokens 是纯文本，需加回缓存读取 + 缓存创建
        double inputLen = p;
        if (isClaudeUsageSemantic) {
            inputLen = p + cr + cc5m + cc1h;
        }

        // 非 Claude 格式时，根据表达式使用的变量从 P/C 中减去对应子类别
        if (!isClaudeUsageSemantic && usedVars != null) {
            if (usedVars.contains("cr")) {
                p -= cr;
            }
            if (usedVars.contains("cc")) {
                p -= cc5m;
            }
            if (usedVars.contains("cc1h")) {
                p -= cc1h;
            }
            if (usedVars.contains("img")) {
                p -= img;
            }
            if (usedVars.contains("ai")) {
                p -= ai;
            }
            if (usedVars.contains("img_o")) {
                c -= imgO;
            }
            if (usedVars.contains("ao")) {
                c -= ao;
            }
        }

        if (p < 0) p = 0;
        if (c < 0) c = 0;

        return TokenParams.builder()
                .p(p)
                .c(c)
                .len(inputLen)
                .cr(cr)
                .cc(cc5m)
                .cc1h(cc1h)
                .img(img)
                .imgO(imgO)
                .ai(ai)
                .ao(ao)
                .build();
    }

    /**
     * 尝试分层结算。      * <p>
     * 检查请求是否使用 tiered_expr 计费，如果是则用冻结的 BillingSnapshot
     * 重新执行表达式计算实际配额。
     *
     * @param relayInfo 中转上下文（含 BillingSnapshot）
     * @param params    归一化后的 token 参数
     * @return TryTieredSettleResult：ok=true 时为分层结算结果，ok=false 表示不适用
     */
    public TryTieredSettleResult tryTieredSettle(RelayInfo relayInfo, TokenParams params) {
        BillingSnapshot snap = relayInfo.getTieredBillingSnapshot();
        if (snap == null || !"tiered_expr".equals(snap.getBillingMode())) {
            return TryTieredSettleResult.notApplicable();
        }

        RequestInput requestInput = relayInfo.getBillingRequestInput();
        if (requestInput == null) {
            requestInput = new RequestInput();
        }

        try {
            TieredResult tr = BillingExprEngine.computeTieredQuotaWithRequest(snap, params, requestInput);
            return TryTieredSettleResult.of(tr.getActualQuotaAfterGroup(), tr);
        } catch (Exception e) {
            // 表达式执行失败时回退到预扣额度
            log.warn("分层结算表达式执行失败，回退到预扣额度: {}", e.getMessage());
            int quota = relayInfo.getFinalPreConsumedQuota();
            if (quota <= 0) {
                quota = snap.getEstimatedQuotaAfterGroup();
            }
            return TryTieredSettleResult.of(quota, null);
        }
    }

    /**
     * TryTieredSettle 的返回值。      */
    public static class TryTieredSettleResult {
        private final boolean ok;
        private final int quota;
        private final TieredResult result;

        private TryTieredSettleResult(boolean ok, int quota, TieredResult result) {
            this.ok = ok;
            this.quota = quota;
            this.result = result;
        }

        public static TryTieredSettleResult notApplicable() {
            return new TryTieredSettleResult(false, 0, null);
        }

        public static TryTieredSettleResult of(int quota, TieredResult result) {
            return new TryTieredSettleResult(true, quota, result);
        }

        public boolean isOk() { return ok; }
        public int getQuota() { return quota; }
        public TieredResult getResult() { return result; }
    }
}
