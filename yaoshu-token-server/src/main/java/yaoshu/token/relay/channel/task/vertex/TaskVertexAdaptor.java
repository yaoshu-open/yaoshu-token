package yaoshu.token.relay.channel.task.vertex;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.dto.TaskPrivateData;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.task.common.TaskCommonHelper;
import yaoshu.token.relay.channel.task.gemini.TaskGeminiBillingHandler;
import yaoshu.token.relay.channel.task.gemini.TaskGeminiDTO;
import yaoshu.token.relay.channel.task.gemini.TaskGeminiImageHandler;
import yaoshu.token.relay.channel.vertex.VertexDTOPlaceholder;
import yaoshu.token.relay.channel.vertex.VertexServiceAccountHelper;
import yaoshu.token.relay.channel.vertex.VertexUrlBuilder;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayUtils;
import yaoshu.token.service.TaskPollingService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vertex AI 视频 Task 适配器  */
@Slf4j
public class TaskVertexAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private static final List<String> MODEL_LIST = List.of(
            "veo-3.0-generate-001",
            "veo-3.0-fast-generate-001",
            "veo-3.1-generate-preview",
            "veo-3.1-fast-generate-preview"
    );
    private static final String DEFAULT_MODEL = "veo-3.0-generate-001";
    private static final Pattern REGION_PATTERN = Pattern.compile("locations/([a-z0-9-]+)/");
    private static final Pattern PROJECT_PATTERN = Pattern.compile("projects/([^/]+)/locations/");
    private static final Pattern MODEL_PATTERN = Pattern.compile("models/([^/]+)/operations/");

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
        Object promptError = RelayUtils.validatePrompt(req.getPrompt());
        if (promptError != null) {
            return promptError;
        }
        if (req.getInputReference() != null && !req.getInputReference().isBlank()
                && (req.getImages() == null || req.getImages().isEmpty())) {
            req.setImages(List.of(req.getInputReference()));
        }
        if (info.getTaskAction() == null || info.getTaskAction().isEmpty()) {
            info.setTaskAction(TaskConstants.TASK_ACTION_TEXT_GENERATE);
        }
        if (info.getOriginModelName() == null || info.getOriginModelName().isBlank()) {
            String model = req.getModel();
            info.setOriginModelName(model == null || model.isBlank() ? DEFAULT_MODEL : model);
        }
        return null;
    }

    @Override
    public Map<String, Double> estimateBilling(RelayInfo info) {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            return null;
        }
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
        VertexServiceAccountHelper.Credentials credentials = parseCredentials(apiKey);
        String modelName = info.getUpstreamModelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = DEFAULT_MODEL;
        }
        String region = VertexDTOPlaceholder.getModelRegion(info.getApiVersion(), modelName);
        if (region == null || region.isBlank()) {
            region = "global";
        }
        return VertexUrlBuilder.buildGoogleModelURL(
                baseURL,
                VertexUrlBuilder.DEFAULT_API_VERSION,
                credentials.getProjectId(),
                region,
                modelName,
                "predictLongRunning"
        );
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) throws Exception {
        VertexServiceAccountHelper.Credentials credentials = parseCredentials(apiKey);
        String proxy = info.getChannelSetting() != null ? info.getChannelSetting().getProxy() : null;
        String token = VertexServiceAccountHelper.acquireAccessToken(credentials, proxy);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("x-goog-user-project", credentials.getProjectId());
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }
        if (req.getInputReference() != null && !req.getInputReference().isBlank()
                && (req.getImages() == null || req.getImages().isEmpty())) {
            req.setImages(List.of(req.getInputReference()));
        }

        TaskGeminiDTO.VeoInstance instance = new TaskGeminiDTO.VeoInstance();
        instance.setPrompt(req.getPrompt());
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            TaskGeminiDTO.VeoImageInput image = TaskGeminiImageHandler.parseImageInput(req.getImages().get(0));
            if (image != null) {
                instance.setImage(image);
                info.setTaskAction(TaskConstants.TASK_ACTION_GENERATE);
            }
        }

        TaskGeminiDTO.VeoParameters params = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), TaskGeminiDTO.VeoParameters.class);
        if (params == null) {
            params = new TaskGeminiDTO.VeoParameters();
        }
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

        TaskGeminiDTO.VeoRequestPayload payload = new TaskGeminiDTO.VeoRequestPayload();
        payload.setInstances(List.of(instance));
        payload.setParameters(params);
        return new ByteArrayInputStream(Convert.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        return ApiRequestExecutor.doTaskApiRequest(this, info, body, Map.of(), "POST");
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        byte[] responseBody = responseBodyBytes(resp);
        TaskGeminiDTO.SubmitResponse submitResponse = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), TaskGeminiDTO.SubmitResponse.class);
        if (submitResponse == null || submitResponse.getName() == null || submitResponse.getName().isBlank()) {
            return IAdaptor.TaskDoResponseResult.failure(taskError("invalid_response", "missing operation name", 500, false));
        }
        return IAdaptor.TaskDoResponseResult.success(TaskCommonHelper.encodeLocalTaskID(submitResponse.getName()), responseBody);
    }

    @Override
    public List<String> getModelList() {
        return MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return "vertex";
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskIdValue = body.get("task_id");
        if (taskIdValue == null) {
            throw new IllegalArgumentException("invalid task_id");
        }
        String upstreamName = TaskCommonHelper.decodeLocalTaskID(String.valueOf(taskIdValue));
        String url = buildFetchOperationURL(baseUrl, upstreamName);
        byte[] payload = Convert.toJSONString(Map.of("operationName", upstreamName)).getBytes(StandardCharsets.UTF_8);

        VertexServiceAccountHelper.Credentials credentials = parseCredentials(key);
        String token = VertexServiceAccountHelper.acquireAccessToken(credentials, proxy);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("x-goog-user-project", credentials.getProjectId())
                .build();
        return buildHttpClient(proxy).send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        TaskGeminiDTO.OperationResponse operation = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), TaskGeminiDTO.OperationResponse.class);
        TaskPollingService.TaskInfo taskInfo = new TaskPollingService.TaskInfo();
        if (operation.getError() != null && operation.getError().getMessage() != null && !operation.getError().getMessage().isBlank()) {
            taskInfo.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            taskInfo.setReason(operation.getError().getMessage());
            taskInfo.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
            return taskInfo;
        }
        if (!operation.isDone()) {
            taskInfo.setStatus(TaskConstants.TASK_STATUS_IN_PROGRESS);
            taskInfo.setProgress("50%");
            return taskInfo;
        }
        taskInfo.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
        taskInfo.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
        taskInfo.setTaskId(TaskCommonHelper.encodeLocalTaskID(operation.getName()));
        String url = extractResultUrl(operation);
        if (url != null && !url.isBlank()) {
            taskInfo.setUrl(url);
        }
        return taskInfo;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) throws Exception {
        Task task = (Task) originTask;
        String upstreamTaskId = extractUpstreamTaskId(task);
        String upstreamName = upstreamTaskId != null && !upstreamTaskId.isBlank()
                ? safeDecodeTaskId(upstreamTaskId)
                : "";

        Map<String, Object> video = new LinkedHashMap<>();
        video.put("id", task.getTaskId());
        video.put("model", extractModelFromOperationName(upstreamName));
        video.put("status", task.getStatus());
        video.put("progress", task.getProgress());
        video.put("created_at", task.getCreatedAt());
        video.put("completed_at", task.getFinishTime() != null && task.getFinishTime() > 0 ? task.getFinishTime() : task.getUpdatedAt());

        String url = extractResultUrlFromTask(task);
        if (url != null && !url.isBlank()) {
            video.put("metadata", Map.of("url", url));
        }
        return Convert.toJSONString(video).getBytes(StandardCharsets.UTF_8);
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

    private VertexServiceAccountHelper.Credentials parseCredentials(String credentialsJson) {
        VertexServiceAccountHelper.Credentials credentials = Convert.toJavaBean(credentialsJson, VertexServiceAccountHelper.Credentials.class);
        if (credentials == null || credentials.getProjectId() == null || credentials.getProjectId().isBlank()) {
            throw new IllegalArgumentException("invalid vertex credentials");
        }
        return credentials;
    }

    private HttpClient buildHttpClient(String proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (proxy != null && !proxy.isBlank()) {
            URI proxyUri = proxy.startsWith("http://") || proxy.startsWith("https://")
                    ? URI.create(proxy)
                    : URI.create("http://" + proxy);
            int port = proxyUri.getPort() > 0 ? proxyUri.getPort() : 80;
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port)));
        }
        return builder.build();
    }

    private String buildFetchOperationURL(String baseUrl, String upstreamName) {
        String region = extractByPattern(REGION_PATTERN, upstreamName);
        if (region == null || region.isBlank()) {
            region = "us-central1";
        }
        String project = extractByPattern(PROJECT_PATTERN, upstreamName);
        String model = extractModelFromOperationName(upstreamName);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("cannot extract model from operation name");
        }
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("cannot extract project from operation name");
        }
        return VertexUrlBuilder.buildGoogleModelURL(
                baseUrl,
                VertexUrlBuilder.DEFAULT_API_VERSION,
                project,
                region,
                model,
                "fetchPredictOperation"
        );
    }

    private String extractResultUrl(TaskGeminiDTO.OperationResponse operation) {
        if (operation == null || operation.getResponse() == null) {
            return null;
        }
        TaskGeminiDTO.OperationResponseBody response = operation.getResponse();
        if (response.getVideos() != null && !response.getVideos().isEmpty()) {
            TaskGeminiDTO.OperationVideo video = response.getVideos().get(0);
            if (video.getBytesBase64Encoded() != null && !video.getBytesBase64Encoded().isBlank()) {
                String mimeType = video.getMimeType();
                if (mimeType == null || mimeType.isBlank()) {
                    String encoding = video.getEncoding();
                    if (encoding == null || encoding.isBlank()) {
                        encoding = "mp4";
                    }
                    mimeType = encoding.contains("/") ? encoding : "video/" + encoding;
                }
                return "data:" + mimeType + ";base64," + video.getBytesBase64Encoded();
            }
        }
        if (response.getBytesBase64Encoded() != null && !response.getBytesBase64Encoded().isBlank()) {
            String encoding = response.getEncoding();
            if (encoding == null || encoding.isBlank()) {
                encoding = "mp4";
            }
            String mimeType = encoding.contains("/") ? encoding : "video/" + encoding;
            return "data:" + mimeType + ";base64," + response.getBytesBase64Encoded();
        }
        if (response.getVideo() != null && !response.getVideo().isBlank()) {
            String encoding = response.getEncoding();
            if (encoding == null || encoding.isBlank()) {
                encoding = URLConnection.guessContentTypeFromName("result.mp4");
            }
            if (encoding == null || encoding.isBlank()) {
                encoding = "video/mp4";
            }
            return "data:" + encoding + ";base64," + response.getVideo();
        }
        return null;
    }

    private String extractResultUrlFromTask(Task task) {
        if (task.getData() != null && !task.getData().isBlank()) {
            try {
                TaskGeminiDTO.OperationResponse operation = Convert.toJavaBean(task.getData(), TaskGeminiDTO.OperationResponse.class);
                String url = extractResultUrl(operation);
                if (url != null && !url.isBlank()) {
                    return url;
                }
            } catch (Exception ignored) {
                // 保持与 Go 一致，解析失败时退回其他来源
            }
        }
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        return privateData != null ? privateData.getResultUrl() : null;
    }

    private TaskPrivateData parsePrivateData(String privateDataJson) {
        if (privateDataJson == null || privateDataJson.isBlank()) {
            return null;
        }
        try {
            return Convert.toJavaBean(privateDataJson, TaskPrivateData.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUpstreamTaskId(Task task) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        return privateData != null ? privateData.getUpstreamTaskId() : null;
    }

    private String safeDecodeTaskId(String taskId) {
        try {
            return TaskCommonHelper.decodeLocalTaskID(taskId);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractModelFromOperationName(String name) {
        String model = extractByPattern(MODEL_PATTERN, name);
        if (model != null && !model.isBlank()) {
            return model;
        }
        if (name != null) {
            int start = name.indexOf("models/");
            int end = name.indexOf("/operations/");
            if (start >= 0 && end > start) {
                return name.substring(start + "models/".length(), end);
            }
        }
        return DEFAULT_MODEL;
    }

    private String extractByPattern(Pattern pattern, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
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
}
