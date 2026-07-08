package yaoshu.token.relay.channel.task.gemini;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.model.GeminiModelConfig;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static yaoshu.token.relay.channel.task.gemini.TaskGeminiDTO.*;

/**
 * Gemini Task 适配器  */
@Slf4j
public class TaskGeminiAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private int channelType;
    private String apiKey;
    private String baseURL;

    @Override
    public void init(RelayInfo info) {
        this.channelType = info.getChannelType();
        this.apiKey = info.getApiKey();
        this.baseURL = info.getChannelBaseUrl();
    }

    @Override
    public Object validateRequestAndSetAction(RelayInfo info) {
        if (info.getTaskAction() == null || info.getTaskAction().isEmpty()) {
            info.setTaskAction(TaskConstants.TASK_ACTION_TEXT_GENERATE);
        }
        return null;
    }

    @Override
    public Map<String, Double> estimateBilling(RelayInfo info) {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) return null;
        int seconds = TaskGeminiBillingHandler.resolveVeoDuration(req.getMetadata(), req.getDuration(), req.getSeconds());
        String resolution = TaskGeminiBillingHandler.resolveVeoResolution(req.getMetadata(), req.getSize());
        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("seconds", (double) seconds);
        ratios.put("resolution", TaskGeminiBillingHandler.veoResolutionRatio(info.getUpstreamModelName(), resolution));
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
        String modelName = info.getUpstreamModelName();
        String version = GeminiModelConfig.getInstance().getGeminiVersionSetting(modelName);
        return baseURL + "/" + version + "/models/" + modelName + ":predictLongRunning";
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("x-goog-api-key", apiKey);
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }

        VeoInstance instance = new VeoInstance();
        instance.setPrompt(req.getPrompt());
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            VeoImageInput image = TaskGeminiImageHandler.parseImageInput(req.getImages().get(0));
            if (image != null) {
                instance.setImage(image);
                info.setTaskAction(TaskConstants.TASK_ACTION_GENERATE);
            }
        }

        VeoParameters params = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), VeoParameters.class);
        if (params == null) params = new VeoParameters();
        if (params.getDurationSeconds() == null && req.getDuration() > 0) {
            params.setDurationSeconds(req.getDuration());
        }
        if ((params.getResolution() == null || params.getResolution().isEmpty()) && req.getSize() != null && !req.getSize().isEmpty()) {
            params.setResolution(TaskGeminiBillingHandler.sizeToVeoResolution(req.getSize()));
        }
        if ((params.getAspectRatio() == null || params.getAspectRatio().isEmpty()) && req.getSize() != null && !req.getSize().isEmpty()) {
            params.setAspectRatio(TaskGeminiBillingHandler.sizeToVeoAspectRatio(req.getSize()));
        }
        if (params.getResolution() != null) {
            params.setResolution(params.getResolution().toLowerCase());
        }
        params.setSampleCount(1);

        VeoRequestPayload payload = new VeoRequestPayload();
        payload.setInstances(List.of(instance));
        payload.setParameters(params);
        return new ByteArrayInputStream(Convert.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @SuppressWarnings("unchecked")
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        byte[] responseBody = responseBodyBytes(resp);
        SubmitResponse submitResponse = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), SubmitResponse.class);
        if (submitResponse == null || submitResponse.getName() == null || submitResponse.getName().trim().isEmpty()) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "invalid_response", "message", "missing operation name", "statusCode", 500, "localError", false));
        }
        return IAdaptor.TaskDoResponseResult.success(TaskCommonHelper.encodeLocalTaskID(submitResponse.getName()), responseBody);
    }

    @Override
    public List<String> getModelList() {
        return TaskGeminiConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return "gemini";
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskIdValue = body.get("task_id");
        if (taskIdValue == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        String upstreamName = TaskCommonHelper.decodeLocalTaskID(String.valueOf(taskIdValue));
        String version = GeminiModelConfig.getInstance().getGeminiVersionSetting("default");
        String url = baseUrl + "/" + version + "/" + upstreamName;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .header("x-goog-api-key", key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        OperationResponse op = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), OperationResponse.class);
        TaskPollingService.TaskInfo info = new TaskPollingService.TaskInfo();
        if (op.getError() != null && op.getError().getMessage() != null && !op.getError().getMessage().isEmpty()) {
            info.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            info.setReason(op.getError().getMessage());
            info.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
            return info;
        }
        if (!op.isDone()) {
            info.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
            info.setProgress("50%");
            return info;
        }
        info.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
        info.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
        info.setTaskId(TaskCommonHelper.encodeLocalTaskID(op.getName()));
        String remoteUrl = extractRemoteUrl(op);
        if (remoteUrl != null && !remoteUrl.isEmpty()) {
            info.setUrl(remoteUrl);
        }
        return info;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("id", task.getTaskId());
        video.put("model", extractModelFromOperationName(task.getTaskId()));
        video.put("status", task.getStatus());
        video.put("progress", task.getProgress());
        video.put("created_at", task.getCreatedAt());
        video.put("completed_at", task.getFinishTime() != null && task.getFinishTime() > 0 ? task.getFinishTime() : task.getUpdatedAt());
        return Convert.toJSONString(video).getBytes(StandardCharsets.UTF_8);
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

    private String extractRemoteUrl(OperationResponse op) {
        if (op.getResponse() == null) return null;
        OperationResponseBody response = op.getResponse();
        if (response.getVideo() != null && !response.getVideo().isEmpty()) return response.getVideo();
        if (response.getVideos() != null && !response.getVideos().isEmpty()) {
            OperationVideo video = response.getVideos().get(0);
            if (video.getBytesBase64Encoded() != null && !video.getBytesBase64Encoded().isEmpty()) {
                String mimeType = video.getMimeType() != null && !video.getMimeType().isEmpty() ? video.getMimeType() : "video/mp4";
                return "data:" + mimeType + ";base64," + video.getBytesBase64Encoded();
            }
        }
        if (response.getBytesBase64Encoded() != null && !response.getBytesBase64Encoded().isEmpty()) {
            return "data:video/mp4;base64," + response.getBytesBase64Encoded();
        }
        return null;
    }

    private String extractModelFromOperationName(String name) {
        if (name == null || name.isEmpty()) return "veo-3.0-generate-001";
        int start = name.indexOf("models/");
        int end = name.indexOf("/operations/");
        if (start >= 0 && end > start) {
            return name.substring(start + "models/".length(), end);
        }
        return "veo-3.0-generate-001";
    }
}
