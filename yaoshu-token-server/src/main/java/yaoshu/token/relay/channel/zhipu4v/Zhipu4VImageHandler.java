package yaoshu.token.relay.channel.zhipu4v;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 智谱 GLM-4V 图片生成处理器  * <p>
 * 解析智谱图片响应 → OpenAI 图片响应格式。
 */
@Slf4j
public class Zhipu4VImageHandler {    /**
     * 智谱图片响应处理      */
    @SuppressWarnings("unchecked")
    public static Usage zhipu4vImageHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        Map<String, Object> zhipuResp = Convert.toJSONObject(responseBody);

        // 错误检查
        Object errorObj = zhipuResp.get("error");
        if (errorObj instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            if (message != null && !message.toString().isEmpty()) {
                throw new RuntimeException("zhipu image error: " + message);
            }
        }

        // 构建 OpenAI 图片响应
        Map<String, Object> payload = new LinkedHashMap<>();
        Object created = zhipuResp.get("created");
        if (created instanceof Number n && n.longValue() != 0) {
            payload.put("created", n.longValue());
        } else {
            payload.put("created", System.currentTimeMillis() / 1000);
        }

        List<Map<String, Object>> dataList = new ArrayList<>();
        Object dataObj = zhipuResp.get("data");
        if (dataObj instanceof List<?> dataArray) {
            for (Object item : dataArray) {
                if (item instanceof Map<?, ?> data) {
                    String url = getStr(data, "url");
                    if (url == null || url.isEmpty()) url = getStr(data, "image_url");
                    String b64 = getStr(data, "b64_json");
                    if (b64 == null || b64.isEmpty()) b64 = getStr(data, "b64_image");

                    if (url != null && !url.isEmpty() && (b64 == null || b64.isEmpty())) {
                        // 有 URL 但无 base64 — 需下载转换（简化：直接用 URL）
                        Map<String, Object> imageData = new LinkedHashMap<>();
                        imageData.put("url", url);
                        dataList.add(imageData);
                    } else if (b64 != null && !b64.isEmpty()) {
                        Map<String, Object> imageData = new LinkedHashMap<>();
                        imageData.put("b64_json", b64);
                        dataList.add(imageData);
                    }
                }
            }
        }
        payload.put("data", dataList);

        byte[] jsonResp = Convert.toJSONString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setContentType("application/json");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(jsonResp);
        response.getOutputStream().flush();

        return new Usage();
    }

    private static String getStr(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
