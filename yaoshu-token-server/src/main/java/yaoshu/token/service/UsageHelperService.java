package yaoshu.token.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.Usage;

/**
 * Usage 辅助服务  */
@Slf4j
public final class UsageHelperService {

    private UsageHelperService() {
    }

    /**
     * 根据响应文本构建 Usage      * <p>
     * 设置本地计 token 标记，使用 Token 估算器计算 completion tokens。
     *
     * @param request     HTTP 请求
     * @param responseText 响应文本
     * @param modelName   模型名称
     * @param promptTokens 提示 token 数
     * @return Usage 对象
     */
    public static Usage responseText2Usage(HttpServletRequest request, String responseText,
                                            String modelName, int promptTokens) {
        request.setAttribute(ContextKeyConstants.LOCAL_COUNT_TOKENS, true);
        Usage usage = new Usage();
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(TokenEstimatorService.estimateTokenByModel(modelName, responseText));
        usage.setTotalTokens(promptTokens + usage.getCompletionTokens());
        return usage;
    }

    /**
     * 校验 Usage 是否有效      */
    public static boolean validUsage(Usage usage) {
        if (usage == null) return false;
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        return (prompt != null && prompt != 0) || (completion != null && completion != 0);
    }
}
