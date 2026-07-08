package yaoshu.token.relay.channel.task.suno;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Suno Task 适配器  */
@Slf4j
public class TaskSunoAdaptor implements IAdaptor.ITaskAdaptor {

    private String baseURL;
    private String apiKey;

    @Override
    public void init(RelayInfo info) {
        this.baseURL = info.getChannelBaseUrl();
        this.apiKey = info.getApiKey();
    }

    @Override
    public Object validateRequestAndSetAction(RelayInfo info) {
        SunoSubmitReq req = getSunoRequest(info);
        String action = resolveAction(info, req);
        if (TaskConstants.SUNO_ACTION_MUSIC.equals(action)) {
            if (req.getMv() == null || req.getMv().isEmpty()) {
                req.setMv("chirp-v3-0");
            }
        } else if (TaskConstants.SUNO_ACTION_LYRICS.equals(action)) {
            if (req.getPrompt() == null || req.getPrompt().isEmpty()) {
                return Map.of("code", "invalid_request", "message", "prompt_empty", "statusCode", 400, "localError", true);
            }
        } else {
            return Map.of("code", "invalid_request", "message", "invalid_action", "statusCode", 400, "localError", true);
        }
        info.setTaskAction(action);
        info.setRequest(req);
        return null;
    }

    @Override
    public Map<String, Double> estimateBilling(RelayInfo info) {
        return null;
    }

    @Override
    public Map<String, Double> adjustBillingOnSubmit(RelayInfo info, byte[] taskData) {
        return null;
    }

    @Override
    public int adjustBillingOnComplete(Object task, Object taskResult) {
        return 0;
    }

    @Override
    public String buildRequestURL(RelayInfo info) {
        return baseURL + "/suno/submit/" + info.getTaskAction();
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", resolveClientHeader(info, "Content-Type", "application/json"));
        String accept = resolveClientHeader(info, "Accept", null);
        if (accept != null && !accept.isEmpty()) headers.put("Accept", accept);
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) {
        SunoSubmitReq req = getSunoRequest(info);
        return new ByteArrayInputStream(Convert.toJSONString(req).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) {
        byte[] responseBody = responseBodyBytes(resp);
        TaskResponse sunoResponse = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), TaskResponse.class);
        if (!sunoResponse.isSuccess()) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of(
                    "code", safe(sunoResponse.getCode()),
                    "message", safe(sunoResponse.getMessage()),
                    "statusCode", 500,
                    "localError", false
            ));
        }
        return IAdaptor.TaskDoResponseResult.success(sunoResponse.getData(), responseBody);
    }

    @Override
    public List<String> getModelList() {
        return TaskSunoConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskSunoConstant.CHANNEL_NAME;
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/suno/fetch"))
                .POST(HttpRequest.BodyPublishers.ofString(Convert.toJSONString(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public Object parseTaskResult(byte[] respBody) throws Exception {
        throw new IllegalStateException("suno uses batch polling via fetchTask; ParseTaskResult is not applicable");
    }

    private String resolveAction(RelayInfo info, SunoSubmitReq req) {
        if (info.getTaskAction() != null && !info.getTaskAction().isEmpty()) {
            return info.getTaskAction().toUpperCase();
        }
        String model = req.getModel() != null && !req.getModel().isEmpty() ? req.getModel() : info.getOriginModelName();
        String action = TaskConstants.SUNO_MODEL_2_ACTION.get(model);
        return action != null ? action : TaskConstants.SUNO_ACTION_MUSIC;
    }

    private SunoSubmitReq getSunoRequest(RelayInfo info) {
        Object request = info.getRequest();
        if (request instanceof SunoSubmitReq sunoSubmitReq) return sunoSubmitReq;
        if (request == null) return new SunoSubmitReq();
        SunoSubmitReq req = Convert.toJavaBean(request, SunoSubmitReq.class);
        if (req.getModel() == null && request instanceof RelayInfo.TaskSubmitReq taskReq) {
            req.setModel(taskReq.getModel());
        }
        return req;
    }

    private String resolveClientHeader(RelayInfo info, String name, String fallback) {
        Map<String, String> headers = info.getClientHeaders();
        if (headers == null || headers.isEmpty()) return fallback;
        String value = headers.get(name);
        if (value != null) return value;
        return headers.getOrDefault(name.toLowerCase(), fallback);
    }

    private byte[] responseBodyBytes(HttpResponse<?> resp) {
        Object body = resp != null ? resp.body() : null;
        if (body instanceof byte[] bytes) return bytes;
        if (body instanceof String text) return text.getBytes(StandardCharsets.UTF_8);
        if (body == null) return new byte[0];
        return Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Data
    public static class SunoSubmitReq {
        /** Go: GptDescriptionPrompt — AI 描述生成提示词 */
        private String gptDescriptionPrompt;
        /** Go: Prompt — 歌词生成提示词 */
        private String prompt;
        /** Go: Mv — 模型版本（默认 chirp-v3-0） */
        private String mv;
        /** Go: Title — 歌曲标题 */
        private String title;
        /** Go: Tags — 音乐风格标签 */
        private String tags;
        /** Go: ContinueAt — 续写起始位置（秒） */
        private java.math.BigDecimal continueAt;
        /** Go: TaskID — 续写任务 ID */
        private String taskID;
        /** Go: ContinueClipId — 续写片段 ID */
        private String continueClipId;
        /** Go: MakeInstrumental — 是否纯器乐 */
        private Boolean makeInstrumental;

        /** Java 扩展：模型名（从 RelayInfo 提取或上游覆盖） */
        private String model;
    }

    @Data
    public static class TaskResponse {
        private String code;
        private String message;
        private String data;

        public boolean isSuccess() {
            return code == null || code.isEmpty() || "success".equalsIgnoreCase(code) || "0".equals(code);
        }
    }
}
