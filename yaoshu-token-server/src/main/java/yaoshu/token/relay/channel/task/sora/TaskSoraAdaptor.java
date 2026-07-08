package yaoshu.token.relay.channel.task.sora;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;
import yaoshu.token.service.TaskPollingService;

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
 * Sora / OpenAI Video Task 适配器  */
@Slf4j
public class TaskSoraAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private String apiKey;
    private String baseURL;

    @Override
    public void init(RelayInfo info) {
        this.apiKey = info.getApiKey();
        this.baseURL = info.getChannelBaseUrl();
    }

    @Override
    public Object validateRequestAndSetAction(RelayInfo info) {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            return taskError("invalid_request", "request not found", 400, true);
        }
        if (TaskConstants.TASK_ACTION_REMIX.equals(info.getTaskAction())) {
            return validateRemixRequest(req);
        }

        if (req.getInputReference() != null && !req.getInputReference().isBlank()
                && (req.getImages() == null || req.getImages().isEmpty())) {
            req.setImages(List.of(req.getInputReference()));
        }
        if (req.getModel() == null || req.getModel().isBlank()) {
            return taskError("missing_model", "model field is required", 400, true);
        }
        Object promptError = RelayUtils.validatePrompt(req.getPrompt());
        if (promptError != null) {
            return promptError;
        }
        int seconds = parseSeconds(req);
        Object soraError = RelayUtils.validateSoraParams(req.getModel(), req.getSize(), seconds);
        if (soraError != null) {
            return soraError;
        }
        info.setTaskAction(req.hasImage() ? TaskConstants.TASK_ACTION_GENERATE : TaskConstants.TASK_ACTION_TEXT_GENERATE);
        return null;
    }

    @Override
    public Map<String, Double> estimateBilling(RelayInfo info) {
        if (TaskConstants.TASK_ACTION_REMIX.equals(info.getTaskAction())) {
            return null;
        }
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            return null;
        }
        int seconds = parseSeconds(req);
        if (seconds <= 0) {
            seconds = 4;
        }
        String size = req.getSize();
        if (size == null || size.isBlank()) {
            size = "720x1280";
        }

        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("seconds", (double) seconds);
        ratios.put("size", ("1792x1024".equals(size) || "1024x1792".equals(size)) ? 1.666667D : 1D);
        return ratios;
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
        if (TaskConstants.TASK_ACTION_REMIX.equals(info.getTaskAction())) {
            return baseURL + "/v1/videos/" + info.getOriginTaskID() + "/remix";
        }
        return baseURL + "/v1/videos";
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) {
        Map<String, Object> body = getRequestBodyMap(info);
        if (body == null) {
            body = new LinkedHashMap<>();
        } else {
            body = new LinkedHashMap<>(body);
        }
        if (info.getUpstreamModelName() != null && !info.getUpstreamModelName().isBlank()) {
            body.put("model", info.getUpstreamModelName());
        }
        return new ByteArrayInputStream(Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        byte[] responseBody = responseBodyBytes(resp);
        ResponseTask responseTask = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), ResponseTask.class);
        String upstreamId = responseTask.getId();
        if (upstreamId == null || upstreamId.isBlank()) {
            upstreamId = responseTask.getTaskId();
        }
        if (upstreamId == null || upstreamId.isBlank()) {
            return IAdaptor.TaskDoResponseResult.failure(taskError("invalid_response", "task_id is empty", 500, false));
        }
        return IAdaptor.TaskDoResponseResult.success(upstreamId, responseBody);
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/videos/" + taskId))
                .GET()
                .header("Authorization", "Bearer " + key)
                .header("Accept", "application/json")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public List<String> getModelList() {
        return TaskSoraConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskSoraConstant.CHANNEL_NAME;
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        ResponseTask responseTask = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), ResponseTask.class);
        TaskPollingService.TaskInfo taskInfo = new TaskPollingService.TaskInfo();
        switch (safe(responseTask.getStatus())) {
            case "queued", "pending" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_QUEUED);
            case "processing", "in_progress" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
            case "completed" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
            case "failed", "cancelled" -> {
                taskInfo.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                if (responseTask.getError() != null && responseTask.getError().getMessage() != null && !responseTask.getError().getMessage().isBlank()) {
                    taskInfo.setReason(responseTask.getError().getMessage());
                } else {
                    taskInfo.setReason("task failed");
                }
            }
            default -> {
            }
        }
        if (responseTask.getProgress() > 0 && responseTask.getProgress() < 100) {
            taskInfo.setProgress(responseTask.getProgress() + "%");
        }
        return taskInfo;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        Map<String, Object> body = task.getData() != null && !task.getData().isBlank()
                ? Convert.toJSONObject(task.getData())
                : new LinkedHashMap<>();
        body.put("id", task.getTaskId());
        body.put("task_id", task.getTaskId());
        if (!body.containsKey("status") && task.getStatus() != null) {
            body.put("status", task.getStatus());
        }
        if (!body.containsKey("progress") && task.getProgress() != null) {
            body.put("progress", task.getProgress());
        }
        if (!body.containsKey("created_at") && task.getCreatedAt() != null) {
            body.put("created_at", task.getCreatedAt());
        }
        if (!body.containsKey("completed_at") && task.getUpdatedAt() != null) {
            body.put("completed_at", task.getUpdatedAt());
        }
        return Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8);
    }

    private Object validateRemixRequest(RelayInfo.TaskSubmitReq req) {
        return RelayUtils.validatePrompt(req.getPrompt());
    }

    private int parseSeconds(RelayInfo.TaskSubmitReq req) {
        if (req.getSeconds() != null && !req.getSeconds().isBlank()) {
            try {
                return Integer.parseInt(req.getSeconds());
            } catch (NumberFormatException ignored) {
                return req.getDuration();
            }
        }
        return req.getDuration();
    }

    private RelayInfo.TaskSubmitReq getTaskRequest(RelayInfo info) {
        Object request = info.getRequest();
        if (request instanceof RelayInfo.TaskSubmitReq taskSubmitReq) {
            return taskSubmitReq;
        }
        if (request == null) {
            return null;
        }
        return Convert.toJavaBean(request, RelayInfo.TaskSubmitReq.class);
    }

    private Map<String, Object> getRequestBodyMap(RelayInfo info) {
        Object request = info.getRequest();
        if (request instanceof Map<?, ?> map) {
            Map<String, Object> body = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    body.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return body;
        }
        if (request == null) {
            return null;
        }
        return Convert.toJSONObject(request);
    }

    private byte[] responseBodyBytes(HttpResponse<?> resp) throws Exception {
        Object body = resp != null ? resp.body() : null;
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        if (body instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        if (body instanceof InputStream inputStream) {
            try (InputStream stream = inputStream) {
                return stream.readAllBytes();
            }
        }
        if (body == null) {
            return new byte[0];
        }
        return Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> taskError(String code, String message, int statusCode, boolean localError) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("statusCode", statusCode);
        error.put("localError", localError);
        return error;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Data
    public static class ResponseTask {
        private String id;
        private String taskId;
        private String object;
        private String model;
        private String status;
        private int progress;
        private Long createdAt;
        private Long completedAt;
        private Long expiresAt;
        private String seconds;
        private String size;
        private String remixedFromVideoId;
        private ResponseError error;
    }

    @Data
    public static class ResponseError {
        private String message;
        private String code;
    }
}
