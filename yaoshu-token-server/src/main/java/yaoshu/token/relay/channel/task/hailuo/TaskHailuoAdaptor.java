package yaoshu.token.relay.channel.task.hailuo;

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

import static yaoshu.token.relay.channel.task.hailuo.TaskHailuoModels.*;

/**
 * Hailuo/MiniMax 视频 Task 适配器  */
@Slf4j
public class TaskHailuoAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

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
        return baseURL + TEXT_TO_VIDEO_ENDPOINT;
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
        VideoRequest payload = convertToRequestPayload(req, info);
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
        VideoResponse hailuoResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), VideoResponse.class);
        if (hailuoResp.getBaseResp() != null && hailuoResp.getBaseResp().getStatusCode() != STATUS_SUCCESS) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of(
                    "code", String.valueOf(hailuoResp.getBaseResp().getStatusCode()),
                    "message", hailuoResp.getBaseResp().getStatusMsg(),
                    "statusCode", 400,
                    "localError", false));
        }
        return IAdaptor.TaskDoResponseResult.success(hailuoResp.getTaskId(), responseBody);
    }

    @Override
    public List<String> getModelList() {
        return MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        String uri = baseUrl + QUERY_TASK_ENDPOINT + "?task_id=" + taskId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        QueryTaskResponse resTask = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), QueryTaskResponse.class);
        TaskPollingService.TaskInfo taskResult = new TaskPollingService.TaskInfo();

        if (resTask.getBaseResp() != null && resTask.getBaseResp().getStatusCode() != STATUS_SUCCESS) {
            taskResult.setReason(resTask.getBaseResp().getStatusMsg());
            taskResult.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
            return taskResult;
        }

        switch (resTask.getStatus()) {
            case TASK_STATUS_PREPARING, TASK_STATUS_QUEUEING -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_IN_PROGRESS);
            }
            case TASK_STATUS_PROCESSING -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
                taskResult.setProgress("50%");
            }
            case TASK_STATUS_SUCCESS -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                taskResult.setUrl(buildVideoURL(resTask.getFileId()));
            }
            case TASK_STATUS_FAILED -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                taskResult.setReason("task failed");
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
        Map<String, Object> openAIVideo = new LinkedHashMap<>();
        openAIVideo.put("id", task.getTaskId());
        openAIVideo.put("status", task.getStatus());
        openAIVideo.put("progress", task.getProgress());
        openAIVideo.put("created_at", task.getCreatedAt());
        openAIVideo.put("completed_at", task.getFinishTime() != null && task.getFinishTime() > 0 ? task.getFinishTime() : task.getUpdatedAt());
        return Convert.toJSONString(openAIVideo).getBytes(StandardCharsets.UTF_8);
    }

    private VideoRequest convertToRequestPayload(RelayInfo.TaskSubmitReq req, RelayInfo info) throws Exception {
        ModelConfig modelConfig = getModelConfig(info.getUpstreamModelName());
        int duration = req.getDuration() > 0 ? req.getDuration() : DEFAULT_DURATION;
        String resolution = req.getSize() != null && !req.getSize().isEmpty()
                ? parseResolutionFromSize(req.getSize(), modelConfig)
                : modelConfig.getDefaultResolution();

        VideoRequest videoRequest = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), VideoRequest.class);
        if (videoRequest == null) videoRequest = new VideoRequest();
        videoRequest.setModel(info.getUpstreamModelName());
        videoRequest.setPrompt(req.getPrompt());
        videoRequest.setDuration(duration);
        videoRequest.setResolution(resolution);
        return videoRequest;
    }

    private String parseResolutionFromSize(String size, ModelConfig modelConfig) {
        if (size.contains("1080")) return RESOLUTION_1080P;
        if (size.contains("768")) return RESOLUTION_768P;
        if (size.contains("720")) return RESOLUTION_720P;
        if (size.contains("512")) return RESOLUTION_512P;
        return modelConfig.getDefaultResolution();
    }

    private String buildVideoURL(String fileID) {
        if (apiKey == null || apiKey.isEmpty() || baseURL == null || baseURL.isEmpty() || fileID == null || fileID.isEmpty()) {
            return "";
        }
        try {
            String url = baseURL + "/v1/files/retrieve?file_id=" + fileID;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .build();
            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
            RetrieveFileResponse retrieveResp = Convert.toJavaBean(new String(response.body(), StandardCharsets.UTF_8), RetrieveFileResponse.class);
            if (retrieveResp.getBaseResp() == null || retrieveResp.getBaseResp().getStatusCode() != STATUS_SUCCESS || retrieveResp.getFile() == null) {
                return "";
            }
            return retrieveResp.getFile().getDownloadUrl();
        } catch (Exception e) {
            log.warn("Hailuo retrieve file url failed: {}", e.getMessage());
            return "";
        }
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
