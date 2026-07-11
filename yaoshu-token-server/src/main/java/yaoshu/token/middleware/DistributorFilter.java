package yaoshu.token.middleware;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.dto.ChannelInfoDTO;
import yaoshu.token.pojo.dto.MidjourneyDTO;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.service.ChannelAffinityService;
import yaoshu.token.service.ChannelSelectService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.service.GroupService;
import yaoshu.token.service.MidjourneyService;
import yaoshu.token.service.RetryParam;
import yaoshu.token.spi.ChannelSelector;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道分发中间件  * <p>
 * 核心流程：
 * <ol>
 * <li>从请求路径/Body 解析 model + shouldSelectChannel（getModelRequest）</li>
 * <li>Token 指定了特定渠道 ID 时直接使用该渠道</li>
 * <li>否则：Token 模型限制检查 → 渠道亲和性优先 → 随机选择渠道</li>
 * <li>设置渠道上下文属性（SetupContextForSelectedChannel）</li>
 * <li>请求成功后记录渠道亲和性</li>
 * </ol>
 */
@Slf4j
public class DistributorFilter implements Filter {    private final ChannelService channelService;
    private final ChannelSelector channelSelector;

    public DistributorFilter(ChannelService channelService, ChannelSelector channelSelector) {
        this.channelService = channelService;
        this.channelSelector = channelSelector;
    }

    // ======================== 模型请求内部类 ========================

    private static class ModelRequest {
        String model;
        String group;
    }

    // ======================== doFilter 主入口 ========================

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                         jakarta.servlet.ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();

        // 模型列表/详情查询不需要渠道分发
        if ("GET".equalsIgnoreCase(request.getMethod())
                && ("/v1/models".equals(path) || path.startsWith("/v1/models/"))) {
            chain.doFilter(request, response);
            return;
        }

        Channel channel = null;
        String selectGroup = null;

        // 1. 解析模型请求
        ModelRequest modelRequest;
        boolean shouldSelectChannel;
        try {
            Object[] parsed = getModelRequest(request);
            modelRequest = (ModelRequest) parsed[0];
            shouldSelectChannel = (boolean) parsed[1];
        } catch (Exception e) {
            abortWithOpenAiMessage(response, 400, "invalid request: " + e.getMessage());
            return;
        }

