package yaoshu.token.relay.channel.cohere;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;
import java.util.Map;

/**
 * Cohere 响应处理（流式/非流式/Rerank）  */
@Slf4j
public final class CohereRelayHandler {    private CohereRelayHandler() {}

    /** Cohere 流式处理器：SSE 透传 + usage 提取 + [DONE] */
    public static Usage cohereStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || inputStream == null) return new Usage();

        Usage[] usage = {new Usage()};
        StreamScanner.scan(inputStream, info, data -> {
            if (data.contains("usage")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = Convert.toJSONObject(data);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> um = (Map<String, Object>) map.get("usage");
                    if (um != null) usage[0] = Convert.toJavaBean(um, Usage.class);
                } catch (Exception ignored) {}
            }
            RelayCommonHelper.stringData(response, data);
            return true;
        }, response);
        RelayCommonHelper.done(response);
        return usage[0];
    }

    /** Cohere 非流式处理器：透传完整响应 + 解析 usage */
    public static Usage cohereHandler(RelayInfo info, byte[] responseBody) throws Exception {
        HttpServletResponse response = info.getResponse();
        if (response == null || responseBody == null) return new Usage();

        Usage usage = parseUsage(responseBody);
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return usage;
    }

    /** Cohere Rerank 处理器：透传完整响应 + 解析 usage */
    public static Usage cohereRerankHandler(byte[] responseBody, HttpServletResponse response) throws Exception {
        if (response == null || responseBody == null) return new Usage();
        Usage usage = parseUsage(responseBody);
        response.getOutputStream().write(responseBody);
        response.getOutputStream().flush();
        return usage;
    }

    private static Usage parseUsage(byte[] body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = Convert.toJSONObject(body);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) map.get("meta");
            if (meta != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> billed = (Map<String, Object>) meta.get("billed_units");
                if (billed != null) {
                    Usage u = new Usage();
                    if (billed.get("input_tokens") instanceof Number) u.setPromptTokens(((Number) billed.get("input_tokens")).intValue());
                    if (billed.get("output_tokens") instanceof Number) u.setCompletionTokens(((Number) billed.get("output_tokens")).intValue());
                    u.setTotalTokens(u.getPromptTokens() + u.getCompletionTokens());
                    return u;
                }
            }
        } catch (Exception e) { log.debug("Cohere usage parse: {}", e.getMessage()); }
        return new Usage();
    }
}
