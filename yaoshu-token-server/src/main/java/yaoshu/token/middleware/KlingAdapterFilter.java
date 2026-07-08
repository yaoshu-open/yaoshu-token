package yaoshu.token.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Kling 适配器中间件  * <p>
 * 将可灵（Kling）API 的请求格式适配为内部标准视频生成格式。
 * <p>
 * 流程：
 * <ol>
 * <li>解析原始请求体，提取 model_name/model（作为 model）和 prompt</li>
 * <li>构建统一请求 {model, prompt, metadata}，覆写请求体</li>
 * <li>将请求路径改写为 /v1/video/generations</li>
 * </ol>
 */
@Slf4j
public class KlingAdapterFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. 读取并解析原始请求体
        String bodyStr;
        try {
            bodyStr = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Go 源码此处也是静默放行（c.Next()）
            chain.doFilter(request, response);
            return;
        }

        com.alibaba.fastjson2.JSONObject originalReq;
        try {
            originalReq = ai.yue.library.base.convert.Convert.toJSONObject(bodyStr);
        } catch (Exception e) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 提取 model（支持 model_name 和 model 两种字段名）
        String model = originalReq.getString("model_name");
        if (model == null || model.isEmpty()) {
            model = originalReq.getString("model");
        }
        String prompt = originalReq.getString("prompt");

        // 3. 构建统一请求
        com.alibaba.fastjson2.JSONObject unifiedReq = new com.alibaba.fastjson2.JSONObject();
        unifiedReq.put("model", model);
        unifiedReq.put("prompt", prompt);
        unifiedReq.put("metadata", originalReq);

        byte[] jsonData;
        try {
            jsonData = unifiedReq.toJSONString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            chain.doFilter(request, response);
            return;
        }

        // 4. 设置请求属性
        request.setAttribute("_request_body", jsonData);

        // 5. 判断 action 类型（image 为空时为文生视频）
        String taskAction = "text_generate";
        Object image = originalReq.get("image");
        if (image != null && !image.toString().isEmpty()) {
            taskAction = "image_to_video";
        }
        request.setAttribute("action", taskAction);

        // 6. 设置改写后的路径标记
        request.setAttribute("_rewritten_path", "/v1/video/generations");

        chain.doFilter(request, response);
    }
}
