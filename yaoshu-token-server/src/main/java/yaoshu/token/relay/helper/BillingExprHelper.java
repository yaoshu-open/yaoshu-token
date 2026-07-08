package yaoshu.token.relay.helper;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.billingexpr.RequestInput;
import yaoshu.token.relay.common.RelayInfo;
import ai.yue.library.base.convert.Convert;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 分层计费表达式辅助  * <p>
 * 构建计费表达式求值器的 RequestInput（headers + body）。
 * RequestInput 类型定义在 billingexpr 包中。
 */
@Slf4j
public final class BillingExprHelper {

    private BillingExprHelper() {
    }

    /**
     * 从入站请求解析计费表达式输入      */
    public static RequestInput resolveIncomingBillingExprRequestInput(HttpServletRequest request, RelayInfo info) throws Exception {
        if (info != null && info.getBillingRequestInput() != null) {
            // 使用已有的 BillingRequestInput（克隆 + 合并 headers）
            RequestInput src = info.getBillingRequestInput();
            RequestInput input = cloneRequestInput(src);
            Map<String, String> merged = cloneStringMap(info.getClientHeaders());
            if (input.getHeaders() != null) {
                merged.putAll(input.getHeaders());
            }
            input.setHeaders(merged);
            return input;
        }

        RequestInput input = new RequestInput();
        if (info != null && info.getClientHeaders() != null) {
            input.setHeaders(cloneStringMap(info.getClientHeaders()));
        }

        // 读取请求体
        byte[] bodyBytes = readIncomingBillingExprBody(request);
        input.setBody(bodyBytes);
        return input;
    }

    /**
     * 从 Request 对象构建计费表达式输入      */
    public static RequestInput buildBillingExprRequestInputFromRequest(Object request, Map<String, String> headers) throws Exception {
        RequestInput input = new RequestInput();
        input.setHeaders(cloneStringMap(headers));

        if (request == null) {
            return input;
        }

        byte[] bodyBytes = Convert.toJSONString(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        input.setBody(bodyBytes);
        return input;
    }

    /** 读取入站请求体（仅 JSON content type） */
    private static byte[] readIncomingBillingExprBody(HttpServletRequest request) throws Exception {
        if (request == null) return null;
        String contentType = request.getContentType();
        if (contentType == null || !isJSONContentType(contentType)) {
            return null;
        }
        return request.getInputStream().readAllBytes();
    }

    /** 克隆 RequestInput */
    private static RequestInput cloneRequestInput(RequestInput src) {
        RequestInput input = new RequestInput();
        input.setHeaders(cloneStringMap(src.getHeaders()));
        if (src.getBody() != null && src.getBody().length > 0) {
            input.setBody(src.getBody().clone());
        }
        return input;
    }

    /** 检查是否为 JSON content type */
    private static boolean isJSONContentType(String contentType) {
        if (contentType == null) return false;
        return contentType.trim().toLowerCase().startsWith("application/json");
    }

    /** 克隆 String Map */
    private static Map<String, String> cloneStringMap(Map<String, String> src) {
        if (src == null || src.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> dst = new HashMap<>(src.size());
        for (Map.Entry<String, String> e : src.entrySet()) {
            if (e.getKey() != null && !e.getKey().trim().isEmpty()) {
                dst.put(e.getKey(), e.getValue());
            }
        }
        return dst;
    }
}
