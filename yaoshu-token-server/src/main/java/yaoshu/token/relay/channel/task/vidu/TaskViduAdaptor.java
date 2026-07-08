package yaoshu.token.relay.channel.task.vidu;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static yaoshu.token.relay.channel.task.vidu.TaskViduDTO.*;

/**
 * Vidu 视频 Task 适配器  */
@Slf4j
public class TaskViduAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private int channelType;
    private String baseURL;

    @Override
    public void init(RelayInfo info) {
        this.channelType = info.getChannelType();
        this.baseURL = info.getChannelBaseUrl();
    }

    @Override
    public Object validateRequestAndSetAction(RelayInfo info) {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        String action = TaskConstants.TASK_ACTION_TEXT_GENERATE;
        if (req != null && req.getMetadata() != null && req.getMetadata().get("action") instanceof String metadataAction) {
            action = metadataAction;
        } else if (req != null && req.hasImage()) {
            action = TaskConstants.TASK_ACTION_GENERATE;
            if (req.getImages().size() == 2) {
                action = TaskConstants.TASK_ACTION_FIRST_TAIL_GENERATE;
            } else if (req.getImages().size() > 2) {
                action = TaskConstants.TASK_ACTION_REFERENCE_GENERATE;
            }
        }
        info.setTaskAction(action);
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
        String path = switch (info.getTaskAction()) {
            case TaskConstants.TASK_ACTION_GENERATE -> "/img2video";
            case TaskConstants.TASK_ACTION_FIRST_TAIL_GENERATE -> "/start-end2video";
            case TaskConstants.TASK_ACTION_REFERENCE_GENERATE -> "/reference2video";
            default -> "/text2video";
        };
        return baseURL + "/ent/v2" + path;
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Token " + info.getApiKey());
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }
        RequestPayload body = convertToRequestPayload(req, info);
        if (TaskConstants.TASK_ACTION_REFERENCE_GENERATE.equals(info.getTaskAction()) && body.getModel() != null && body.getModel().contains("viduq2")) {
            body.setModel("viduq2");
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
        ResponsePayload vResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), ResponsePayload.class);
        if ("failed".equals(vResp.getState())) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "task_failed", "message", "task failed", "statusCode", 400, "localError", true));
        }
        return IAdaptor.TaskDoResponseResult.success(vResp.getTaskId(), responseBody);
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        String url = baseUrl + "/ent/v2/tasks/" + taskId + "/creations";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Token " + key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public List<String> getModelList() {
        return TaskViduConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskViduConstant.CHANNEL_NAME;
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) throws Exception {
        TaskResultResponse taskResp = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), TaskResultResponse.class);
        TaskPollingService.TaskInfo taskInfo = new TaskPollingService.TaskInfo();
        switch (taskResp.getState()) {
            case "created", "queueing" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_SUBMITTED);
            case "processing" -> taskInfo.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
            case "success" -> {
                taskInfo.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                if (taskResp.getCreations() != null && !taskResp.getCreations().isEmpty()) {
                    taskInfo.setUrl(taskResp.getCreations().get(0).getUrl());
                }
            }
            case "failed" -> {
                taskInfo.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                taskInfo.setReason(taskResp.getErrCode());
            }
            default -> throw new IllegalStateException("unknown task state: " + taskResp.getState());
        }
        return taskInfo;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        TaskResultResponse viduResp = task.getData() != null && !task.getData().isEmpty()
                ? Convert.toJavaBean(task.getData(), TaskResultResponse.class)
                : new TaskResultResponse();
        Map<String, Object> openAIVideo = new LinkedHashMap<>();
        openAIVideo.put("id", task.getTaskId());
        openAIVideo.put("status", task.getStatus());
        openAIVideo.put("progress", task.getProgress());
        openAIVideo.put("created_at", task.getCreatedAt());
        openAIVideo.put("completed_at", task.getUpdatedAt());
        if (viduResp.getCreations() != null && !viduResp.getCreations().isEmpty()) {
            openAIVideo.put("metadata", Map.of("url", viduResp.getCreations().get(0).getUrl()));
        }
        if ("failed".equals(viduResp.getState()) && viduResp.getErrCode() != null && !viduResp.getErrCode().isEmpty()) {
            openAIVideo.put("error", Map.of("message", viduResp.getErrCode(), "code", viduResp.getErrCode()));
        }
        return Convert.toJSONString(openAIVideo).getBytes(StandardCharsets.UTF_8);
    }

    private RequestPayload convertToRequestPayload(RelayInfo.TaskSubmitReq req, RelayInfo info) throws Exception {
        RequestPayload payload = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), RequestPayload.class);
        if (payload == null) payload = new RequestPayload();
        payload.setModel(TaskCommonHelper.defaultString(info.getUpstreamModelName(), "viduq1"));
        payload.setImages(req.getImages());
        payload.setPrompt(req.getPrompt());
        payload.setDuration(TaskCommonHelper.defaultInt(req.getDuration(), 5));
        payload.setResolution(TaskCommonHelper.defaultString(req.getSize(), "1080p"));
        payload.setMovementAmplitude(TaskCommonHelper.defaultString(payload.getMovementAmplitude(), "auto"));
        if (payload.getBgm() == null) payload.setBgm(false);
        return payload;
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
