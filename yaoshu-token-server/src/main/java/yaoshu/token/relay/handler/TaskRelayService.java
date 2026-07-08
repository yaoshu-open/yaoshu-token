package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.dto.PriceData;
import yaoshu.token.pojo.dto.TaskBillingContext;
import yaoshu.token.pojo.dto.TaskPrivateData;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.ModelMappedHelper;
import yaoshu.token.relay.helper.PriceHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ChannelCacheService;
import yaoshu.token.service.ChannelSelectService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.service.RetryParam;
import yaoshu.token.service.TaskBillingService;
import yaoshu.token.service.TaskService;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中转任务编排服务  * <p>
 * 核心职责：任务模式的 AI API 中转编排（视频生成/音乐生成等异步任务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRelayService {

    private final TaskService taskService;
    private final ChannelService channelService;
    private final BillingService billingService;
    private final TaskBillingService taskBillingService;

    /**
     * 处理基于已有任务的提交（remix / continuation）。      */
    public boolean resolveOriginTask(HttpServletRequest request, RelayInfo info) {
        String path = request.getRequestURI();
        if (path == null || !path.contains("/v1/videos/")) {
            return false;
        }

        boolean isRemix = path.endsWith("/remix");
        boolean isContinuation = path.endsWith("/continuation");
        if (!isRemix && !isContinuation) {
            return false;
        }

        String taskId = extractTaskIdFromPath(path);
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("video_id is required");
        }

        int userId = resolveUserId(request, info);
        Task originTask = taskService.getByTaskId(userId, taskId);
        if (originTask == null) {
            throw new IllegalArgumentException("task_origin_not_exist");
        }

        info.setTaskAction(isRemix ? TaskConstants.TASK_ACTION_REMIX : "continuation");
        info.setOriginTaskID(taskId);
        if (info.getOriginModelName() == null || info.getOriginModelName().isEmpty()) {
            info.setOriginModelName(resolveOriginModelName(originTask));
        }

        Channel channel = ChannelCacheService.cacheGetChannel(originTask.getChannelId());
        if (channel == null) {
            throw new IllegalArgumentException("channel_not_found");
        }
        if (channel.getStatus() == null || channel.getStatus() != CommonConstants.CHANNEL_STATUS_ENABLED) {
            throw new IllegalArgumentException("task_channel_disable");
        }
        info.setLockedChannel(channel);
        if (originTask.getChannelId() != null && originTask.getChannelId() != info.getChannelId()) {
            info.setChannelId(originTask.getChannelId());
            info.setChannelType(channel.getType() != null ? channel.getType() : 0);
            info.setChannelBaseUrl(channel.getBaseUrl());
            info.setApiKey(channel.getKey());
        }
        return true;
    }

    /**
     * 控制器级任务提交闭环。      */
    public TaskSubmitResult relayTask(HttpServletRequest request, RelayInfo info) {
        resolveOriginTask(request, info);
        TaskSubmitResult result = null;
        TaskRelayException taskErr = null;
        RetryParam retryParam = new RetryParam(request, info.getTokenGroup(), info.getOriginModelName());
        info.setRetryIndex(0);
        info.setLastError(null);

        for (; retryParam.getRetry() <= CommonConstants.retryTimes; retryParam.increaseRetry()) {
            Channel channel;
            try {
                channel = selectTaskChannel(request, info, retryParam);
                addUsedChannel(request, channel.getId());
                result = relayTaskSubmit(request, info);
                taskErr = null;
                break;
            } catch (TaskRelayException e) {
                taskErr = e;
                if (!shouldRetryTaskRelay(request, e, CommonConstants.retryTimes - retryParam.getRetry())) {
                    break;
                }
            } catch (Exception e) {
                taskErr = taskError(e, "do_request_failed", 500, false);
                if (!shouldRetryTaskRelay(request, taskErr, CommonConstants.retryTimes - retryParam.getRetry())) {
                    break;
                }
            }
        }

        if (taskErr != null) {
            if (info.getBilling() != null) {
                info.getBilling().refund();
            }
            throw taskErr;
        }
        if (result == null) {
            throw taskError("task_submit_failed", "task_submit_failed", 500, false);
        }

        billingService.settleBilling(info, result.getQuota());
        logTaskConsumption(request, info, result.getQuota());
        Task task = buildTaskRecord(request, info, result.getPlatform(), result.getUpstreamTaskID(), result.getTaskData(), result.getQuota());
        taskService.insert(task);
        return result;
    }

    /**
     * 提交任务到上游。      */
    public TaskSubmitResult relayTaskSubmit(HttpServletRequest request, RelayInfo info) throws Exception {
        info.setStartTime(info.getStartTime() != null ? info.getStartTime() : LocalDateTime.now());
        initChannelMetaFromRequest(request, info);
        String platform = resolvePlatform(request, info);
        IAdaptor.ITaskAdaptor adaptor = RelayAdaptor.getTaskAdaptor(platform);
        if (adaptor == null) {
            throw taskError("invalid api platform: " + platform, "invalid_api_platform", 400, true);
        }

        adaptor.init(info);
        Object taskErr = adaptor.validateRequestAndSetAction(info);
        if (taskErr != null) {
            throw toTaskRelayException(taskErr);
        }

        String modelName = info.getOriginModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = resolveModelName(platform, info.getTaskAction());
        }
        info.setOriginModelName(modelName);
        info.setUpstreamModelName(modelName);
        ModelMappedHelper.apply(info, stringAttr(request, ContextKeyConstants.CHANNEL_MODEL_MAPPING), info.getRequest());

        if (info.getPublicTaskID() == null || info.getPublicTaskID().isEmpty()) {
            info.setPublicTaskID(TaskService.generateTaskID());
        }

        PriceData priceData = PriceHelper.modelPriceHelperPerCall(info);
        Map<String, Double> estimatedRatios = adaptor.estimateBilling(info);
        if (estimatedRatios != null && !estimatedRatios.isEmpty()) {
            estimatedRatios.forEach(priceData::addOtherRatio);
        }
        applyOtherRatios(priceData);

        if (info.getBilling() == null && !priceData.isFreeModel()) {
            info.setForcePreConsume(true);
            String billingError = billingService.preConsumeBilling(info, priceData.getQuota());
            if (billingError != null) {
                throw taskError(billingError, "insufficient_quota", 402, true);
            }
        }

        Object requestBody = adaptor.buildRequestBody(info);
        HttpResponse<?> resp = adaptor.doRequest(info, requestBody);
        if (resp != null && (resp.statusCode() < 200 || resp.statusCode() >= 300)) {
            throw taskError("fail_to_fetch_task: " + resp.statusCode(), "fail_to_fetch_task", resp.statusCode(), false);
        }
        IAdaptor.TaskDoResponseResult submitResult = adaptor.doResponse(info, resp);
        if (submitResult.isError()) {
            throw toTaskRelayException(submitResult.getError());
        }

        int finalQuota = priceData.getQuota();
        Map<String, Double> adjustedRatios = adaptor.adjustBillingOnSubmit(info, submitResult.getTaskData());
        if (adjustedRatios != null && !adjustedRatios.isEmpty()) {
            finalQuota = recalcQuotaFromRatios(priceData, adjustedRatios);
            priceData.setOtherRatios(new LinkedHashMap<>(adjustedRatios));
            priceData.setQuota(finalQuota);
        }
        return new TaskSubmitResult(submitResult.getTaskID(), submitResult.getTaskData(), platform, finalQuota, info.getPublicTaskID());
    }

    /**
     * 拉取本地任务结果。      */
    public Map<String, Object> taskFetch(HttpServletRequest request, RelayInfo info) throws Exception {
        String path = request.getRequestURI();
        String taskId = extractTaskIdFromPath(path);
        if (taskId == null || taskId.isEmpty()) {
            taskId = request.getParameter("task_id");
        }
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("task_id is required");
        }
        Task task = taskService.getByTaskId(resolveUserId(request, info), taskId);
        if (task == null) {
            throw new IllegalArgumentException("task_not_exist");
        }
        if (path != null && path.startsWith("/v1/videos/")) {
            IAdaptor.ITaskAdaptor adaptor = RelayAdaptor.getTaskAdaptor(task.getPlatform());
            if (adaptor instanceof IAdaptor.OpenAIVideoConverter converter) {
                return Convert.toJSONObject(new String(converter.convertToOpenAIVideo(task)));
            }
            throw new IllegalStateException("not_implemented:" + task.getPlatform());
        }
        return taskResponse(taskModel2Dto(task));
    }

    /**
     * Suno 批量查询响应。      */
    public Map<String, Object> sunoFetch(HttpServletRequest request, RelayInfo info, List<String> ids) {
        List<Task> tasks = ids == null || ids.isEmpty()
                ? List.of()
                : taskService.getByTaskIds(resolveUserId(request, info), ids);
        return taskResponse(tasks.stream().map(this::taskModel2Dto).toList());
    }

    /**
     * Suno 单任务查询响应。      */
    public Map<String, Object> sunoFetchById(HttpServletRequest request, RelayInfo info, String taskId) {
        Task task = taskService.getByTaskId(resolveUserId(request, info), taskId);
        if (task == null) {
            throw new IllegalArgumentException("task_not_exist");
        }
        return taskResponse(taskModel2Dto(task));
    }

    private Task buildTaskRecord(HttpServletRequest request, RelayInfo info, String platform, String upstreamTaskId, byte[] taskData, int quota) {
        long now = Instant.now().getEpochSecond();
        Task task = new Task();
        task.setTaskId(info.getPublicTaskID());
        task.setPlatform(platform);
        task.setUserId(resolveUserId(request, info));
        task.setGroup(info.getUsingGroup() != null ? info.getUsingGroup() : info.getUserGroup());
        task.setChannelId(info.getChannelId());
        task.setQuota(quota);
        task.setAction(info.getTaskAction());
        task.setStatus(TaskConstants.TASK_STATUS_SUBMITTED);
        task.setSubmitTime(now);
        task.setProgress("10%");
        task.setData(taskData != null ? new String(taskData) : null);
        task.setPrivateData(Convert.toJSONString(buildPrivateData(info, upstreamTaskId)));
        return task;
    }

    private TaskPrivateData buildPrivateData(RelayInfo info, String upstreamTaskId) {
        TaskPrivateData privateData = new TaskPrivateData();
        privateData.setKey(info.getApiKey());
        privateData.setUpstreamTaskId(upstreamTaskId);
        privateData.setBillingSource(info.getBillingSource());
        privateData.setSubscriptionId(info.getSubscriptionId() > 0 ? info.getSubscriptionId() : null);
        privateData.setTokenId(info.getTokenId() > 0 ? info.getTokenId() : null);
        TaskBillingContext billingContext = new TaskBillingContext();
        billingContext.setOriginModelName(info.getOriginModelName());
        billingContext.setPerCallBilling(true);
        if (info.getPriceData() != null) {
            billingContext.setOtherRatios(info.getPriceData().getOtherRatios());
            billingContext.setModelPrice(info.getPriceData().getModelPrice());
            billingContext.setModelRatio(info.getPriceData().getModelRatio());
            if (info.getPriceData().getGroupRatioInfo() != null) {
                billingContext.setGroupRatio(info.getPriceData().getGroupRatioInfo().getGroupRatio());
            }
        }
        privateData.setBillingContext(billingContext);
        return privateData;
    }

    private Map<String, Object> taskModel2Dto(Task task) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", task.getId());
        dto.put("created_at", task.getCreatedAt());
        dto.put("updated_at", task.getUpdatedAt());
        dto.put("task_id", task.getTaskId());
        dto.put("platform", task.getPlatform());
        dto.put("user_id", task.getUserId());
        dto.put("group", task.getGroup());
        dto.put("channel_id", task.getChannelId());
        dto.put("quota", task.getQuota());
        dto.put("action", task.getAction());
        dto.put("status", task.getStatus());
        dto.put("fail_reason", task.getFailReason());
        dto.put("result_url", getResultUrl(task));
        dto.put("submit_time", task.getSubmitTime());
        dto.put("start_time", task.getStartTime());
        dto.put("finish_time", task.getFinishTime());
        dto.put("progress", task.getProgress());
        dto.put("properties", task.getProperties());
        dto.put("data", task.getData());
        return dto;
    }

    private Map<String, Object> taskResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "success");
        response.put("data", data);
        return response;
    }

    private String getResultUrl(Task task) {
        TaskPrivateData privateData = parsePrivateData(task.getPrivateData());
        return privateData != null && privateData.getResultUrl() != null ? privateData.getResultUrl() : task.getFailReason();
    }

    private TaskPrivateData parsePrivateData(String privateDataJson) {
        if (privateDataJson == null || privateDataJson.isEmpty()) return null;
        try {
            return Convert.toJavaBean(privateDataJson, TaskPrivateData.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void initChannelMetaFromRequest(HttpServletRequest request, RelayInfo info) {
        info.setChannelId(toInt(request.getAttribute(ContextKeyConstants.CHANNEL_ID), info.getChannelId()));
        info.setChannelType(toInt(request.getAttribute(ContextKeyConstants.CHANNEL_TYPE), info.getChannelType()));
        info.setApiType(info.getChannelType());
        info.setChannelBaseUrl(stringAttr(request, ContextKeyConstants.CHANNEL_BASE_URL));
        info.setApiKey(stringAttr(request, ContextKeyConstants.CHANNEL_KEY));
        info.setOrganization(stringAttr(request, ContextKeyConstants.CHANNEL_ORGANIZATION));
        info.setTokenId(toInt(request.getAttribute(ContextKeyConstants.TOKEN_ID), info.getTokenId()));
        info.setUserId(toInt(request.getAttribute(ContextKeyConstants.USER_ID), info.getUserId()));
        info.setTokenGroup(defaultString(info.getTokenGroup(), stringAttr(request, ContextKeyConstants.TOKEN_GROUP)));
        info.setUserGroup(defaultString(info.getUserGroup(), stringAttr(request, ContextKeyConstants.USER_GROUP)));
        info.setUsingGroup(defaultString(stringAttr(request, ContextKeyConstants.USING_GROUP), info.getTokenGroup()));
        info.setTokenKey(stringAttr(request, ContextKeyConstants.TOKEN_KEY));
        info.setTokenUnlimited(Boolean.TRUE.equals(request.getAttribute(ContextKeyConstants.TOKEN_UNLIMITED_QUOTA)));
        info.setRequestURLPath(request.getRequestURI());
        info.setClientHeaders(extractHeaders(request));
        String originalModel = stringAttr(request, ContextKeyConstants.ORIGINAL_MODEL);
        if ((info.getOriginModelName() == null || info.getOriginModelName().isEmpty()) && originalModel != null) {
            info.setOriginModelName(originalModel);
        }
    }

    private Channel selectTaskChannel(HttpServletRequest request, RelayInfo info, RetryParam retryParam) {
        info.setRetryIndex(retryParam.getRetry());
        if (info.getLockedChannel() instanceof Channel lockedChannel) {
            setupRequestForSelectedChannel(request, lockedChannel);
            applyChannelToRelayInfo(info, lockedChannel);
            return lockedChannel;
        }
        Object[] selected = ChannelSelectService.cacheGetRandomSatisfiedChannel(retryParam, channelService);
        Channel channel = selected != null && selected.length > 0 && selected[0] instanceof Channel c ? c : null;
        if (selected != null && selected.length > 1 && selected[1] != null) {
            info.setUsingGroup(String.valueOf(selected[1]));
        }
        if (channel == null) {
            throw taskError("get_channel_failed", "get_channel_failed", 500, true);
        }
        setupRequestForSelectedChannel(request, channel);
        applyChannelToRelayInfo(info, channel);
        return channel;
    }

    private void setupRequestForSelectedChannel(HttpServletRequest request, Channel channel) {
        request.setAttribute(ContextKeyConstants.CHANNEL_ID, channel.getId());
        request.setAttribute(ContextKeyConstants.CHANNEL_NAME, channel.getName());
        request.setAttribute(ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        request.setAttribute(ContextKeyConstants.CHANNEL_CREATE_TIME, channel.getCreatedTime());
        request.setAttribute(ContextKeyConstants.CHANNEL_SETTING, channel.getSetting());
        request.setAttribute(ContextKeyConstants.CHANNEL_OTHER_SETTING, channel.getOtherInfo());
        request.setAttribute(ContextKeyConstants.CHANNEL_MODEL_MAPPING, channel.getModelMapping());
        request.setAttribute(ContextKeyConstants.CHANNEL_STATUS_CODE_MAPPING, channel.getStatusCodeMapping());
        request.setAttribute(ContextKeyConstants.CHANNEL_AUTO_BAN, channel.getAutoBan());
        request.setAttribute(ContextKeyConstants.CHANNEL_KEY, extractFirstKey(channel));
        request.setAttribute(ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());
        request.setAttribute(ContextKeyConstants.CHANNEL_IS_MULTI_KEY, false);
        if (channel.getOpenaiOrganization() != null && !channel.getOpenaiOrganization().isEmpty()) {
            request.setAttribute(ContextKeyConstants.CHANNEL_ORGANIZATION, channel.getOpenaiOrganization());
        }
    }

    private void applyChannelToRelayInfo(RelayInfo info, Channel channel) {
        info.setChannelId(channel.getId() != null ? channel.getId() : 0);
        info.setChannelType(channel.getType() != null ? channel.getType() : 0);
        info.setApiType(info.getChannelType());
        info.setChannelBaseUrl(channel.getBaseUrl());
        info.setApiKey(extractFirstKey(channel));
        info.setOrganization(channel.getOpenaiOrganization());
    }

    private void applyOtherRatios(PriceData priceData) {
        if (priceData.getOtherRatios() == null || priceData.getOtherRatios().isEmpty()) {
            return;
        }
        int quota = priceData.getQuota();
        for (Double ratio : priceData.getOtherRatios().values()) {
            if (ratio != null && ratio > 0 && ratio != 1.0) {
                quota = (int) (quota * ratio);
            }
        }
        priceData.setQuota(quota);
    }

    private int recalcQuotaFromRatios(PriceData priceData, Map<String, Double> adjustedRatios) {
        int baseQuota = priceData.getQuota();
        if (priceData.getOtherRatios() != null) {
            for (Double ratio : priceData.getOtherRatios().values()) {
                if (ratio != null && ratio > 0 && ratio != 1.0) {
                    baseQuota = (int) (baseQuota / ratio);
                }
            }
        }
        double result = baseQuota;
        for (Double ratio : adjustedRatios.values()) {
            if (ratio != null && ratio > 0 && ratio != 1.0) {
                result *= ratio;
            }
        }
        return (int) result;
    }

    private void logTaskConsumption(HttpServletRequest request, RelayInfo info, int quota) {
        Map<String, Object> other = new LinkedHashMap<>();
        if (info.getPriceData() != null) {
            other.put("price_data", info.getPriceData().toSetting());
            other.put("other_ratios", info.getPriceData().getOtherRatios());
        }
        taskBillingService.logTaskConsumption(
                resolveUserId(request, info),
                info.getChannelId(),
                info.getTokenId(),
                stringAttr(request, "token_name"),
                info.getOriginModelName(),
                quota,
                defaultString(info.getUsingGroup(), info.getUserGroup()),
                other
        );
    }

    private boolean shouldRetryTaskRelay(HttpServletRequest request, TaskRelayException taskErr, int retryTimes) {
        if (taskErr == null || retryTimes <= 0) return false;
        if (request.getAttribute(ContextKeyConstants.TOKEN_SPECIFIC_CHANNEL_ID) != null) return false;
        int statusCode = taskErr.getStatusCode();
        if (statusCode == 429 || statusCode == 307) return true;
        if (statusCode == 400 || statusCode == 408) return false;
        if (taskErr.isLocalError()) return false;
        if (statusCode >= 200 && statusCode < 300) return false;
        return statusCode >= 500 || statusCode < 100 || statusCode > 599;
    }

    @SuppressWarnings("unchecked")
    private TaskRelayException toTaskRelayException(Object error) {
        if (error instanceof TaskRelayException e) return e;
        if (error instanceof Map<?, ?> map) {
            Object codeObj = map.get("code");
            String code = codeObj != null ? String.valueOf(codeObj) : "task_error";
            Object messageObj = map.get("message");
            String message = messageObj != null ? String.valueOf(messageObj) : code;
            int statusCode = toInt(map.get("statusCode"), 500);
            if (map.containsKey("status_code")) statusCode = toInt(map.get("status_code"), statusCode);
            boolean localError = Boolean.TRUE.equals(map.get("localError")) || Boolean.TRUE.equals(map.get("local_error"));
            return taskError(message, code, statusCode, localError);
        }
        return taskError(Convert.toJSONString(error), "task_error", 500, false);
    }

    private TaskRelayException taskError(Throwable cause, String code, int statusCode, boolean localError) {
        return new TaskRelayException(cause.getMessage(), code, statusCode, localError, cause);
    }

    private TaskRelayException taskError(String message, String code, int statusCode, boolean localError) {
        return new TaskRelayException(message, code, statusCode, localError, null);
    }

    private String resolveOriginModelName(Task originTask) {
        Map<?, ?> data = originTask.getData() != null && !originTask.getData().isEmpty() ? Convert.toJSONObject(originTask.getData()) : Map.of();
        Object model = data.get("model");
        return model != null ? String.valueOf(model) : originTask.getPlatform();
    }

    private String resolvePlatform(HttpServletRequest request, RelayInfo info) {
        Object platform = request.getAttribute("platform");
        if (platform != null) return String.valueOf(platform);
        if (info.getChannelType() > 0) return String.valueOf(info.getChannelType());
        return info.getRequestURLPath() != null && info.getRequestURLPath().contains("suno") ? "suno" : "";
    }

    private String resolveModelName(String platform, String action) {
        if (TaskConstants.TASK_PLATFORM_SUNO.equals(platform)) {
            return TaskConstants.SUNO_ACTION_LYRICS.equals(action) ? "suno_lyrics" : "suno_music";
        }
        return platform;
    }

    private int resolveUserId(HttpServletRequest request, RelayInfo info) {
        Object id = request.getAttribute("id");
        if (id instanceof Number number) return number.intValue();
        if (id != null) return Integer.parseInt(String.valueOf(id));
        return info.getUserId();
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String stringAttr(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String defaultString(String value, String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return headers;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    private void addUsedChannel(HttpServletRequest request, Integer channelId) {
        if (channelId == null) return;
        Object current = request.getAttribute("use_channel");
        List<String> useChannel = current instanceof List<?> list
                ? new java.util.ArrayList<>(list.stream().map(String::valueOf).toList())
                : new java.util.ArrayList<>();
        useChannel.add(String.valueOf(channelId));
        request.setAttribute("use_channel", useChannel);
    }

    private String extractFirstKey(Channel channel) {
        if (channel.getKey() == null || channel.getKey().isEmpty()) return "";
        String key = channel.getKey().trim();
        int newlineIdx = key.indexOf('\n');
        return newlineIdx > 0 ? key.substring(0, newlineIdx).trim() : key;
    }

    /** 从路径中提取 task_id */
    private String extractTaskIdFromPath(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (("videos".equals(parts[i]) || "generations".equals(parts[i]) || "fetch".equals(parts[i])) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    @Data
    public static class TaskSubmitResult {
        private final String upstreamTaskID;
        private final byte[] taskData;
        private final String platform;
        private final int quota;
        private final String publicTaskID;
    }

    @Getter
    public static class TaskRelayException extends RuntimeException {
        private final String code;
        private final int statusCode;
        private final boolean localError;

        public TaskRelayException(String message, String code, int statusCode, boolean localError, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.statusCode = statusCode;
            this.localError = localError;
        }

        public Map<String, Object> toResponseBody() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", code);
            body.put("message", statusCode == 429 ? "当前分组上游负载已饱和，请稍后再试" : getMessage());
            body.put("status_code", statusCode);
            return body;
        }
    }
}
