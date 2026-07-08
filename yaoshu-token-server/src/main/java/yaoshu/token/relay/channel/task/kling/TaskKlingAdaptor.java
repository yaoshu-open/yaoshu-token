package yaoshu.token.relay.channel.task.kling;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.task.common.TaskCommonHelper;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.TaskPollingService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kling 视频 Task 适配器  */
@Slf4j
public class TaskKlingAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private String apiKey;
    private String baseURL;

    @Override
    public void init(RelayInfo info) {
        this.apiKey = info.getApiKey();
        this.baseURL = info.getChannelBaseUrl();
    }

    @Override
    public Object validateRequestAndSetAction(RelayInfo info) {
        if (info.getTaskAction() == null || info.getTaskAction().isEmpty()) {
            info.setTaskAction(TaskConstants.TASK_ACTION_GENERATE);
        }
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
        String path = TaskConstants.TASK_ACTION_GENERATE.equals(info.getTaskAction()) ? "/v1/videos/image2video" : "/v1/videos/text2video";
        if (isYaoshuTokenKey(apiKey)) {
            return baseURL + "/kling" + path;
        }
        return baseURL + path;
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + createJWTToken(apiKey));
        headers.put("User-Agent", "kling-sdk/1.0");
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }
        RequestPayload payload = convertToRequestPayload(req, info);
        if ((payload.getImage() == null || payload.getImage().isEmpty()) && (payload.getImageTail() == null || payload.getImageTail().isEmpty())) {
            info.setTaskAction(TaskConstants.TASK_ACTION_TEXT_GENERATE);
        }
        return new ByteArrayInputStream(Convert.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) {
        byte[] responseBody = responseBodyBytes(resp);
        ResponsePayload kResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), ResponsePayload.class);
        if (kResp.getCode() != 0) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "task_failed", "message", safe(kResp.getMessage()), "statusCode", 400, "localError", true));
        }
        String taskId = kResp.getData() != null ? kResp.getData().getTaskId() : kResp.getTaskId();
        return IAdaptor.TaskDoResponseResult.success(taskId, responseBody);
    }

    @Override
    public List<String> getModelList() {
        return TaskKlingChannelConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskKlingChannelConstant.CHANNEL_NAME;
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        Object action = body.get("action");
        if (taskId == null) throw new IllegalArgumentException("invalid task_id");
        if (action == null) throw new IllegalArgumentException("invalid action");
        String path = TaskConstants.TASK_ACTION_GENERATE.equals(String.valueOf(action)) ? "/v1/videos/image2video" : "/v1/videos/text2video";
        String url = isYaoshuTokenKey(key) ? baseUrl + "/kling" + path + "/" + taskId : baseUrl + path + "/" + taskId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + createJWTToken(key))
                .header("User-Agent", "kling-sdk/1.0")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        ResponsePayload resPayload = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), ResponsePayload.class);
        TaskPollingService.TaskInfo taskInfo = new TaskPollingService.TaskInfo();
        if (resPayload.getCode() != 0) {
            taskInfo.setReason(resPayload.getMessage());
        }
        if (resPayload.getData() != null) {
            taskInfo.setTaskId(resPayload.getData().getTaskId());
            taskInfo.setReason(resPayload.getData().getTaskStatusMsg());
            switch (safe(resPayload.getData().getTaskStatus())) {
                case "submitted" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_SUBMITTED);
                case "processing" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
                case "succeed" -> {
                    taskInfo.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                    if (resPayload.getData().getTaskResult() != null && resPayload.getData().getTaskResult().getVideos() != null && !resPayload.getData().getTaskResult().getVideos().isEmpty()) {
                        taskInfo.setUrl(resPayload.getData().getTaskResult().getVideos().get(0).getUrl());
                    }
                    Integer tokens = ceilPositive(resPayload.getData().getFinalUnitDeduction());
                    if (tokens != null) {
                        taskInfo.setTotalTokens(tokens);
                    }
                }
                case "failed" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                default -> throw new IllegalStateException("unknown task status: " + resPayload.getData().getTaskStatus());
            }
        }
        return taskInfo;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        ResponsePayload klingResp = task.getData() != null && !task.getData().isEmpty()
                ? Convert.toJavaBean(task.getData(), ResponsePayload.class)
                : new ResponsePayload();
        Map<String, Object> openAIVideo = new LinkedHashMap<>();
        openAIVideo.put("id", task.getTaskId());
        openAIVideo.put("status", task.getStatus());
        openAIVideo.put("progress", task.getProgress());
        openAIVideo.put("created_at", klingResp.getData() != null ? klingResp.getData().getCreatedAt() : task.getCreatedAt());
        openAIVideo.put("completed_at", klingResp.getData() != null ? klingResp.getData().getUpdatedAt() : task.getUpdatedAt());
        if (klingResp.getData() != null && klingResp.getData().getTaskResult() != null && klingResp.getData().getTaskResult().getVideos() != null && !klingResp.getData().getTaskResult().getVideos().isEmpty()) {
            Video video = klingResp.getData().getTaskResult().getVideos().get(0);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (video.getUrl() != null && !video.getUrl().isEmpty()) metadata.put("url", video.getUrl());
            if (video.getDuration() != null && !video.getDuration().isEmpty()) openAIVideo.put("seconds", video.getDuration());
            openAIVideo.put("metadata", metadata);
        }
        if (klingResp.getCode() != 0 && klingResp.getMessage() != null && !klingResp.getMessage().isEmpty()) {
            openAIVideo.put("error", Map.of("message", klingResp.getMessage(), "code", String.valueOf(klingResp.getCode())));
        }
        if (klingResp.getData() != null && "failed".equals(klingResp.getData().getTaskStatus())) {
            openAIVideo.put("error", Map.of("message", safe(klingResp.getData().getTaskStatusMsg())));
        }
        return Convert.toJSONString(openAIVideo).getBytes(StandardCharsets.UTF_8);
    }

    private RequestPayload convertToRequestPayload(RelayInfo.TaskSubmitReq req, RelayInfo info) throws Exception {
        RequestPayload payload = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), RequestPayload.class);
        if (payload == null) payload = new RequestPayload();
        payload.setPrompt(req.getPrompt());
        payload.setImage(req.getImage());
        payload.setMode(TaskCommonHelper.defaultString(req.getMode(), "std"));
        payload.setDuration(String.valueOf(TaskCommonHelper.defaultInt(req.getDuration(), 5)));
        payload.setAspectRatio(getAspectRatio(req.getSize()));
        payload.setModelName(TaskCommonHelper.defaultString(info.getUpstreamModelName(), "kling-v1"));
        payload.setModel(payload.getModelName());
        if (payload.getCfgScale() == null) payload.setCfgScale(0.5);
        if (payload.getDynamicMasks() == null) payload.setDynamicMasks(List.of());
        return payload;
    }

    private String createJWTToken(String key) throws Exception {
        if (isYaoshuTokenKey(key)) return key;
        String[] keyParts = key.split("\\|", 2);
        if (keyParts.length != 2) throw new IllegalArgumentException("invalid api_key, required format is accessKey|secretKey");
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iss\":\"" + keyParts[0].trim() + "\",\"exp\":" + (now + 1800) + ",\"nbf\":" + (now - 5) + "}");
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyParts[1].trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String base64Url(String text) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String getAspectRatio(String size) {
        return switch (safe(size)) {
            case "1024x1024", "512x512" -> "1:1";
            case "1280x720", "1920x1080" -> "16:9";
            case "720x1280", "1080x1920" -> "9:16";
            default -> "1:1";
        };
    }

    private Integer ceilPositive(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            int rounded = (int) Math.ceil(Double.parseDouble(value));
            return rounded > 0 ? rounded : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isYaoshuTokenKey(String key) {
        return key != null && key.startsWith("sk-");
    }

    private RelayInfo.TaskSubmitReq getTaskRequest(RelayInfo info) {
        Object request = info.getRequest();
        if (request instanceof RelayInfo.TaskSubmitReq taskSubmitReq) return taskSubmitReq;
        if (request == null) return null;
        return Convert.toJavaBean(request, RelayInfo.TaskSubmitReq.class);
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
    public static class RequestPayload {
        private String prompt;
        private String image;
        private String imageTail;
        private String negativePrompt;
        private String mode;
        private String duration;
        private String aspectRatio;
        private String modelName;
        private String model;
        private Double cfgScale;
        private String staticMask;
        private List<Object> dynamicMasks;
        private Object cameraControl;
        private String callbackUrl;
        private String externalTaskId;
    }

    @Data
    public static class ResponsePayload {
        private int code;
        private String message;
        private String taskId;
        private String requestId;
        private ResponseData data;
    }

    @Data
    public static class ResponseData {
        private String taskId;
        private String taskStatus;
        private String taskStatusMsg;
        private TaskResult taskResult;
        private Long createdAt;
        private Long updatedAt;
        private String finalUnitDeduction;
    }

    @Data
    public static class TaskResult {
        private List<Video> videos;
    }

    @Data
    public static class Video {
        private String id;
        private String url;
        private String watermarkUrl;
        private String duration;
    }
}