        // 2. 检查 Token 是否指定了特定渠道
        Object specificChannelId = request.getAttribute(ContextKeyConstants.TOKEN_SPECIFIC_CHANNEL_ID);
        if (specificChannelId != null) {
            int id = toInt(specificChannelId);
            channel = channelService.getById(id);
            if (channel == null || (channel.getStatus() != null && channel.getStatus() != 1)) {
                abortWithOpenAiMessage(response,
                        channel == null ? 400 : 403,
                        channel == null ? "invalid specific channel id" : "specified channel is disabled");
                return;
            }
        } else if (shouldSelectChannel) {
            // 3. Token 模型限制检查
            Boolean modelLimitEnabled = (Boolean) request.getAttribute(ContextKeyConstants.TOKEN_MODEL_LIMIT_ENABLED);
            if (Boolean.TRUE.equals(modelLimitEnabled)) {
                @SuppressWarnings("unchecked")
                Map<String, Boolean> tokenModelLimit = (Map<String, Boolean>) request.getAttribute(ContextKeyConstants.TOKEN_MODEL_LIMIT);
                if (tokenModelLimit == null || tokenModelLimit.isEmpty()) {
                    abortWithOpenAiMessage(response, 403, "this token has no access to any model");
                    return;
                }
                String modelName = modelRequest.model != null ? modelRequest.model : "";
                if (!tokenModelLimit.containsKey(modelName)) {
                    abortWithOpenAiMessage(response, 403,
                            "token has no access to model: " + modelName);
                    return;
                }
            }

            if (modelRequest.model == null || modelRequest.model.isEmpty()) {
                abortWithOpenAiMessage(response, 400, "model name is required");
                return;
            }

            String usingGroup = getAttrString(request, ContextKeyConstants.USING_GROUP);

            // 4. 渠道亲和性优先
            int[] affinityResult = ChannelAffinityService.getPreferredChannelByAffinity(
                    request, modelRequest.model, usingGroup);
            int preferredChannelId = affinityResult[0];
            boolean affinityFound = affinityResult[1] != 0;

            if (affinityFound && preferredChannelId > 0) {
                Channel preferred = channelService.getById(preferredChannelId);
                boolean affinityUsable = false;
                if (preferred != null && (preferred.getStatus() != null && preferred.getStatus() == 1)) {
                    if ("auto".equals(usingGroup)) {
                        // auto 分组：遍历用户的自动子分组，找到第一个启用该模型的子分组
                        String userGroup = getAttrString(request, ContextKeyConstants.USER_GROUP);
                        for (String g : GroupService.getUserAutoGroup(userGroup)) {
                            if (isModelNameEnabledForChannel(preferred, modelRequest.model, g)) {
                                selectGroup = g;
                                request.setAttribute(ContextKeyConstants.AUTO_GROUP, g);
                                channel = preferred;
                                affinityUsable = true;
                                ChannelAffinityService.markChannelAffinityUsed(request, g, preferred.getId());
                                break;
                            }
                        }
                    } else if (isModelNameEnabledForChannel(preferred, modelRequest.model, usingGroup)) {
                        channel = preferred;
                        selectGroup = usingGroup;
                        affinityUsable = true;
                        ChannelAffinityService.markChannelAffinityUsed(request, usingGroup, preferred.getId());
                    }
                }
                if (!affinityUsable && !ChannelAffinityService.shouldKeepAffinityOnChannelDisabled()) {
                    // 亲和性渠道不可用且配置不保留时，清除当前请求的亲和性缓存
                    ChannelAffinityService.clearCurrentAffinityCache(request);
                }
            }

            // 5. 随机选择渠道（亲和性未命中或不可用时）
            if (channel == null) {
                RetryParam retryParam = new RetryParam();
                retryParam.setRequest(request);
                retryParam.setModelName(modelRequest.model);
                retryParam.setTokenGroup(usingGroup);
                retryParam.setRetry(0);

                Object[] selectResult = ChannelSelectService.cacheGetRandomSatisfiedChannel(
                        retryParam, channelService, channelSelector);
                channel = (Channel) selectResult[0];
                selectGroup = (String) selectResult[1];

                if (channel == null) {
                    String showGroup = "auto".equals(usingGroup)
                            ? "auto(" + (selectGroup != null ? selectGroup : "") + ")"
                            : usingGroup;
                    abortWithOpenAiMessage(response, 503,
                            "no available channel in group " + showGroup + " for model " + modelRequest.model);
                    return;
                }
            }
        }

        // 6. 设置渠道上下文
        request.setAttribute(ContextKeyConstants.ORIGINAL_MODEL, modelRequest.model);
        setupContextForSelectedChannel(request, channel, modelRequest.model);

        // 7. 记录请求开始时间
        request.setAttribute("request_start_time", System.currentTimeMillis());

        // 8. 执行后续链
        chain.doFilter(request, response);

