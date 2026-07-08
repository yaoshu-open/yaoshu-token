package yaoshu.token.relay.channel.task.ali;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
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
import java.util.Locale;
import java.util.Map;

import static yaoshu.token.relay.channel.task.ali.TaskAliDTO.*;

/**
 * 阿里通义万相 Task 适配器  */
@Slf4j
public class TaskAliAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

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
        RelayInfo.TaskSubmitReq taskReq = getTaskRequest(info);
        if (taskReq == null) return null;
        try {
            AliVideoRequest aliReq = convertToAliRequest(info, taskReq);
            Map<String, Double> otherRatios = new LinkedHashMap<>();
            otherRatios.put("seconds", aliReq.getParameters().getDuration() != null ? aliReq.getParameters().getDuration().doubleValue() : 5.0);
            otherRatios.putAll(processAliOtherRatios(aliReq));
            return otherRatios;
        } catch (Exception e) {
            return null;
        }
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
        return baseURL + "/api/v1/services/aigc/video-generation/video-synthesis";
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("X-DashScope-Async", "enable");
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq taskReq = getTaskRequest(info);
        if (taskReq == null) throw new IllegalStateException("request not found in relay info");
        AliVideoRequest aliReq = convertToAliRequest(info, taskReq);
        return new ByteArrayInputStream(Convert.toJSONString(aliReq).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) {
        byte[] responseBody = responseBodyBytes(resp);
        AliVideoResponse aliResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), AliVideoResponse.class);
        if (aliResp.getCode() != null && !aliResp.getCode().isEmpty()) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "ali_api_error", "message", aliResp.getCode() + ": " + aliResp.getMessage(), "statusCode", resp.statusCode(), "localError", false));
        }
        if (aliResp.getOutput() == null || aliResp.getOutput().getTaskId() == null || aliResp.getOutput().getTaskId().isEmpty()) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", "invalid_response", "message", "task_id is empty", "statusCode", 500, "localError", false));
        }
        return IAdaptor.TaskDoResponseResult.success(aliResp.getOutput().getTaskId(), responseBody);
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) throw new IllegalArgumentException("invalid task_id");
        String uri = baseUrl + "/api/v1/tasks/" + taskId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .header("Authorization", "Bearer " + key)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public List<String> getModelList() {
        return TaskAliConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return TaskAliConstant.CHANNEL_NAME;
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        AliVideoResponse aliResp = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), AliVideoResponse.class);
        TaskPollingService.TaskInfo taskResult = new TaskPollingService.TaskInfo();
        String status = aliResp.getOutput() != null ? aliResp.getOutput().getTaskStatus() : null;
        switch (status) {
            case "PENDING" -> taskResult.setStatus(TaskConstants.TASK_STATUS_QUEUED);
            case "RUNNING" -> taskResult.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
            case "SUCCEEDED" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                taskResult.setUrl(aliResp.getOutput().getVideoUrl());
            }
            case "FAILED", "CANCELED", "UNKNOWN" -> {
                taskResult.setStatus(TaskConstants.TASK_STATUS_FAILURE);
                if (aliResp.getMessage() != null && !aliResp.getMessage().isEmpty()) {
                    taskResult.setReason(aliResp.getMessage());
                } else if (aliResp.getOutput() != null && aliResp.getOutput().getMessage() != null && !aliResp.getOutput().getMessage().isEmpty()) {
                    taskResult.setReason("task failed, code: " + aliResp.getOutput().getCode() + " , message: " + aliResp.getOutput().getMessage());
                } else {
                    taskResult.setReason("task failed");
                }
            }
            default -> taskResult.setStatus(TaskConstants.TASK_STATUS_QUEUED);
        }
        return taskResult;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        AliVideoResponse aliResp = task.getData() != null && !task.getData().isEmpty()
                ? Convert.toJavaBean(task.getData(), AliVideoResponse.class)
                : new AliVideoResponse();
        Map<String, Object> openAIResp = new LinkedHashMap<>();
        openAIResp.put("id", task.getTaskId());
        openAIResp.put("status", aliResp.getOutput() != null ? convertAliStatus(aliResp.getOutput().getTaskStatus()) : task.getStatus());
        openAIResp.put("progress", task.getProgress());
        openAIResp.put("created_at", task.getCreatedAt());
        openAIResp.put("completed_at", task.getUpdatedAt());
        if (aliResp.getOutput() != null && aliResp.getOutput().getVideoUrl() != null) {
            openAIResp.put("metadata", Map.of("url", aliResp.getOutput().getVideoUrl()));
        }
        if (aliResp.getCode() != null && !aliResp.getCode().isEmpty()) {
            openAIResp.put("error", Map.of("code", aliResp.getCode(), "message", aliResp.getMessage()));
        } else if (aliResp.getOutput() != null && aliResp.getOutput().getCode() != null && !aliResp.getOutput().getCode().isEmpty()) {
            openAIResp.put("error", Map.of("code", aliResp.getOutput().getCode(), "message", aliResp.getOutput().getMessage()));
        }
        return Convert.toJSONString(openAIResp).getBytes(StandardCharsets.UTF_8);
    }

    private AliVideoRequest convertToAliRequest(RelayInfo info, RelayInfo.TaskSubmitReq req) throws Exception {
        String upstreamModel = info.isModelMapped() ? info.getUpstreamModelName() : req.getModel();
        AliVideoInput input = new AliVideoInput();
        input.setPrompt(req.getPrompt());
        input.setImgUrl(req.getInputReference());

        AliVideoParameters parameters = new AliVideoParameters();
        parameters.setPromptExtend(true);
        parameters.setWatermark(false);
        applySize(req, parameters);
        if (req.getDuration() > 0) {
            parameters.setDuration(req.getDuration());
        } else if (req.getSeconds() != null && !req.getSeconds().isEmpty()) {
            parameters.setDuration(Integer.parseInt(req.getSeconds()));
        } else {
            parameters.setDuration(5);
        }

        AliVideoRequest aliReq = new AliVideoRequest();
        aliReq.setModel(upstreamModel);
        aliReq.setInput(input);
        aliReq.setParameters(parameters);
        if (req.getMetadata() != null) {
            AliVideoRequest metadataReq = Convert.toJavaBean(Convert.toJSONString(req.getMetadata()), AliVideoRequest.class);
            mergeMetadata(aliReq, metadataReq);
        }
        if (!upstreamModel.equals(aliReq.getModel())) {
            throw new IllegalArgumentException("can't change model with metadata");
        }
        return aliReq;
    }

    private void applySize(RelayInfo.TaskSubmitReq req, AliVideoParameters parameters) {
        String model = req.getModel() != null ? req.getModel() : "";
        String size = req.getSize();
        if (size != null && !size.isEmpty()) {
            if (model.contains("t2v") && !size.contains("*")) {
                throw new IllegalArgumentException("invalid size: " + size + ", example: 1920*1080");
            }
            if (size.contains("*")) {
                parameters.setSize(size);
            } else {
                String resolution = size.toUpperCase(Locale.ROOT);
                if (!resolution.endsWith("P")) resolution += "P";
                parameters.setResolution(resolution);
            }
            return;
        }
        if (model.contains("t2v")) {
            parameters.setSize(model.startsWith("wan2.5") || model.startsWith("wan2.2") ? "1920*1080" : "1280*720");
        } else if (model.startsWith("wan2.6") || model.startsWith("wan2.5") || model.startsWith("wan2.2-i2v-plus")) {
            parameters.setResolution("1080P");
        } else if (model.startsWith("wan2.2-i2v-flash")) {
            parameters.setResolution("720P");
        } else {
            parameters.setResolution("720P");
        }
    }

    private void mergeMetadata(AliVideoRequest target, AliVideoRequest metadata) {
        if (metadata == null) return;
        if (metadata.getInput() != null) {
            AliVideoInput input = target.getInput();
            AliVideoInput metaInput = metadata.getInput();
            if (metaInput.getAudioUrl() != null) input.setAudioUrl(metaInput.getAudioUrl());
            if (metaInput.getImgUrl() != null) input.setImgUrl(metaInput.getImgUrl());
            if (metaInput.getFirstFrameUrl() != null) input.setFirstFrameUrl(metaInput.getFirstFrameUrl());
            if (metaInput.getLastFrameUrl() != null) input.setLastFrameUrl(metaInput.getLastFrameUrl());
            if (metaInput.getNegativePrompt() != null) input.setNegativePrompt(metaInput.getNegativePrompt());
            if (metaInput.getTemplate() != null) input.setTemplate(metaInput.getTemplate());
        }
        if (metadata.getParameters() != null) {
            AliVideoParameters params = target.getParameters();
            AliVideoParameters metaParams = metadata.getParameters();
            if (metaParams.getResolution() != null) params.setResolution(metaParams.getResolution());
            if (metaParams.getSize() != null) params.setSize(metaParams.getSize());
            if (metaParams.getDuration() != null) params.setDuration(metaParams.getDuration());
            if (metaParams.getPromptExtend() != null) params.setPromptExtend(metaParams.getPromptExtend());
            if (metaParams.getWatermark() != null) params.setWatermark(metaParams.getWatermark());
            if (metaParams.getAudio() != null) params.setAudio(metaParams.getAudio());
            if (metaParams.getSeed() != null) params.setSeed(metaParams.getSeed());
        }
    }

    private Map<String, Double> processAliOtherRatios(AliVideoRequest aliReq) {
        String resolution;
        String size = aliReq.getParameters().getSize();
        if (size != null && !size.isEmpty()) {
            resolution = sizeToResolution(size);
            if (resolution == null) return Map.of();
        } else {
            resolution = aliReq.getParameters().getResolution().toUpperCase(Locale.ROOT);
            if (!resolution.endsWith("P")) resolution += "P";
        }
        Map<String, Double> ratio = TaskAliConstant.ALI_RATIOS.get(aliReq.getModel());
        if (ratio != null && ratio.containsKey(resolution)) {
            return Map.of("resolution-" + resolution, ratio.get(resolution));
        }
        return Map.of();
    }

    private String sizeToResolution(String size) {
        if (TaskAliConstant.SIZE_480P.contains(size)) return "480P";
        if (TaskAliConstant.SIZE_720P.contains(size)) return "720P";
        if (TaskAliConstant.SIZE_1080P.contains(size)) return "1080P";
        return null;
    }

    private String convertAliStatus(String aliStatus) {
        return switch (aliStatus) {
            case "PENDING" -> "queued";
            case "RUNNING" -> "in_progress";
            case "SUCCEEDED" -> "completed";
            case "FAILED", "CANCELED", "UNKNOWN" -> "failed";
            default -> "unknown";
        };
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
