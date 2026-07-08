package yaoshu.token.relay.channel.task.jimeng;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.task.common.TaskCommonHelper;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.TaskPollingService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jimeng 视频 Task 适配器  */
@Slf4j
public class TaskJimengAdaptor implements IAdaptor.ITaskAdaptor, IAdaptor.OpenAIVideoConverter {

    private static final long MAX_FILE_SIZE = 4L * 1024 * 1024 + 700L * 1024;

    private String accessKey;
    private String secretKey;
    private String baseURL;

    @Override
    public void init(RelayInfo info) {
        this.baseURL = info.getChannelBaseUrl();
        String[] keyParts = info.getApiKey() != null ? info.getApiKey().split("\\|", 2) : new String[0];
        if (keyParts.length == 2) {
            this.accessKey = keyParts[0].trim();
            this.secretKey = keyParts[1].trim();
        }
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
        if (isYaoshuTokenKey(info.getApiKey())) {
            return baseURL + "/jimeng/?Action=CVSync2AsyncSubmitTask&Version=2022-08-31";
        }
        return baseURL + "/?Action=CVSync2AsyncSubmitTask&Version=2022-08-31";
    }

    @Override
    public Map<String, String> buildRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (isYaoshuTokenKey(info.getApiKey())) {
            headers.put("Authorization", "Bearer " + info.getApiKey());
        } else {
            headers.putAll(signHeaders("POST", URI.create(buildRequestURL(info)), new byte[0], accessKey, secretKey, "application/json"));
        }
        return headers;
    }

    @Override
    public InputStream buildRequestBody(RelayInfo info) throws Exception {
        RelayInfo.TaskSubmitReq req = getTaskRequest(info);
        if (req == null) {
            throw new IllegalStateException("request not found in relay info");
        }
        RequestPayload payload = convertToRequestPayload(req, info);
        return new ByteArrayInputStream(Convert.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception {
        InputStream body = requestBody instanceof InputStream inputStream ? inputStream : null;
        byte[] bodyBytes = body != null ? body.readAllBytes() : new byte[0];
        URI uri = URI.create(buildRequestURL(info));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (isYaoshuTokenKey(info.getApiKey())) {
            builder.header("Authorization", "Bearer " + info.getApiKey());
        } else {
            signHeaders("POST", uri, bodyBytes, accessKey, secretKey, "application/json").forEach(builder::header);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public IAdaptor.TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) {
        byte[] responseBody = responseBodyBytes(resp);
        ResponsePayload jResp = Convert.toJavaBean(new String(responseBody, StandardCharsets.UTF_8), ResponsePayload.class);
        if (jResp.getCode() != 10000) {
            return IAdaptor.TaskDoResponseResult.failure(Map.of("code", String.valueOf(jResp.getCode()), "message", safe(jResp.getMessage()), "statusCode", 500, "localError", false));
        }
        return IAdaptor.TaskDoResponseResult.success(jResp.getData() != null ? jResp.getData().getTaskId() : null, responseBody);
    }

    @Override
    public HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception {
        Object taskId = body.get("task_id");
        if (taskId == null) throw new IllegalArgumentException("invalid task_id");
        String uri = isYaoshuTokenKey(key)
                ? baseUrl + "/jimeng/?Action=CVSync2AsyncGetResult&Version=2022-08-31"
                : baseUrl + "/?Action=CVSync2AsyncGetResult&Version=2022-08-31";
        Map<String, String> payload = Map.of("req_key", "jimeng_vgfm_t2v_l20", "task_id", String.valueOf(taskId));
        byte[] payloadBytes = Convert.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payloadBytes))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (isYaoshuTokenKey(key)) {
            builder.header("Authorization", "Bearer " + key);
        } else {
            String[] keyParts = key.split("\\|", 2);
            if (keyParts.length != 2) throw new IllegalArgumentException("invalid api key format for jimeng: expected 'ak|sk'");
            signHeaders("POST", URI.create(uri), payloadBytes, keyParts[0].trim(), keyParts[1].trim(), "application/json").forEach(builder::header);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public List<String> getModelList() {
        return TaskJimengConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return "jimeng";
    }

    @Override
    public TaskPollingService.TaskInfo parseTaskResult(byte[] respBody) {
        ResponseTask resTask = Convert.toJavaBean(new String(respBody, StandardCharsets.UTF_8), ResponseTask.class);
        TaskPollingService.TaskInfo taskResult = new TaskPollingService.TaskInfo();
        if (resTask.getCode() != 10000) {
            taskResult.setReason(resTask.getMessage());
            taskResult.setStatus(TaskConstants.TASK_STATUS_FAILURE);
            taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
        }
        if (resTask.getData() != null) {
            switch (safe(resTask.getData().getStatus())) {
                case "in_queue" -> {
                    taskResult.setStatus(TaskConstants.TASK_STATUS_QUEUED);
                    taskResult.setProgress(TaskCommonHelper.PROGRESS_SUBMITTED);
                }
                case "done" -> {
                    taskResult.setStatus(TaskConstants.TASK_STATUS_SUCCESS);
                    taskResult.setProgress(TaskCommonHelper.PROGRESS_COMPLETE);
                }
                default -> {
                }
            }
            taskResult.setUrl(resTask.getData().getVideoUrl());
        }
        return taskResult;
    }

    @Override
    public byte[] convertToOpenAIVideo(Object originTask) {
        Task task = (Task) originTask;
        ResponseTask jimengResp = task.getData() != null && !task.getData().isEmpty()
                ? Convert.toJavaBean(task.getData(), ResponseTask.class)
                : new ResponseTask();
        Map<String, Object> openAIVideo = new LinkedHashMap<>();
        openAIVideo.put("id", task.getTaskId());
        openAIVideo.put("status", task.getStatus());
        openAIVideo.put("progress", task.getProgress());
        openAIVideo.put("created_at", task.getCreatedAt());
        openAIVideo.put("completed_at", task.getUpdatedAt());
        if (jimengResp.getData() != null) openAIVideo.put("metadata", Map.of("url", safe(jimengResp.getData().getVideoUrl())));
        if (jimengResp.getCode() != 10000) {
            openAIVideo.put("error", Map.of("message", safe(jimengResp.getMessage()), "code", String.valueOf(jimengResp.getCode())));
        }
        return Convert.toJSONString(openAIVideo).getBytes(StandardCharsets.UTF_8);
    }

    private RequestPayload convertToRequestPayload(RelayInfo.TaskSubmitReq req, RelayInfo info) throws Exception {
        RequestPayload payload = TaskCommonHelper.unmarshalMetadata(req.getMetadata(), RequestPayload.class);
        if (payload == null) payload = new RequestPayload();
        payload.setReqKey(info.getUpstreamModelName());
        payload.setPrompt(req.getPrompt());
        payload.setFrames(req.getDuration() == 10 ? 241 : 121);
        if (req.hasImage()) {
            if (req.getImages().get(0).startsWith("http")) payload.setImageUrls(req.getImages());
            else payload.setBinaryDataBase64(req.getImages());
        }
        int imageLen = Math.max(req.getImages() != null ? req.getImages().size() : 0,
                Math.max(payload.getBinaryDataBase64() != null ? payload.getBinaryDataBase64().size() : 0,
                        payload.getImageUrls() != null ? payload.getImageUrls().size() : 0));
        if (payload.getReqKey() != null && payload.getReqKey().contains("jimeng_v30")) {
            if ("jimeng_v30_pro".equals(payload.getReqKey())) {
                payload.setReqKey("jimeng_ti2v_v30_pro");
            } else if (imageLen > 1) {
                payload.setReqKey(trimSuffixP(payload.getReqKey().replaceFirst("jimeng_v30", "jimeng_i2v_first_tail_v30")));
            } else if (imageLen == 1) {
                payload.setReqKey(trimSuffixP(payload.getReqKey().replaceFirst("jimeng_v30", "jimeng_i2v_first_v30")));
            } else {
                payload.setReqKey(payload.getReqKey().replaceFirst("jimeng_v30", "jimeng_t2v_v30"));
            }
        }
        return payload;
    }

    private Map<String, String> signHeaders(String method, URI uri, byte[] bodyBytes, String ak, String sk, String contentType) throws Exception {
        String hexPayloadHash = sha256Hex(bodyBytes);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String xDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String shortDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String canonicalQuery = canonicalQuery(uri.getRawQuery());
        Map<String, String> headersToSign = new LinkedHashMap<>();
        headersToSign.put("content-type", contentType);
        headersToSign.put("host", uri.getHost());
        headersToSign.put("x-content-sha256", hexPayloadHash);
        headersToSign.put("x-date", xDate);
        List<String> keys = new ArrayList<>(headersToSign.keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder canonicalHeaders = new StringBuilder();
        for (String key : keys) {
            canonicalHeaders.append(key).append(':').append(headersToSign.get(key).trim()).append('\n');
        }
        String signedHeaders = String.join(";", keys);
        String canonicalRequest = method + "\n" + uri.getPath() + "\n" + canonicalQuery + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hexPayloadHash;
        String credentialScope = shortDate + "/cn-north-1/cv/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] kDate = hmac(sk.getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] kRegion = hmac(kDate, "cn-north-1");
        byte[] kService = hmac(kRegion, "cv");
        byte[] kSigning = hmac(kService, "request");
        String signature = HexFormat.of().formatHex(hmac(kSigning, stringToSign));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", uri.getHost());
        headers.put("X-Date", xDate);
        headers.put("X-Content-Sha256", hexPayloadHash);
        headers.put("Authorization", "HMAC-SHA256 Credential=" + ak + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return headers;
    }

    private String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> parts = new ArrayList<>(List.of(rawQuery.split("&")));
        parts.sort(Comparator.naturalOrder());
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            int idx = part.indexOf('=');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String value = idx >= 0 ? part.substring(idx + 1) : "";
            encoded.add(URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20") + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("&", encoded);
    }

    private String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String trimSuffixP(String value) {
        return value != null && value.endsWith("p") ? value.substring(0, value.length() - 1) : value;
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
        private String reqKey;
        private List<String> binaryDataBase64;
        private List<String> imageUrls;
        private String prompt;
        private Long seed;
        private String aspectRatio;
        private Integer frames;
    }

    @Data
    public static class ResponsePayload {
        private int code;
        private String message;
        private String requestId;
        private ResponseData data;
    }

    @Data
    public static class ResponseData {
        private String taskId;
    }

    @Data
    public static class ResponseTask {
        private int code;
        private TaskData data;
        private String message;
        private String requestId;
        private int status;
        private String timeElapsed;
    }

    @Data
    public static class TaskData {
        private List<Object> binaryDataBase64;
        private Object imageUrls;
        private String respData;
        private String status;
        private String videoUrl;
    }
}
