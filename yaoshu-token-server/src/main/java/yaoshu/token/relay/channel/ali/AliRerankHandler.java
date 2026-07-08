package yaoshu.token.relay.channel.ali;

import ai.yue.library.base.convert.Convert;
import yaoshu.token.pojo.dto.RerankRequest;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.AliRerankInput;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.AliRerankParameters;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.AliRerankRequest;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.AliRerankResponse;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.AliUsage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阿里 Rerank 中转处理器  * <p>
 * 负责将 OpenAI Rerank 请求转换为阿里 Rerank 格式，并解析阿里响应为标准 Rerank 响应。
 */
public class AliRerankHandler {    /**
     * OpenAI Rerank 请求 → 阿里 Rerank 请求      */
    public static AliRerankRequest convertRerankRequest(RerankRequest request) {
        Boolean returnDocuments = request.getReturnDocuments();
        if (returnDocuments == null) {
            returnDocuments = true;
        }
        AliRerankRequest aliRequest = new AliRerankRequest();
        aliRequest.setModel(request.getModel());

        AliRerankInput input = new AliRerankInput();
        input.setQuery(request.getQuery());
        input.setDocuments(request.getDocuments());
        aliRequest.setInput(input);

        AliRerankParameters parameters = new AliRerankParameters();
        parameters.setTopN(request.getTopN());
        parameters.setReturnDocuments(returnDocuments);
        aliRequest.setParameters(parameters);

        return aliRequest;
    }

    /**
     * 阿里 Rerank 响应处理      * <p>
     * 解析阿里响应 → 标准 Rerank 响应写入 HttpServletResponse，返回 usage。
     */
    public static Usage rerankHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        AliRerankResponse aliResponse = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), AliRerankResponse.class);

        // 阿里错误响应
        if (aliResponse.getCode() != null && !aliResponse.getCode().isEmpty()) {
            throw new RuntimeException("ali rerank error: " + aliResponse.getMessage()
                    + " (code=" + aliResponse.getCode() + ")");
        }

        // 构建标准 Rerank 响应
        AliUsage aliUsage = aliResponse.getUsage();
        Usage usage = new Usage();
        if (aliUsage != null) {
            usage.setPromptTokens(aliUsage.getTotalTokens());
            usage.setCompletionTokens(0);
            usage.setTotalTokens(aliUsage.getTotalTokens());
        }

        Map<String, Object> rerankResponse = new LinkedHashMap<>();
        if (aliResponse.getOutput() != null) {
            rerankResponse.put("results", aliResponse.getOutput().getResults());
        }
        rerankResponse.put("usage", Map.of(
                "prompt_tokens", usage.getPromptTokens(),
                "completion_tokens", usage.getCompletionTokens(),
                "total_tokens", usage.getTotalTokens()
        ));

        byte[] jsonBytes = Convert.toJSONString(rerankResponse).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setHeader("Content-Type", "application/json");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(jsonBytes);
        response.getOutputStream().flush();

        return usage;
    }
}
