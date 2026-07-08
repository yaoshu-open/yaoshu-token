package yaoshu.token.relay.channel.task.doubao;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.task.common.TaskCommonHelper;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.TaskPollingService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static yaoshu.token.relay.channel.task.doubao.TaskDoubaoDTO.*;

/**
 * Doubao 视频 Task 适配器  */
@Slf4j
public class TaskDoubaoAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

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
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null || !hasVideoInMetadata(req.getMetadata())) return null;
        Double ratio = TaskDoubaoConstant.getVideoInputRatio(info.getOriginModelName());
        if (ratio == null) return null;
        return Map.of("video_input", ratio);
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
        return baseURL + "/api/v3/contents/generations/tasks";
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }
        RequestPayload body = convertToRequestPayload(req);
        if (info.isModelMapped()) {
            body.setModel(info.getUpstreamModelName());
        } else {
            info.setUpstreamModelName(body.getModel());
        }
        return new ByteArrayInputStream(Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) {
        byte[] responseBody = responseBodyBytes(resp);
        ResponsePayload dResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), ResponsePayload.class);
        if (dResp.getId() == null || dResp.getId().isEmpty()) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "invalid_response", "message", "task_id is empty", "statusCode", 500, "localError", false));
        }
        return IAdaptor.TaskDoResponseResult.success(dResp.getId(), responseBody);
    }

    @Override
    public List<String> getModelList() {
        return TaskDoubaoConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskDoubaoConstant.CHANNEL_NAME;
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        String uri = baseUrl + "/api/v3/contents/generations/tasks/" + taskId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        ResponseTask resTask = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), ResponseTask.class);
        TaskPollingService.TaskInfo taskResult = new TaskPollingService.TaskInfo();
        switch (resTask.getStatus()) {
            case "pending", "queued" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_QUEUED);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_SUBMITTED);
            }
            case "processing", "running" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
                taskResult.setProgress("50%");
            }
            case "succeeded" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                if (resTask.getContent() != null) taskResult.setUrl(resTask.getContent().getVideoUrl());
                if (resTask.getUsage() != null) {
                    taskResult.setTotalTokens(resTask.getUsage().getTotalTokens());
                }
            }
            case "failed" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                if (resTask.getError() != null) taskResult.setReason(resTask.getError().getMessage());
            }
            default -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_IN_PROGRESS);
            }
        }
        return taskResult;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        ResponseTask dResp = task.getData() != null && !task.getData().isEmpty()
                ? Convert.toJavaBean(task.getData(), ResponseTask.class)
                : new ResponseTask();
        Map<String, Object> openAIVideo = new LinkedHashMap<>();
        openAIVideo.put("id", task.getTaskId());
        openAIVideo.put("task_id", task.getTaskId());
        openAIVideo.put("status", task.getStatus());
        openAIVideo.put("progress", task.getProgress());
        if (dResp.getContent() != null) openAIVideo.put("metadata", Map.of("url", dResp.getContent().getVideoUrl()));
        openAIVideo.put("created_at", task.getCreatedAt());
        openAIVideo.put("completed_at", task.getUpdatedAt());
        if (dResp.getModel() != null) openAIVideo.put("model", dResp.getModel());
        if ("failed".equals(dResp.getStatus()) && dResp.getError() != null) {
            openAIVideo.put("error", Map.of("message", dResp.getError().getMessage(), "code", dResp.getError().getCode()));
        }
        return Convert.toJSONString(openAIVideo).getBytes(StandardCharsets.UTF_8);
    }

    private RequestPayload convertToRequestPayload(RelayInfo.TaskSubmitReq req) throws Exception {
        RequestPayload payload = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), RequestPayload.class);
        if (payload == null) payload = new RequestPayload();
        payload.setModel(req.getModel());
        List<ContentItem> content = payload.getContent() != null ? new ArrayList<>(payload.getContent()) : new ArrayList<>();
        if (req.hasImage()) {
            for (String imgURL : req.getImages()) {
                ContentItem item = new ContentItem();
                item.setType("image_url");
                MediaURL imageURL = new MediaURL();
                imageURL.setUrl(imgURL);
                item.setImageUrl(imageURL);
                content.add(item);
            }
        }
        if (req.getSeconds() != null && !req.getSeconds().isEmpty()) {
            try {
                int seconds = Integer.parseInt(req.getSeconds());
                if (seconds > 0) payload.setDuration(seconds);
            } catch (NumberFormatException ignored) {
            }
        }
        content.removeIf(item -> "text".equals(item.getType()));
        ContentItem text = new ContentItem();
        text.setType("text");
        text.setText(req.getPrompt());
        content.add(text);
        payload.setContent(content);
        return payload;
    }

    private boolean hasVideoInMetadata(Map<String, Object> metadata) {
        if (metadata == null) return false;
        Object contentRaw = metadata.get("content");
        if (!(contentRaw instanceof List<?> contentList)) return false;
        for (Object item : contentList) {
            if (!(item instanceof Map<?, ?> itemMap)) continue;
            if ("video_url".equals(itemMap.get("type")) || itemMap.containsKey("video_url")) return true;
        }
        return false;
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
}