        // 9. 请求成功后记录渠道亲和性
        if (channel != null && response.getStatus() < 400) {
            ChannelAffinityService.recordChannelAffinity(request, channel.getId());
        }
    }

    // ======================== 模型请求解析 ========================

    /**
     * 从请求路径/Body 解析模型名和分组      *
     * @return [ModelRequest, shouldSelectChannel]
     */
    private Object[] getModelRequest(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        ModelRequest mr = new ModelRequest();
        boolean shouldSelectChannel = true;

        // MJ 路径
        if (path.contains("/mj/")) {
            int relayMode = RelayModeEnum.path2RelayModeMidjourney(path);
            // 查询/通知类（fetch/notify/image-seed）无需选渠道
            if (relayMode == RelayModeEnum.MIDJOURNEY_TASK_FETCH
                    || relayMode == RelayModeEnum.MIDJOURNEY_TASK_FETCH_BY_CONDITION
                    || relayMode == RelayModeEnum.MIDJOURNEY_NOTIFY
                    || relayMode == RelayModeEnum.MIDJOURNEY_TASK_IMAGE_SEED) {
                shouldSelectChannel = false;
            } else {
                MidjourneyDTO.MidjourneyRequest mjReq = parseMidjourneyRequest(request);
                MidjourneyService.MjRequestModel result =
                        MidjourneyService.getMjRequestModel(relayMode, mjReq);
                if (!result.success()) {
                    throw new IOException("invalid midjourney request: " + result.errorDesc());
                }
                if (result.modelName() == null || result.modelName().isEmpty()) {
                    // task fetch/notify 类——无需选渠道
                    shouldSelectChannel = false;
                } else {
                    mr.model = result.modelName();
                }
            }
            request.setAttribute("relay_mode", relayMode);
            return new Object[]{mr, shouldSelectChannel};
        }

        // Suno 路径
        if (path.contains("/suno/")) {
            int relayMode = RelayModeEnum.path2RelaySuno(method, path);
            // fetch / fetch_by_id 无需选渠道
            if (relayMode == RelayModeEnum.SUNO_FETCH || relayMode == RelayModeEnum.SUNO_FETCH_BY_ID) {
                shouldSelectChannel = false;
            } else {
                // submit：action → suno_<action> 模型名（action 取自路径末段）
                String action = extractLastPathSegment(path);
                mr.model = coverTaskActionToModelName(TaskConstants.TASK_PLATFORM_SUNO, action);
            }
            request.setAttribute("platform", TaskConstants.TASK_PLATFORM_SUNO);
            request.setAttribute("relay_mode", relayMode);
            return new Object[]{mr, shouldSelectChannel};
        }

        // 视频路径
        if (path.contains("/v1/videos/") && path.endsWith("/remix")) {
            shouldSelectChannel = false;
            return new Object[]{mr, shouldSelectChannel};
        }
        if (path.contains("/v1/videos")) {
            if ("POST".equalsIgnoreCase(method)) {
                mr = parseModelFromBody(request);
            } else {
                shouldSelectChannel = false;
            }
            return new Object[]{mr, shouldSelectChannel};
        }
        if (path.contains("/v1/video/generations")) {
            if ("POST".equalsIgnoreCase(method)) {
                mr = parseModelFromBody(request);
            } else {
                shouldSelectChannel = false;
            }
            return new Object[]{mr, shouldSelectChannel};
        }

        // Gemini API 路径
        if (path.startsWith("/v1beta/models/") || path.startsWith("/v1/models/")) {
            String modelName = extractModelNameFromGeminiPath(path);
            if (modelName != null) {
                mr.model = modelName;
            }
            request.setAttribute("relay_mode", "gemini");
            return new Object[]{mr, shouldSelectChannel};
        }

        // 音频路径
        if (path.startsWith("/v1/audio")) {
            if (path.startsWith("/v1/audio/speech")) {
                mr.model = coalesce(mr.model, "tts-1");
            } else if (path.startsWith("/v1/audio/translations")) {
                mr = parseModelFromBody(request);
                mr.model = coalesce(mr.model, "whisper-1");
            } else if (path.startsWith("/v1/audio/transcriptions")) {
                mr = parseModelFromBody(request);
                mr.model = coalesce(mr.model, "whisper-1");
            }
            return new Object[]{mr, shouldSelectChannel};
        }

        // 图像路径
        if (path.startsWith("/v1/images/generations")) {
            mr.model = coalesce(mr.model, "dall-e");
            return new Object[]{mr, shouldSelectChannel};
        }
        if (path.startsWith("/v1/images/edits")) {
            mr = parseModelFromBody(request);
            return new Object[]{mr, shouldSelectChannel};
        }

        // realtime (WebSocket)
        if (path.startsWith("/v1/realtime")) {
            mr.model = request.getParameter("model");
            return new Object[]{mr, shouldSelectChannel};
        }

        // moderations
        if (path.startsWith("/v1/moderations")) {
            mr.model = coalesce(mr.model, "text-moderation-stable");
            return new Object[]{mr, shouldSelectChannel};
        }

        // embeddings
        if (path.endsWith("embeddings")) {
            if (mr.model == null || mr.model.isEmpty()) {
                // 尝试从路径最后一段获取
                String[] segments = path.split("/");
                if (segments.length > 0) {
                    mr.model = segments[segments.length - 1];
                }
            }
        }

        // /pg/chat/completions — Playground 调试
        // Playground 走 Sa-Token 会话认证，group 由 AuthFilter 从 SaSession 注入（USING_GROUP），
        // 请求体不携带 group 字段（对前端透明，见设计_登录鉴权与Sa-Token重构.md §3.3）
        if (path.startsWith("/pg/chat/completions")) {
            mr = parseModelFromBody(request);
            return new Object[]{mr, shouldSelectChannel};
        }

        // 通用路径：从 JSON Body 提取 model/group
        if (!path.startsWith("/v1/audio/transcriptions")
                && !request.getContentType().contains("multipart/form-data")) {
            mr = parseModelFromBody(request);
        }

        // compact 路径追加后缀
        if (path.startsWith("/v1/responses/compact") && mr.model != null && !mr.model.isEmpty()) {
            mr.model = mr.model + "-compact";
        }

        return new Object[]{mr, shouldSelectChannel};
    }

    /**
     * 从 JSON 请求体解析 Midjourney 请求      */
    private MidjourneyDTO.MidjourneyRequest parseMidjourneyRequest(HttpServletRequest request) throws IOException {
        try {
            return Convert.toJavaBean(
                    new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                    MidjourneyDTO.MidjourneyRequest.class);
        } catch (Exception e) {
            log.debug("Failed to parse midjourney request body: {}", e.getMessage());
            return new MidjourneyDTO.MidjourneyRequest();
        }
    }

    /**
     * 取路径最后一段（用于 Suno action 提取），如 /suno/submit/music → music
     */
    private String extractLastPathSegment(String path) {
        if (path == null || path.isEmpty()) return "";
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = trimmed.lastIndexOf('/');
        return idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
    }

    /**
     * 任务平台 action → 模型名      */
    private String coverTaskActionToModelName(String platform, String action) {
        return platform.toLowerCase() + "_" + (action == null ? "" : action.toLowerCase());
    }

    /**
     * 从 JSON 请求体解析 model/group 字段，同时将解析结果缓存至 request attribute，
     * 供 RelayController 复用，避免二次消费 InputStream（规避 yue-library
     * RepeatedlyReadServletRequestFilter 可能的注册顺序问题）。
     */
    private ModelRequest parseModelFromBody(HttpServletRequest request) throws IOException {
        ModelRequest mr = new ModelRequest();
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            try {
                // 先读取字节再解析 JSON（readAllBytes() 可配合 RepeatedlyReadServletRequestWrapper 重复读取；
                // Convert.toJSONObject(InputStream) 可能将 ServletInputStream 当 JavaBean 序列化）
                @SuppressWarnings("unchecked")
                Map<String, Object> body = Convert.toJSONObject(
                        new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                // 缓存解析后的 body，供 RelayController 复用
                request.setAttribute(ContextKeyConstants.PARSED_REQUEST_BODY, body);
                Object model = body.get("model");
                if (model != null) mr.model = model.toString();
                Object group = body.get("group");
                if (group != null) mr.group = group.toString();
            } catch (Exception e) {
                log.debug("Failed to parse model from JSON body: {}", e.getMessage());
            }
        }
        return mr;
    }

    // ======================== 渠道上下文设置 ========================

    /**
     * 设置选中渠道的上下文属性      */
    private void setupContextForSelectedChannel(HttpServletRequest request, Channel channel, String modelName) {
        if (channel == null) return;

        setAttr(request, ContextKeyConstants.CHANNEL_ID, channel.getId());
        setAttr(request, ContextKeyConstants.CHANNEL_NAME, channel.getName());
        setAttr(request, ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        setAttr(request, ContextKeyConstants.CHANNEL_CREATE_TIME, channel.getCreatedTime());
        setAttr(request, ContextKeyConstants.CHANNEL_SETTING, channel.getSetting());
        setAttr(request, ContextKeyConstants.CHANNEL_OTHER_SETTING, channel.getOtherInfo());
        setAttr(request, ContextKeyConstants.CHANNEL_MODEL_MAPPING, channel.getModelMapping());
        setAttr(request, ContextKeyConstants.CHANNEL_STATUS_CODE_MAPPING, channel.getStatusCodeMapping());
        setAttr(request, ContextKeyConstants.CHANNEL_AUTO_BAN, channel.getAutoBan());
        // Header / Param Override
        setAttr(request, ContextKeyConstants.CHANNEL_HEADER_OVERRIDE, channel.getHeaderOverride());
        setAttr(request, ContextKeyConstants.CHANNEL_PARAM_OVERRIDE, channel.getParamOverride());

        // OpenAI Organization
        if (channel.getOpenaiOrganization() != null && !channel.getOpenaiOrganization().isEmpty()) {
            setAttr(request, ContextKeyConstants.CHANNEL_ORGANIZATION, channel.getOpenaiOrganization());
        }

        // Key 选择（单 key 或号池轮询）
        ChannelInfoDTO channelInfo = parseChannelInfo(channel);
        int[] keyIndex = new int[1];  // 输出参数：选中的 key index
        String key = selectKey(channel, channelInfo, keyIndex);
        setAttr(request, ContextKeyConstants.CHANNEL_KEY, key);
        setAttr(request, ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());
        setAttr(request, ContextKeyConstants.CHANNEL_IS_MULTI_KEY, channelInfo != null && channelInfo.isMultiKey());
        if (channelInfo != null && channelInfo.isMultiKey()) {
            request.setAttribute("channel_multi_key_index", keyIndex[0]);
        }

        // 按渠道类型设置特定参数
        if (channel.getType() != null) {
            switch (channel.getType()) {
                case ChannelConstants.CHANNEL_TYPE_AZURE:
                    request.setAttribute("api_version", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_VERTEX_AI:
                    request.setAttribute("region", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_XUNFEI:
                    request.setAttribute("api_version", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_GEMINI:
                    request.setAttribute("api_version", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_ALI:
                    request.setAttribute("plugin", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_MOKA_AI:
                    request.setAttribute("api_version", channel.getOther());
                    break;
                case ChannelConstants.CHANNEL_TYPE_COZE:
                    request.setAttribute("bot_id", channel.getOther());
                    break;
            }
        }

        log.debug("Distributor: selected channel [{}] {} for model {}", channel.getId(), channel.getName(), modelName);
    }

    // ======================== 辅助方法 ========================

    /**
     * 从渠道 key 中选择一个启用的 key（单 key 或号池轮询）
     *
     * @param channel      渠道实体
     * @param channelInfo  渠道信息 DTO（可为 null）
     * @param outKeyIndex  输出参数：选中的 key index（单 key 为 0）
     * @return 选中的 key 字符串
     */
    private String selectKey(Channel channel, ChannelInfoDTO channelInfo, int[] outKeyIndex) {
        outKeyIndex[0] = 0;
        if (channel.getKey() == null || channel.getKey().isEmpty()) {
            return "";
        }

        // 非 multiKey 模式：取第一个 key
        if (channelInfo == null || !channelInfo.isMultiKey()) {
            return extractFirstKey(channel);
        }

        // multiKey 模式：解析 key 列表，跳过已禁用的 index，按 pollingIndex 轮询
        List<String> keys = parseKeyList(channel.getKey());
        if (keys.isEmpty()) {
            return "";
        }

        int size = keys.size();
        int startIndex = channelInfo.getMultiKeyPollingIndex() % size;
        Map<Integer, Integer> statusList = channelInfo.getMultiKeyStatusList();

        for (int i = 0; i < size; i++) {
            int idx = (startIndex + i) % size;
            // 跳过已禁用的 key（statusList 中标记为 MANUALLY_DISABLED 或 AUTO_DISABLED）
            if (statusList != null) {
                Integer status = statusList.get(idx);
                if (status != null && status != CommonConstants.CHANNEL_STATUS_ENABLED) {
                    continue;
                }
            }
            outKeyIndex[0] = idx;
            return keys.get(idx);
        }

        // 所有 key 都被禁用，回退到第一个
        outKeyIndex[0] = 0;
        return keys.get(0);
    }

    /**
     * 解析渠道的 channel_info JSON 为 ChannelInfoDTO
     */
    private ChannelInfoDTO parseChannelInfo(Channel channel) {
        String json = channel.getChannelInfo();
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Convert.toJavaBean(json, ChannelInfoDTO.class);
        } catch (Exception e) {
            log.debug("Failed to parse channel_info for channel {}: {}", channel.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 从渠道 key 字段解析出 key 列表（支持 JSON 数组、换行分隔、单 key）
     */
    private List<String> parseKeyList(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return List.of();
        }
        String key = rawKey.trim();
        // JSON 数组格式
        if (key.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(key, String[].class);
                return Arrays.asList(arr);
            } catch (Exception e) {
                log.debug("multi-key JSON 解析失败: {}", e.getMessage());
            }
        }
        // 换行分隔
        if (key.contains("\n")) {
            return Arrays.stream(key.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        // 单 key
        return List.of(key);
    }

    /**
     * 从渠道 key 中提取第一个 key（单 key 或换行分隔的第一个）
     */
    private String extractFirstKey(Channel channel) {
        if (channel.getKey() == null || channel.getKey().isEmpty()) {
            return "";
        }
        String key = channel.getKey().trim();
        // JSON 数组格式
        if (key.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(key, String[].class);
                return arr.length > 0 ? arr[0] : "";
            } catch (Exception e) {
                log.debug("single-key JSON 解析失败: {}", e.getMessage());
            }
        }
        // 换行分隔取第一个
        int newlineIdx = key.indexOf('\n');
        return newlineIdx > 0 ? key.substring(0, newlineIdx).trim() : key;
    }

    /**
     * 从 Gemini API URL 路径提取模型名
     * 输入: /v1beta/models/gemini-2.0-flash:generateContent → gemini-2.0-flash
     */
    private String extractModelNameFromGeminiPath(String path) {
        int modelsIdx = path.indexOf("/models/");
        if (modelsIdx == -1) return null;
        String afterModels = path.substring(modelsIdx + 8);
        int colonIdx = afterModels.indexOf(':');
        if (colonIdx > 0) {
            return afterModels.substring(0, colonIdx);
        }
        return afterModels;
    }

    /**
     * 检查模型名是否在指定分组+渠道下启用。      * <p>
     * 联查 abilities 表确认 (group, model, channelId) 启用记录存在。
     */
    private boolean isModelNameEnabledForChannel(Channel channel, String modelName, String group) {
        if (channel == null || modelName == null || group == null || group.isEmpty()) return false;
        return channelService.isChannelEnabledForGroupModel(group, modelName, channel.getId());
    }

    /**
     * 返回非空值，若 s 为空则返回 defaultVal
     */
    private String coalesce(String s, String defaultVal) {
        return (s != null && !s.isEmpty()) ? s : defaultVal;
    }

    private void setAttr(HttpServletRequest request, String key, Object val) {
        if (val != null) {
            request.setAttribute(key, val);
        }
    }

    private String getAttrString(HttpServletRequest request, String key) {
        Object val = request.getAttribute(key);
        return val != null ? val.toString() : "";
    }

    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 返回 OpenAI 兼容格式的错误响应
     */
    private void abortWithOpenAiMessage(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> errorBody = Map.of(
                "error", Map.of("message", message, "type", "distributor_error", "code", String.valueOf(status))
        );
        response.getWriter().write(Convert.toJSONString(errorBody));
    }
}
