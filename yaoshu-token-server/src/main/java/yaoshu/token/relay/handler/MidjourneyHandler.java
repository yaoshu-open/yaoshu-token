package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.constant.MidjourneyConstants;
import yaoshu.token.pojo.dto.MidjourneyDTO;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.common.ModelUtils;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.Midjourney;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.PriceHelper;
import yaoshu.token.service.ChannelCacheService;
import yaoshu.token.service.DownloadService;
import yaoshu.token.service.MidjourneyService;
import yaoshu.token.service.QuotaService;
import yaoshu.token.service.UserService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Midjourney 中转处理器  * <p>
 * 两个独立端点：
 * <ul>
 * <li>RelayMidjourneyImage — 图片代理（从 MJ 任务 URL 流式获取图片返回客户端）</li>
 * <li>RelayMidjourneyNotify — 通知回调（处理 MJ 上游回调，解析任务状态）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidjourneyHandler {    private final MidjourneyService midjourneyService;
    private final QuotaService quotaService;
    private final UserService userService;
    private final LogMapper logMapper;
    private final TokenMapper tokenMapper;
    private final ModelUtils modelUtils;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Midjourney 图片代理      * <p>
     * 根据 taskId 查询 Midjourney 任务记录，获取 imageUrl 后流式返回图片。
     *
     * @param taskId MJ 任务 ID
     * @param resp  HttpServletResponse
     */
    public void relayMidjourneyImage(String taskId, HttpServletResponse resp) throws IOException {
        // 查询 MJ 任务记录
        Midjourney task = midjourneyService.getByOnlyMJId(taskId);
        if (task == null) {
            resp.setContentType("application/json");
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"midjourney_task_not_found\"}");
            return;
        }

        streamMidjourneyImage(task, resp);
    }

    /**
     * Midjourney 任务图片流式代理（已查得任务记录）。
     */
    public void streamMidjourneyImage(Midjourney task, HttpServletResponse resp) throws IOException {
        String imageUrl = task.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            resp.sendError(400, "{\"error\":\"image_url_empty\"}");
            return;
        }

        try {
            DownloadService.validateURL(imageUrl);
        } catch (Exception e) {
            resp.setStatus(403);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"request blocked: " + e.getMessage().replace("\"", "\\\"") + "\"}");
            return;
        }

        HttpClient httpClient;
        try {
            httpClient = buildHttpClient(task.getChannelId());
        } catch (IllegalArgumentException e) {
            resp.setStatus(400);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"proxy_url_invalid\"}");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<InputStream> imageResp;
        try {
            imageResp = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            log.error("Failed to fetch MJ image from {}: {}", imageUrl, e.getMessage());
            resp.sendError(500, "{\"error\":\"http_get_image_failed\"}");
            return;
        }

        if (imageResp.statusCode() != 200) {
            // 读取错误 body
            String errorBody;
            try {
                errorBody = new String(imageResp.body().readAllBytes());
            } catch (Exception e) {
                errorBody = "unknown error";
            }
            resp.setStatus(imageResp.statusCode());
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"" + errorBody.replace("\"", "\\\"") + "\"}");
            return;
        }

        // 设置 Content-Type（回退为 image/jpeg）
        String contentType = imageResp.headers().firstValue("Content-Type").orElse("image/jpeg");
        resp.setContentType(contentType);

        // 流式 copy 图片数据到响应
        try (InputStream in = imageResp.body();
             OutputStream out = resp.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }

    /**
     * Midjourney 通知回调处理。      */
    public Map<String, Object> relayMidjourneyNotify(MidjourneyDTO.MidjourneyTaskDTO midjRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (midjRequest == null) {
            response.put("code", 4);
            response.put("description", "bind_request_body_failed");
            return response;
        }

        String taskId = midjRequest.getId();
        if (taskId == null || taskId.isEmpty()) {
            response.put("code", 4);
            response.put("description", "midjourney_task_not_found");
            return response;
        }

        Midjourney task = midjourneyService.getByOnlyMJId(taskId);
        if (task == null) {
            response.put("code", 4);
            response.put("description", "midjourney_task_not_found");
            return response;
        }

        task.setProgress(midjRequest.getProgress());
        task.setPromptEn(midjRequest.getPromptEn());
        task.setState(midjRequest.getState());
        task.setSubmitTime(midjRequest.getSubmitTime());
        task.setStartTime(midjRequest.getStartTime());
        task.setFinishTime(midjRequest.getFinishTime());
        task.setImageUrl(midjRequest.getImageUrl());
        task.setVideoUrl(midjRequest.getVideoUrl());
        task.setVideoUrls(toJson(midjRequest.getVideoUrls()));
        task.setStatus(midjRequest.getStatus());
        task.setFailReason(midjRequest.getFailReason());
        task.setButtons(toJson(midjRequest.getButtons()));
        task.setProperties(toJson(midjRequest.getProperties()));
        midjourneyService.updateTask(task);
        return null;
    }

    /**
     * 查询单个 MJ 任务。      */
    public Object relayMidjourneyTaskFetch(HttpServletRequest request, String taskId) {
        int userId = toInt(request.getAttribute(ContextKeyConstants.USER_ID));
        Midjourney task = midjourneyService.getByMJId(userId, taskId);
        if (task == null) {
            return midjourneyError("task_no_found");
        }
        return coverMidjourneyTaskDto(task);
    }

    /**
     * 按条件查询 MJ 任务列表。      */
    public Object relayMidjourneyTaskListByCondition(HttpServletRequest request, Map<String, Object> body) {
        int userId = toInt(request.getAttribute(ContextKeyConstants.USER_ID));
        Object idsObj = body != null ? body.get("ids") : null;
        List<String> ids = idsObj instanceof List<?> list ? list.stream().map(String::valueOf).toList() : Collections.emptyList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return midjourneyService.getByMJIds(userId, ids).stream().map(this::coverMidjourneyTaskDto).toList();
    }

    /**
     * 查询 MJ 图像种子。      */
    public Object relayMidjourneyTaskImageSeed(HttpServletRequest request, String taskId) {
        int userId = toInt(request.getAttribute(ContextKeyConstants.USER_ID));
        Midjourney task = midjourneyService.getByMJId(userId, taskId);
        if (task == null) {
            return midjourneyError("task_no_found");
        }
        Channel channel = ChannelCacheService.cacheGetChannel(task.getChannelId());
        if (channel == null) {
            return midjourneyError("get_channel_info_failed");
        }
        if (channel.getStatus() == null || channel.getStatus() != CommonConstants.CHANNEL_STATUS_ENABLED) {
            return midjourneyError("该任务所属渠道已被禁用");
        }
        try {
            String url = safeBaseUrl(channel) + "/mj/task/" + taskId + "/image-seed";
            HttpRequest upstreamRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("mj-api-secret", extractFirstKey(channel))
                    .GET()
                    .build();
            HttpResponse<String> upstreamResp = buildHttpClient(channel.getId()).send(upstreamRequest, HttpResponse.BodyHandlers.ofString());
            return Convert.toJSONObject(upstreamResp.body());
        } catch (Exception e) {
            log.error("MJ image-seed fetch failed: {}", e.getMessage());
            return midjourneyError("do_request_failed");
        }
    }

    /**
     * MJ 提交类中继。      */
    public Object relayMidjourneySubmit(HttpServletRequest request, Map<String, Object> body, int relayMode) {
        MidjourneyDTO.MidjourneyRequest mjRequest = Convert.toJavaBean(body, MidjourneyDTO.MidjourneyRequest.class);
        boolean consumeQuota = true;
        int effectiveRelayMode = relayMode;
        if (effectiveRelayMode == RelayModeEnum.MIDJOURNEY_ACTION) {
            String err = MidjourneyService.coverPlusActionToNormalAction(mjRequest);
            if (err != null) return midjourneyError(err);
            effectiveRelayMode = RelayModeEnum.MIDJOURNEY_CHANGE;
        }
        Object actionError = applySubmitActionAndOriginChannel(request, mjRequest, effectiveRelayMode);
        if (actionError != null) return actionError;
        if (MidjourneyConstants.MJ_ACTION_INPAINT.equals(mjRequest.getAction())
                || MidjourneyConstants.MJ_ACTION_CUSTOM_ZOOM.equals(mjRequest.getAction())) {
            consumeQuota = false;
        }

        RelayInfo relayInfo = buildRelayInfo(request, MidjourneyService.covertMjpActionToModelName(mjRequest.getAction()));
        PriceData priceData = PriceHelper.modelPriceHelperPerCall(relayInfo);
        if (consumeQuota && userService.getUserQuota(relayInfo.getUserId()) - priceData.getQuota() < 0) {
            return midjourneyError("quota_not_enough");
        }

        Map<String, Object> upstreamBody = applyMidjourneyRequestToBody(body, mjRequest);
        MidjourneyUpstreamResult upstreamResult = doMidjourneyHttpRequest(request, upstreamBody, Duration.ofSeconds(60));
        if (upstreamResult.error() != null) return upstreamResult.error();
        MidjourneyDTO.MidjourneyResponse midjResponse = upstreamResult.response();
        Midjourney task = buildSubmitTask(relayInfo, midjResponse, mjRequest, priceData.getQuota());
        if (!isBillableSubmitCode(midjResponse.getCode())) {
            task.setFailReason(midjResponse.getDescription());
            consumeQuota = false;
        }
        byte[] responseBody = upstreamResult.responseBody();
        if (Integer.valueOf(21).equals(midjResponse.getCode())) {
            applyExistingTaskProperties(task, midjResponse);
            if (!MidjourneyConstants.MJ_ACTION_INPAINT.equals(mjRequest.getAction())
                    && !MidjourneyConstants.MJ_ACTION_CUSTOM_ZOOM.equals(mjRequest.getAction())) {
                responseBody = replaceCode(responseBody, 21, 1);
            }
        }
        if (Integer.valueOf(1).equals(midjResponse.getCode()) && MidjourneyConstants.MJ_ACTION_UPLOAD.equals(mjRequest.getAction())) {
            task.setProgress("100%");
            task.setStatus("SUCCESS");
        }
        midjourneyService.saveTask(task);
        if (Integer.valueOf(22).equals(midjResponse.getCode())) {
            responseBody = replaceCode(responseBody, 22, 1);
        }
        if (consumeQuota && upstreamResult.statusCode() == 200) {
            consumeMidjourneyQuota(relayInfo, priceData, mjRequest.getAction(), midjResponse.getResult());
        }
        return toResponseObject(responseBody, midjResponse);
    }

    /**
     * MJ 换脸中继。      */
    public Object relaySwapFace(HttpServletRequest request, Map<String, Object> body) {
        MidjourneyDTO.SwapFaceRequest swapFaceRequest = Convert.toJavaBean(body, MidjourneyDTO.SwapFaceRequest.class);
        if (swapFaceRequest.getSourceBase64() == null || swapFaceRequest.getSourceBase64().isEmpty()
                || swapFaceRequest.getTargetBase64() == null || swapFaceRequest.getTargetBase64().isEmpty()) {
            return midjourneyError("sour_base64_and_target_base64_is_required");
        }
        RelayInfo relayInfo = buildRelayInfo(request, MidjourneyService.covertMjpActionToModelName(MidjourneyConstants.MJ_ACTION_SWAP_FACE));
        PriceData priceData = PriceHelper.modelPriceHelperPerCall(relayInfo);
        if (userService.getUserQuota(relayInfo.getUserId()) - priceData.getQuota() < 0) {
            return midjourneyError("quota_not_enough");
        }
        MidjourneyUpstreamResult upstreamResult = doMidjourneyHttpRequest(request, body, Duration.ofSeconds(60));
        if (upstreamResult.error() != null) return upstreamResult.error();
        MidjourneyDTO.MidjourneyResponse midjResponse = upstreamResult.response();
        Midjourney task = buildSubmitTask(relayInfo, midjResponse, swapRequestAsMidjourneyRequest(), priceData.getQuota());
        midjourneyService.saveTask(task);
        if (upstreamResult.statusCode() == 200 && Integer.valueOf(1).equals(midjResponse.getCode())) {
            consumeMidjourneyQuota(relayInfo, priceData, MidjourneyConstants.MJ_ACTION_SWAP_FACE, midjResponse.getResult());
        }
        return midjResponse;
    }

    private Object applySubmitActionAndOriginChannel(HttpServletRequest request, MidjourneyDTO.MidjourneyRequest mjRequest, int relayMode) {
        if (relayMode == RelayModeEnum.MIDJOURNEY_VIDEO) mjRequest.setAction(MidjourneyConstants.MJ_ACTION_VIDEO);
        if (relayMode == RelayModeEnum.MIDJOURNEY_IMAGINE) {
            if (mjRequest.getPrompt() == null || mjRequest.getPrompt().isEmpty()) return midjourneyError("prompt_is_required");
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_IMAGINE);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_DESCRIBE) {
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_DESCRIBE);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_EDITS) {
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_EDITS);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_SHORTEN) {
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_SHORTEN);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_BLEND) {
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_BLEND);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_UPLOAD) {
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_UPLOAD);
        }

        String originMjId = null;
        if (relayMode == RelayModeEnum.MIDJOURNEY_CHANGE) {
            if (mjRequest.getTaskId() == null || mjRequest.getTaskId().isEmpty()) return midjourneyError("task_id_is_required");
            if (mjRequest.getAction() == null || mjRequest.getAction().isEmpty()) return midjourneyError("action_is_required");
            if (mjRequest.getIndex() == null || mjRequest.getIndex() == 0) return midjourneyError("index_is_required");
            originMjId = mjRequest.getTaskId();
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_SIMPLE_CHANGE) {
            if (mjRequest.getContent() == null || mjRequest.getContent().isEmpty()) return midjourneyError("content_is_required");
            MidjourneyDTO.MidjourneyRequest params = MidjourneyService.convertSimpleChangeParams(mjRequest.getContent());
            if (params == null) return midjourneyError("content_parse_failed");
            originMjId = params.getTaskId();
            mjRequest.setAction(params.getAction());
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_MODAL) {
            originMjId = mjRequest.getTaskId();
            mjRequest.setAction(MidjourneyConstants.MJ_ACTION_MODAL);
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_VIDEO && mjRequest.getTaskId() != null && !mjRequest.getTaskId().isEmpty()) {
            originMjId = mjRequest.getTaskId();
        }
        if (originMjId == null || originMjId.isEmpty()) return null;
        Midjourney originTask = midjourneyService.getByMJId(toInt(request.getAttribute(ContextKeyConstants.USER_ID)), originMjId);
        if (originTask == null) return midjourneyError("task_not_found");
        Channel channel = ChannelCacheService.cacheGetChannel(originTask.getChannelId());
        if (channel == null) return midjourneyError("get_channel_info_failed");
        if (channel.getStatus() == null || channel.getStatus() != CommonConstants.CHANNEL_STATUS_ENABLED) return midjourneyError("该任务所属渠道已被禁用");
        applyChannelToRequest(request, channel);
        mjRequest.setPrompt(originTask.getPrompt());
        return null;
    }

    private MidjourneyDTO.MidjourneyRequest swapRequestAsMidjourneyRequest() {
        MidjourneyDTO.MidjourneyRequest req = new MidjourneyDTO.MidjourneyRequest();
        req.setAction(MidjourneyConstants.MJ_ACTION_SWAP_FACE);
        req.setPrompt("InsightFace");
        return req;
    }

    private Map<String, Object> applyMidjourneyRequestToBody(Map<String, Object> body, MidjourneyDTO.MidjourneyRequest request) {
        Map<String, Object> result = new LinkedHashMap<>(body != null ? body : Map.of());
        if (request.getPrompt() != null) result.put("prompt", request.getPrompt());
        if (request.getAction() != null) result.put("action", request.getAction());
        if (request.getTaskId() != null) result.put("taskId", request.getTaskId());
        if (request.getIndex() != null) result.put("index", request.getIndex());
        if (request.getContent() != null) result.put("content", request.getContent());
        return result;
    }

    private MidjourneyUpstreamResult doMidjourneyHttpRequest(HttpServletRequest request, Map<String, Object> body, Duration timeout) {
        try {
            Channel channel = currentChannel(request);
            if (channel == null) return MidjourneyUpstreamResult.error(midjourneyError("get_channel_info_failed"));
            Map<String, Object> requestBody = new LinkedHashMap<>(body != null ? body : Map.of());
            requestBody.remove("accountFilter");
            requestBody.remove("notifyHook");
            Object prompt = requestBody.get("prompt");
            if (prompt instanceof String promptText) {
                requestBody.put("prompt", promptText.replace("--fast", "").replace("--relax", "").replace("--turbo", ""));
            }
            String fullRequestUrl = safeBaseUrl(channel) + getMjRequestPath(request.getRequestURI());
            HttpRequest upstreamRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fullRequestUrl))
                    .timeout(timeout)
                    .header("Content-Type", defaultHeader(request, "Content-Type", "application/json"))
                    .header("Accept", defaultHeader(request, "Accept", "application/json"))
                    .header("mj-api-secret", extractFirstKey(channel))
                    .method(request.getMethod(), HttpRequest.BodyPublishers.ofString(toJson(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> upstreamResp = buildHttpClient(channel.getId()).send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
            byte[] responseBody = upstreamResp.body() != null ? upstreamResp.body() : new byte[0];
            if (responseBody.length == 0) return MidjourneyUpstreamResult.error(midjourneyError("empty_response_body"));
            MidjourneyDTO.MidjourneyResponse response = parseMidjourneyResponse(responseBody);
            if (response == null) return MidjourneyUpstreamResult.error(midjourneyError("unmarshal_response_body_failed"));
            return new MidjourneyUpstreamResult(upstreamResp.statusCode(), response, responseBody, null);
        } catch (Exception e) {
            log.error("MJ upstream request failed: {}", e.getMessage());
            return MidjourneyUpstreamResult.error(midjourneyError("do_request_failed"));
        }
    }

    private MidjourneyDTO.MidjourneyResponse parseMidjourneyResponse(byte[] responseBody) {
        try {
            return Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), MidjourneyDTO.MidjourneyResponse.class);
        } catch (Exception ignored) {
            try {
                MidjourneyDTO.MidjourneyUploadResponse uploadResponse = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), MidjourneyDTO.MidjourneyUploadResponse.class);
                MidjourneyDTO.MidjourneyResponse response = new MidjourneyDTO.MidjourneyResponse();
                response.setCode(uploadResponse.getCode());
                response.setDescription(uploadResponse.getDescription());
                response.setProperties(Map.of("result", uploadResponse.getResult() != null ? uploadResponse.getResult() : List.of()));
                response.setResult(uploadResponse.getResult() != null && !uploadResponse.getResult().isEmpty() ? uploadResponse.getResult().get(0) : "");
                return response;
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private Midjourney buildSubmitTask(RelayInfo relayInfo, MidjourneyDTO.MidjourneyResponse response,
                                       MidjourneyDTO.MidjourneyRequest request, int quota) {
        long now = System.currentTimeMillis();
        Midjourney task = new Midjourney();
        task.setUserId(relayInfo.getUserId());
        task.setCode(response.getCode());
        task.setAction(request.getAction());
        task.setMjId(response.getResult());
        task.setPrompt(request.getPrompt());
        task.setPromptEn("");
        task.setDescription(response.getDescription());
        task.setState("");
        task.setSubmitTime(now);
        task.setStartTime(MidjourneyConstants.MJ_ACTION_SWAP_FACE.equals(request.getAction()) ? now : 0L);
        task.setFinishTime(0L);
        task.setImageUrl("");
        task.setStatus("");
        task.setProgress("0%");
        task.setFailReason("");
        task.setChannelId(relayInfo.getChannelId());
        task.setQuota(quota);
        return task;
    }

    @SuppressWarnings("unchecked")
    private void applyExistingTaskProperties(Midjourney task, MidjourneyDTO.MidjourneyResponse response) {
        if (!(response.getProperties() instanceof Map<?, ?> properties)) return;
        Object imageUrl = properties.get("imageUrl");
        Object status = properties.get("status");
        if (imageUrl instanceof String imageUrlText && status instanceof String statusText) {
            task.setImageUrl(imageUrlText);
            task.setStatus(statusText);
            if ("SUCCESS".equals(statusText)) {
                long now = System.currentTimeMillis();
                task.setProgress("100%");
                task.setStartTime(now);
                task.setFinishTime(now);
                response.setCode(1);
            }
        }
    }

    private boolean isBillableSubmitCode(Integer code) {
        return Integer.valueOf(1).equals(code) || Integer.valueOf(21).equals(code) || Integer.valueOf(22).equals(code);
    }

    private byte[] replaceCode(byte[] responseBody, int oldCode, int newCode) {
        return new String(responseBody, StandardCharsets.UTF_8)
                .replace("\"code\":" + oldCode, "\"code\":" + newCode)
                .getBytes(StandardCharsets.UTF_8);
    }

    private Object toResponseObject(byte[] responseBody, MidjourneyDTO.MidjourneyResponse fallback) {
        try {
            return Convert.toJSONObject(responseBody);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void consumeMidjourneyQuota(RelayInfo relayInfo, PriceData priceData, String action, String resultId) {
        int quota = priceData.getQuota();
        quotaService.postConsumeQuota(relayInfo, -quota, 0);
        if (!relayInfo.isTokenUnlimited()) {
            tokenMapper.decreaseRemainQuota(relayInfo.getTokenId(), quota);
        }
        tokenMapper.increaseUsedQuota(relayInfo.getTokenId(), quota);
        recordMidjourneyConsumeLog(relayInfo, priceData, action, resultId);
        modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_USED_QUOTA, relayInfo.getUserId(), quota);
        modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_REQUEST_COUNT, relayInfo.getUserId(), 1);
        modelUtils.addNewRecord(ModelUtils.BATCH_UPDATE_TYPE_CHANNEL_USED_QUOTA, relayInfo.getChannelId(), quota);
    }

    private void recordMidjourneyConsumeLog(RelayInfo relayInfo, PriceData priceData, String action, String resultId) {
        try {
            Log entry = new Log();
            entry.setUserId(relayInfo.getUserId());
            entry.setType(2);
            entry.setChannelId(relayInfo.getChannelId());
            entry.setModelName(MidjourneyService.covertMjpActionToModelName(action));
            entry.setTokenId(relayInfo.getTokenId());
            entry.setTokenName(relayInfo.getTokenKey());
            entry.setQuota(priceData.getQuota());
            entry.setGroup(relayInfo.getUsingGroup());
            entry.setCreatedAt(System.currentTimeMillis() / 1000);
            entry.setContent("模型固定价格 " + priceData.getModelPrice() + "，分组倍率 "
                    + (priceData.getGroupRatioInfo() != null ? priceData.getGroupRatioInfo().getGroupRatio() : 1.0)
                    + "，操作 " + action + (resultId != null ? "，ID " + resultId : ""));
            entry.setOther(toJson(Map.of("price_data", priceData.toSetting(), "action", action)));
            logMapper.insert(entry);
        } catch (Exception e) {
            log.error("记录 MJ 消费日志失败: {}", e.getMessage());
        }
    }

    private RelayInfo buildRelayInfo(HttpServletRequest request, String modelName) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(java.time.LocalDateTime.now());
        info.setRequestURLPath(request.getRequestURI());
        info.setUserId(toInt(request.getAttribute(ContextKeyConstants.USER_ID)));
        info.setTokenId(toInt(request.getAttribute(ContextKeyConstants.TOKEN_ID)));
        info.setTokenKey(stringAttr(request, ContextKeyConstants.TOKEN_KEY));
        info.setTokenUnlimited(Boolean.TRUE.equals(request.getAttribute(ContextKeyConstants.TOKEN_UNLIMITED_QUOTA)));
        info.setTokenGroup(stringAttr(request, ContextKeyConstants.TOKEN_GROUP));
        info.setUserGroup(stringAttr(request, ContextKeyConstants.USER_GROUP));
        info.setUsingGroup(defaultString(stringAttr(request, ContextKeyConstants.USING_GROUP), info.getTokenGroup()));
        info.setChannelId(toInt(request.getAttribute(ContextKeyConstants.CHANNEL_ID)));
        info.setChannelType(toInt(request.getAttribute(ContextKeyConstants.CHANNEL_TYPE)));
        info.setChannelBaseUrl(stringAttr(request, ContextKeyConstants.CHANNEL_BASE_URL));
        info.setApiKey(stringAttr(request, ContextKeyConstants.CHANNEL_KEY));
        info.setOriginModelName(modelName);
        info.setUpstreamModelName(modelName);
        return info;
    }

    private Channel currentChannel(HttpServletRequest request) {
        int channelId = toInt(request.getAttribute(ContextKeyConstants.CHANNEL_ID));
        Channel channel = ChannelCacheService.cacheGetChannel(channelId);
        if (channel != null) return channel;
        String baseUrl = stringAttr(request, ContextKeyConstants.CHANNEL_BASE_URL);
        String key = stringAttr(request, ContextKeyConstants.CHANNEL_KEY);
        if (baseUrl == null && key == null) return null;
        Channel fallback = new Channel();
        fallback.setId(channelId);
        fallback.setBaseUrl(baseUrl);
        fallback.setKey(key);
        return fallback;
    }

    private void applyChannelToRequest(HttpServletRequest request, Channel channel) {
        request.setAttribute(ContextKeyConstants.CHANNEL_ID, channel.getId());
        request.setAttribute(ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        request.setAttribute(ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());
        request.setAttribute(ContextKeyConstants.CHANNEL_KEY, extractFirstKey(channel));
    }

    private String getMjRequestPath(String path) {
        if (path != null && path.contains("/mj-")) {
            String[] parts = path.split("/mj/");
            if (parts.length >= 2) return "/mj/" + parts[1];
        }
        return path != null ? path : "";
    }

    private String defaultHeader(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private String stringAttr(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String defaultString(String value, String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private record MidjourneyUpstreamResult(int statusCode, MidjourneyDTO.MidjourneyResponse response,
                                            byte[] responseBody, Map<String, Object> error) {
        static MidjourneyUpstreamResult error(Map<String, Object> error) {
            return new MidjourneyUpstreamResult(500, null, new byte[0], error);
        }
    }

    private MidjourneyDTO.MidjourneyTaskDTO coverMidjourneyTaskDto(Midjourney task) {
        MidjourneyDTO.MidjourneyTaskDTO dto = new MidjourneyDTO.MidjourneyTaskDTO();
        dto.setId(task.getMjId());
        dto.setProgress(task.getProgress());
        dto.setPromptEn(task.getPromptEn());
        dto.setState(task.getState());
        dto.setSubmitTime(task.getSubmitTime());
        dto.setStartTime(task.getStartTime());
        dto.setFinishTime(task.getFinishTime());
        dto.setImageUrl(task.getImageUrl());
        dto.setVideoUrl(task.getVideoUrl());
        dto.setStatus(task.getStatus());
        dto.setFailReason(task.getFailReason());
        dto.setAction(task.getAction());
        dto.setDescription(task.getDescription());
        dto.setPrompt(task.getPrompt());
        dto.setButtons(fromJson(task.getButtons(), Object.class));
        dto.setVideoUrls(parseVideoUrls(task.getVideoUrls()));
        dto.setProperties(fromJson(task.getProperties(), Object.class));
        return dto;
    }

    private Map<String, Object> midjourneyError(String description) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", 4);
        error.put("description", description);
        return error;
    }

    private HttpClient buildHttpClient(Integer channelId) {
        if (channelId == null) return HTTP_CLIENT;
        Channel channel = ChannelCacheService.cacheGetChannel(channelId);
        if (channel == null) return HTTP_CLIENT;
        String proxy = parseProxyFromSetting(channel.getSetting());
        if (proxy == null || proxy.isBlank()) return HTTP_CLIENT;
        try {
            URI proxyUri = proxy.startsWith("http://") || proxy.startsWith("https://")
                    ? URI.create(proxy)
                    : URI.create("http://" + proxy);
            int port = proxyUri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(proxyUri.getScheme()) ? 443 : 80;
            }
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port)))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("proxy_url_invalid", e);
        }
    }

    private String parseProxyFromSetting(String channelSetting) {
        if (channelSetting == null || channelSetting.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> setting = Convert.toJSONObject(channelSetting);
            Object proxy = setting.get("proxy");
            return proxy instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<MidjourneyDTO.ImgUrls> parseVideoUrls(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return (List<MidjourneyDTO.ImgUrls>) Convert.toJavaBean(json, List.class);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) return null;
        try {
            return Convert.toJavaBean(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String safeBaseUrl(Channel channel) {
        String baseUrl = channel.getBaseUrl();
        return baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "";
    }

    private String extractFirstKey(Channel channel) {
        if (channel.getKey() == null || channel.getKey().isEmpty()) return "";
        String key = channel.getKey().trim();
        int newlineIdx = key.indexOf('\n');
        return newlineIdx > 0 ? key.substring(0, newlineIdx).trim() : key;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return Convert.toJSONString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
