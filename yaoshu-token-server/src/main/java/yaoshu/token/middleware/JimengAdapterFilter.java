package yaoshu.token.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 即梦适配器中间件  * <p>
 * 将即梦（Jimeng）官方 API 的请求格式适配为内部标准视频生成格式。
 * <p>
 * 流程：
 * <ol>
 * <li>从 query 获取 Action 参数（必填）</li>
 * <li>解析原始请求体，提取 req_key（作为 model）和 prompt</li>
 * <li>构建统一请求 {model, prompt, metadata}，覆写请求体</li>
 * <li>将请求路径改写为 /v1/video/generations</li>
 * <li>Action=CVSync2AsyncGetResult 时改写为 GET /v1/video/generations/{taskId}</li>
 * </ol>
 */
@Slf4j
public class JimengAdapterFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. 检查 Action 参数
        String action = request.getParameter("Action");
        if (action == null || action.isEmpty()) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_BAD_REQUEST, "Action query parameter is required");
            return;
        }

        // 2. 读取并解析原始请求体
        String bodyStr;
        try {
            bodyStr = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        com.alibaba.fastjson2.JSONObject originalReq;
        try {
            originalReq = ai.yue.library.base.convert.Convert.toJSONObject(bodyStr);
        } catch (Exception e) {
            MiddlewareUtils.abortWithOpenAiMessage(response,
                    HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        // 3. 提取字段并构建统一请求
        String model = originalReq.getString("req_key");
        String prompt = originalReq.getString("prompt");

        com.alibaba.fastjson2.JSONObject unifiedReq = new com.alibaba.fastjson2.JSONObject();
        unifiedReq.put("model", model);
        unifiedReq.put("prompt", prompt);
        unifiedReq.put("metadata", originalReq);

        byte[] jsonData = unifiedReq.toJSONString().getBytes(StandardCharsets.UTF_8);

        // 4. 设置请求属性（供后续 Handler 使用）
        request.setAttribute("_request_body", jsonData);

        // 5. 判断 action 类型
        String taskAction = "text_generate";
        Object image = originalReq.get("image");
        if (image != null && !image.toString().isEmpty()) {
            taskAction = "image_to_video";
        }
        request.setAttribute("action", taskAction);

        // 6. CVSync2AsyncGetResult 改写为查询模式
        if ("CVSync2AsyncGetResult".equals(action)) {
            String taskId = originalReq.getString("task_id");
            if (taskId == null || taskId.isEmpty()) {
                MiddlewareUtils.abortWithOpenAiMessage(response,
                        HttpServletResponse.SC_BAD_REQUEST, "task_id is required for CVSync2AsyncGetResult");
                return;
            }
            request.setAttribute("task_id", taskId);
            request.setAttribute("relay_mode", "video_fetch_by_id");
        }

        // 7. 设置改写后的路径标记（后续 Handler 根据 action/task_id 决定路由）
        request.setAttribute("_rewritten_path", "/v1/video/generations");

        chain.doFilter(request, response);
    }
}
