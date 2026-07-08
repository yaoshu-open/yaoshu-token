package yaoshu.token.relay.channel.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * MiniMax 图片生成处理器  * <p>
 * 解析 MiniMax 图片响应 → OpenAI 图片响应格式。
 */
@Slf4j
public class MiniMaxImageHandler {    /**
     * MiniMax 图片响应处理
     */
    @SuppressWarnings("unchecked")
    public static Usage miniMaxImageHandler(RelayInfo info, HttpResponse<InputStream> resp) throws Exception {
        HttpServletResponse response = info.getResponse();
        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        Map<String, Object> miniMaxResp = Convert.toJSONObject(responseBody);

        // 错误检查
        Object baseResp = miniMaxResp.get("base_resp");
        if (baseResp instanceof Map<?, ?> baseMap) {
            Object statusCode = baseMap.get("status_code");
            Object statusMsg = baseMap.get("status_msg");
            if (statusCode instanceof Number n && n.intValue() != 0) {
                throw new RuntimeException("minimax image error: " + statusMsg + " (code=" + n + ")");
            }
        }

        // 构建 OpenAI 图片响应
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("created", System.currentTimeMillis() / 1000);

        List<Map<String, Object>> dataList = new ArrayList<>();
        Object dataObj = miniMaxResp.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            // image_urls
            Object urls = data.get("image_urls");
            if (urls instanceof List<?> urlList) {
                for (Object url : urlList) {
                    Map<String, Object> imageData = new LinkedHashMap<>();
                    imageData.put("url", url.toString());
                    dataList.add(imageData);
                }
            }
            // image_base64
            Object base64List = data.get("image_base64");
            if (base64List instanceof List<?> b64List) {
                for (Object b64 : b64List) {
                    Map<String, Object> imageData = new LinkedHashMap<>();
                    imageData.put("b64_json", b64.toString());
                    dataList.add(imageData);
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
}
