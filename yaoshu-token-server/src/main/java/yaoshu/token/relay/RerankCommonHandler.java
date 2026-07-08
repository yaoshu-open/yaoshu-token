package yaoshu.token.relay;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.RerankResponse;
import yaoshu.token.pojo.dto.RerankResponseResult;
import yaoshu.token.relay.channel.xinference.XinferenceDTOPlaceholder;
import yaoshu.token.relay.common.RelayInfo;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Rerank 通用处理器  * <p>
 * 提供 Rerank API 的通用处理逻辑（供各渠道共用）。
 * 支持 Xinference 渠道的特殊响应格式转换。
 */
@Slf4j
public class RerankCommonHandler {    /** 渠道类型：Xinference */
    private static final int CHANNEL_TYPE_XINFERENCE = 37;

    /**
     * Rerank 通用处理入口      *
     * @param info RelayInfo 上下文
     * @param resp 上游 HTTP 响应
     * @return Usage 计费信息
     */
    public static RelayInfo.Usage rerankHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        log.debug("reranker response body: {}", new String(responseBody));

        RerankResponse jinaResp;

        if (info.getChannelType() == CHANNEL_TYPE_XINFERENCE) {
            // Xinference 特殊处理
            XinferenceDTOPlaceholder.XinRerankResponse xinResp = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), XinferenceDTOPlaceholder.XinRerankResponse.class);

            List<RerankResponseResult> results = new ArrayList<>();
            if (xinResp.getResults() != null) {
                for (XinferenceDTOPlaceholder.XinRerankResponseDocument result : xinResp.getResults()) {
                    RerankResponseResult respResult = new RerankResponseResult();
                    respResult.setIndex(result.getIndex());
                    respResult.setRelevanceScore(result.getRelevanceScore());

                    if (info.isRerankerReturnDocuments()) {
                        Object document = null;
                        if (result.getDocument() != null) {
                            if (result.getDocument() instanceof String doc) {
                                if (doc.isEmpty()) {
                                    document = info.getRerankerDocuments() != null && result.getIndex() < info.getRerankerDocuments().size()
                                            ? info.getRerankerDocuments().get(result.getIndex()) : null;
                                } else {
                                    document = doc;
                                }
                            } else {
                                document = result.getDocument();
                            }
                        }
                        respResult.setDocument(document);
                    }
                    results.add(respResult);
                }
            }

            jinaResp = new RerankResponse();
            jinaResp.setResults(results);
            yaoshu.token.pojo.dto.Usage dtoUsage = new yaoshu.token.pojo.dto.Usage();
            dtoUsage.setPromptTokens(info.getEstimatePromptTokens());
            dtoUsage.setTotalTokens(info.getEstimatePromptTokens());
            jinaResp.setUsage(dtoUsage);
        } else {
            // 标准处理
            jinaResp = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), RerankResponse.class);
            if (jinaResp.getUsage() != null && jinaResp.getUsage().getTotalTokens() > 0) {
                jinaResp.getUsage().setPromptTokens(jinaResp.getUsage().getTotalTokens());
            }
        }

        // 写入响应
        response.setContentType("application/json");
        response.setStatus(200);
        byte[] jsonResp = Convert.toJSONString(jinaResp).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.getOutputStream().write(jsonResp);
        response.getOutputStream().flush();

        // 返回 Usage
        RelayInfo.Usage resultUsage = new RelayInfo.Usage();
        if (jinaResp.getUsage() != null) {
            resultUsage.setPromptTokens(jinaResp.getUsage().getPromptTokens());
            resultUsage.setCompletionTokens(jinaResp.getUsage().getCompletionTokens());
            resultUsage.setTotalTokens(jinaResp.getUsage().getTotalTokens());
        }
        return resultUsage;
    }
}
