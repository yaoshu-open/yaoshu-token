package yaoshu.token.controller;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.constant.StatusCodeRetryConfig;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.vo.PricingVO;
import yaoshu.token.pojo.vo.PricingVendorVO;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.handler.*;
import yaoshu.token.relay.helper.PriceHelper;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.ChannelManagementService;
import yaoshu.token.service.TokenCounterService;
import yaoshu.token.service.ChannelSelectService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.PerfMetricsService;
import yaoshu.token.service.PricingService;
import yaoshu.token.service.RetryParam;
import yaoshu.token.spi.ChannelHealthHandler;
import yaoshu.token.spi.ChannelSelector;
import yaoshu.token.spi.RelayRequestInterceptor;
import yaoshu.token.spi.RelayRetryStrategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI API 中转核心控制器  * <p>
 * 覆盖路由：/v1/*、/v1beta/*（30+ 端点）
 * 认证：TokenAuth（大部分）、CORS、Stats、TokenAuth + Distribute
 * <p>
 * 分发策略：relayFormat → relayMode → handler dispatch
 */
@Slf4j
@RestController
public class RelayController {

    private final CompatibleHandler compatibleHandler;
    private final ClaudeHandler claudeHandler;
    private final GeminiHandler geminiHandler;
    private final EmbeddingHandler embeddingHandler;
    private final RerankHandler rerankHandler;
    private final AudioHandler audioHandler;
    private final ImageHandler imageHandler;
    private final ResponsesHandler responsesHandler;
    private final MidjourneyHandler midjourneyHandler;
    private final PricingService pricingService;
    private final ChannelService channelService;
    private final ChannelManagementService channelManagementService;
    private final LogMapper logMapper;
    private final PerfMetricsService perfMetricsService;
    private final RelayRetryStrategy relayRetryStrategy;
    private final ChannelHealthHandler channelHealthHandler;
    private final RelayRequestInterceptor relayRequestInterceptor;
    private final ModelService modelService;
    private final BillingService billingService;
    private final TokenCounterService tokenCounterService;
    @Autowired(required = false)
    private ChannelSelector channelSelector;

    public RelayController(CompatibleHandler compatibleHandler,
                           ClaudeHandler claudeHandler,
                           GeminiHandler geminiHandler,
                           EmbeddingHandler embeddingHandler,
                           RerankHandler rerankHandler,
                           AudioHandler audioHandler,
                           ImageHandler imageHandler,
                           ResponsesHandler responsesHandler,
                           MidjourneyHandler midjourneyHandler,
                           PricingService pricingService,
                           ChannelService channelService,
                           ChannelManagementService channelManagementService,
                           LogMapper logMapper,
                           PerfMetricsService perfMetricsService,
                           RelayRetryStrategy relayRetryStrategy,
                           ChannelHealthHandler channelHealthHandler,
                           RelayRequestInterceptor relayRequestInterceptor,
                           ModelService modelService,
                           BillingService billingService,
                           TokenCounterService tokenCounterService,
                           @Autowired(required = false) ChannelSelector channelSelector) {
        this.compatibleHandler = compatibleHandler;
        this.claudeHandler = claudeHandler;
        this.geminiHandler = geminiHandler;
        this.embeddingHandler = embeddingHandler;
        this.rerankHandler = rerankHandler;
        this.audioHandler = audioHandler;
        this.imageHandler = imageHandler;
        this.responsesHandler = responsesHandler;
        this.midjourneyHandler = midjourneyHandler;
        this.pricingService = pricingService;
        this.channelService = channelService;
        this.channelManagementService = channelManagementService;
        this.logMapper = logMapper;
        this.perfMetricsService = perfMetricsService;
        this.relayRetryStrategy = relayRetryStrategy;
        this.channelHealthHandler = channelHealthHandler;
        this.relayRequestInterceptor = relayRequestInterceptor;
        this.modelService = modelService;
        this.billingService = billingService;
        this.tokenCounterService = tokenCounterService;
        this.channelSelector = channelSelector;
    }

    // ======================== 模型列表 ========================

    /**
     * 获取模型列表（OpenAI 格式）      * <p>
     * 返回当前定价中的模型名称列表，包含 owned_by 供应商信息。
     * 委托给 ModelService.listAllOpenAIModels()，末尾会经过 ModelListFilter 过滤。
     */
    @GetMapping("/v1/models")
    public Map<String, Object> listModels() {
        List<Map<String, Object>> models = modelService.listAllOpenAIModels();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", "list");
        result.put("data", models);
        return result;
    }

    /**
     * 获取单个模型信息      */
    @GetMapping("/v1/models/{model}")
    public Map<String, Object> retrieveModel(@PathVariable String model) {
        List<PricingVO> pricing = pricingService.getPricing();
        PricingVO found = pricing.stream()
                .filter(p -> p.getModelName().equals(model))
                .findFirst().orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", model);
        result.put("object", "model");
        result.put("created", 1626777600);
        result.put("owned_by", found != null && found.getVendorId() != null
                ? pricingService.getVendors().stream()
                    .filter(v -> v.getId().equals(found.getVendorId()))
                    .findFirst().map(PricingVendorVO::getName).orElse("custom")
                : "custom");
        result.put("supported_endpoint_types",
                found != null ? found.getSupportedEndpointTypes() : List.of());
        return result;
    }

    /**
     * Gemini 模型列表（Gemini 格式）      */
    @GetMapping("/v1beta/models")
    public Map<String, Object> listGeminiModels() {
        List<PricingVO> pricing = pricingService.getPricing();
        List<Map<String, Object>> geminiModels = new ArrayList<>();
        for (PricingVO p : pricing) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("name", p.getModelName());
            gm.put("displayName", p.getModelName());
            geminiModels.add(gm);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", geminiModels);
        result.put("nextPageToken", null);
        return result;
    }

    /**
     * Gemini 兼容 OpenAI 模型列表
     */
    @GetMapping("/v1beta/openai/models")
    public Map<String, Object> listGeminiCompatibleModels() {
        return listModels();
    }

    // ======================== AI API 中转（核心 dispatch） ========================

    @PostMapping("/v1/chat/completions")
    public void chatCompletions(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.CHAT_COMPLETIONS);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/completions")
    public void completions(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.COMPLETIONS);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/embeddings")
    public void embeddings(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.EMBEDDINGS);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/images/generations")
    public void imagesGenerations(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.IMAGES_GENERATIONS);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/audio/transcriptions")
    public void audioTranscriptions(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.AUDIO_TRANSCRIPTION);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/audio/translations")
    public void audioTranslations(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.AUDIO_TRANSLATION);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/audio/speech")
    public void audioSpeech(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.AUDIO_SPEECH);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/rerank")
    public void rerank(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.RERANK);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/messages")
    public void claudeMessages(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        dispatchRelay(req, resp, info, "claude");
    }

    @PostMapping("/v1/responses")
    public void responses(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.RESPONSES);
        dispatchRelay(req, resp, info, "openai");
    }

    @PostMapping("/v1/responses/compact")
    public void responsesCompact(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        info.setRelayMode(RelayModeEnum.RESPONSES_COMPACT);
        dispatchRelay(req, resp, info, "openai");
    }

    @RequestMapping("/v1beta/models/**")
    public void geminiV1Beta(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        dispatchRelay(req, resp, info, "gemini");
    }

    @RequestMapping("/v1/models/**")
    public void geminiModels(HttpServletRequest req, HttpServletResponse resp) {
        RelayInfo info = buildRelayInfo(req);
        dispatchRelay(req, resp, info, "gemini");
    }

    // ======================== 辅助 ========================

    /**
     * 从 DistributorFilter 设置的 request attributes 构建 RelayInfo，
     */
    @SuppressWarnings("unchecked")
    private RelayInfo buildRelayInfo(HttpServletRequest req) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());

        // requestId（订阅预扣费幂等键）
        String requestId = (String) req.getAttribute("request_id");
        if (requestId == null || requestId.isEmpty()) {
            requestId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        info.setRequestId(requestId);

        // 渠道上下文（DistributorFilter 设置）
        Integer channelId = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_ID);
        Integer channelType = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_TYPE);
        if (channelId != null) info.setChannelId(channelId);
        if (channelType != null) info.setChannelType(channelType);

        info.setChannelBaseUrl((String) req.getAttribute(ContextKeyConstants.CHANNEL_BASE_URL));
        info.setApiKey((String) req.getAttribute(ContextKeyConstants.CHANNEL_KEY));
        info.setOrganization((String) req.getAttribute(ContextKeyConstants.CHANNEL_ORGANIZATION));
        info.setApiType(channelType != null ? channelType : 0);

        // 渠道设置
        Object settingObj = req.getAttribute(ContextKeyConstants.CHANNEL_SETTING);
        if (settingObj instanceof ChannelSettingsDTO cs) info.setChannelSetting(cs);

        Object otherSettingObj = req.getAttribute(ContextKeyConstants.CHANNEL_OTHER_SETTING);
        if (otherSettingObj instanceof ChannelOtherSettingsDTO cos) info.setChannelOtherSettings(cos);

        // Header / Param Override（DistributorFilter 设置的渠道级覆写配置）
        String headerOverrideJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_HEADER_OVERRIDE);
        if (headerOverrideJson != null && !headerOverrideJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> headerOverride = Convert.toJSONObject(headerOverrideJson);
                info.setHeadersOverride(headerOverride);
            } catch (Exception e) {
                log.debug("headerOverride JSON 解析失败: {}", e.getMessage());
            }
        }
        String paramOverrideJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_PARAM_OVERRIDE);
        if (paramOverrideJson != null && !paramOverrideJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramOverride = Convert.toJSONObject(paramOverrideJson);
                info.setParamOverride(paramOverride);
            } catch (Exception e2) {
                log.debug("paramOverride JSON 解析失败: {}", e2.getMessage());
            }
        }

        // 模型名映射
        info.setModelMapped(parseModelMapping(req));

        // Token / 用户上下文
        info.setTokenId(toInt(req.getAttribute("token_id")));
        info.setUserId(toInt(req.getAttribute("id")));
        info.setTokenGroup((String) req.getAttribute("token_group"));
        // 分组倍率依赖 usingGroup/userGroup（PriceHelper.handleGroupRatio），
        info.setUsingGroup((String) req.getAttribute(ContextKeyConstants.USING_GROUP));
        info.setUserGroup((String) req.getAttribute(ContextKeyConstants.USER_GROUP));
        // auto_group 覆盖：DistributorFilter 在 auto 分组场景选到实际子分组后设置 AUTO_GROUP，
        String autoGroup = (String) req.getAttribute(ContextKeyConstants.AUTO_GROUP);
        if (autoGroup != null && !autoGroup.isEmpty()) {
            info.setUsingGroup(autoGroup);
        }
        info.setTokenKey((String) req.getAttribute("token_key"));
        info.setTokenUnlimited(Boolean.TRUE.equals(req.getAttribute("token_unlimited")));
        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("username", req.getAttribute("username"));
        extraData.put("channelName", req.getAttribute(ContextKeyConstants.CHANNEL_NAME));
        extraData.put("tokenName", req.getAttribute("token_name"));
        extraData.put("upstreamRequestId", req.getAttribute("upstream_request_id"));
        info.setExtraData(extraData);

        // 原始模型名（DistributorFilter 解析的 model 参数）
        String modelName = (String) req.getAttribute("original_model");
        if (modelName != null && !modelName.isEmpty()) {
            info.setOriginModelName(modelName);
        }

        // 请求体解析
        parseRequestBody(req, info);

        info.setRequestURLPath(req.getRequestURI());

        return info;
    }

    /**
     * 模型名映射解析
     */
    private boolean parseModelMapping(HttpServletRequest req) {
        String mappingJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_MODEL_MAPPING);
        if (mappingJson != null && !mappingJson.isEmpty() && !"{}".equals(mappingJson)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapping = Convert.toJSONObject(mappingJson);
                String originModel = (String) req.getAttribute("original_model");
                if (originModel != null && mapping.containsKey(originModel)) {
                    String upstreamModel = mapping.get(originModel).toString();
                    if (upstreamModel != null && !upstreamModel.isEmpty()) {
                        req.setAttribute("upstream_model_name", upstreamModel);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("model_mapping 解析失败: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * 尝试解析请求体为对应 DTO 类型并注入 RelayInfo
     * <p>
     * 优先从 DistributorFilter 缓存的 request attribute 读取 body，
     * 避免二次消费 InputStream。
     */
    @SuppressWarnings("unchecked")
    private void parseRequestBody(HttpServletRequest req, RelayInfo info) {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.contains("application/json")) return;

        try {
            // 优先复用 DistributorFilter 缓存的 parsed body
            Map<String, Object> body = (Map<String, Object>) req.getAttribute(ContextKeyConstants.PARSED_REQUEST_BODY);
            if (body == null) {
                // 兜底：自行读取（readAllBytes() 配合 RepeatedlyReadServletRequestWrapper 可重复读取）
                body = Convert.toJSONObject(
                        new String(req.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            info.setRequest(body);  // 暂存原始 Map，由 Handler 按需转换为目标 DTO
            String model = (String) body.get("model");

            if (model == null) {
                // 尝试从路径提取 model（如 /v1beta/models/gemini-2.0-flash:generateContent）
                String path = req.getRequestURI();
                if (path.contains("/models/")) {
                    String[] parts = path.split("/models/");
                    if (parts.length > 1) {
                        model = parts[1].replaceAll(":.*", "");
                    }
                }
            }

            if (info.getOriginModelName() == null || info.getOriginModelName().isEmpty()) {
                info.setOriginModelName(model);
            }

            info.setUpstreamModelName(
                    (String) req.getAttribute("upstream_model_name"));
            if (info.getUpstreamModelName() == null || info.getUpstreamModelName().isEmpty()) {
                info.setUpstreamModelName(model);
            }

        } catch (Exception e) {
            log.debug("Failed to parse request body for model extraction: {}", e.getMessage());
        }
    }

    /**
     * 主中继分发入口，含渠道重试循环      * <p>
     * 重试策略：for retry <= RetryTimes → 每次重试重新选渠道 → addUsedChannel → 执行转发 →
     * 成功返回 / 失败 → processChannelError → shouldRetry → 继续/跳出。
     *
     * @param relayFormat "openai" / "claude" / "gemini"
     */
    void dispatchRelay(HttpServletRequest req, HttpServletResponse resp,
                                RelayInfo info, String relayFormat) {
        info.setRelayFormat(relayFormat);  // 设置中转格式，供流式响应 format 分派使用
        RetryParam retryParam = new RetryParam(req, info.getTokenGroup(), info.getOriginModelName());
        info.setRetryIndex(0);
        info.setLastError(null);

        RelayException lastError = null;

        // ====== 计费编排 ======
        // 1. 快速构建计费用 token 元数据（CountToken 关闭时仅提取 maxTokens，不解析消息文本）
        TokenCountMeta pricingMeta = tokenCounterService.fastTokenCountMetaForPricing(info.getRequest());
        // 2. 构建 PriceData（modelRatio/groupRatio 等比率，modelPriceHelper 内部 setPriceData 到 info）
        PriceHelper.modelPriceHelper(info, 0, pricingMeta.getMaxTokens());
        // 3. 预扣费（免费模型跳过）
        if (!info.getPriceData().isFreeModel()) {
            String preConsumeErr = billingService.preConsumeBilling(info, info.getPriceData().getQuotaToPreConsume());
            if (preConsumeErr != null) {
                // 预扣费失败为终端条件（额度不足不可重试），直接写入 OpenAI 兼容错误响应并返回。
                // 不能 throw——此处位于 try 块之外，throw 会导致异常逃逸为 500 空 body。
                RelayException ex = new RelayException(preConsumeErr, ErrorCode.INSUFFICIENT_USER_QUOTA);
                ex.setStatusCode(429);
                writeApiError(resp, ex, relayFormat);
                return;
            }
        }

        try {
        for (; retryParam.getRetry() <= CommonConstants.retryTimes; retryParam.increaseRetry()) {
            info.setRetryIndex(retryParam.getRetry());

            // 重试时需重新选渠道 + 重新读请求体
            if (retryParam.getRetry() > 0) {
                // 号池重试判断：401/429 且当前渠道是多 key 且还有未试过的 key → 换 key 不换渠道
                if (lastError != null && isKeyLevelError(lastError) && tryNextKeyOnSameChannel(req, info)) {
                    // 换 key 成功，不重新选渠道，直接继续执行
                    log.debug("号池切换：channel={} 换 key 重试，keyIndex={}",
                            info.getChannelId(), info.getChannelMultiKeyIndex());
                } else {
                    Channel channel = selectChannelForRetry(req, info, retryParam);
                    if (channel == null) {
                        lastError = new RelayException(
                                "no available channel for retry (group=" + info.getTokenGroup()
                                        + ", model=" + info.getOriginModelName() + ")",
                                yaoshu.token.pojo.dto.ErrorCode.GET_CHANNEL_FAILED);
                        lastError.setStatusCode(503);
                        break;
                    }
                    // 仅限非 autoBan 的 retry 设置；首次 retry 已在 DistributorFilter 中设置
                    setupChannelContextOnRetry(req, channel, info);
                    addUsedChannel(req, channel.getId());
                }
                // 重新解析请求体（重试时上游可能不同）
                parseRequestBody(req, info);
            } else {
                // 首次请求：记录 DistributorFilter 已选渠道
                Integer channelId = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_ID);
                if (channelId != null) {
                    addUsedChannel(req, channelId);
                }
            }

            try {
                // SPI：请求发往上游前调用
                if (relayRequestInterceptor != null) {
                    relayRequestInterceptor.preRequest(info);
                }

                switch (relayFormat) {
                    case "claude":
                        claudeHandler.claudeHelper(req, resp, info);
                        break;
                    case "gemini":
                        geminiHandler.geminiHelper(req, resp, info);
                        break;
                    default:
                        dispatchByMode(req, resp, info);
                }
                // 成功
                info.setLastError(null);
                if (relayRequestInterceptor != null) {
                    relayRequestInterceptor.postResponse(info, null);
                }
                // 成功路径性能采样
                recordPerfSample(info, true, info.getPerfOutputTokens());
                return;
            } catch (RelayException e) {
                lastError = e;
                info.setLastError(e);
                processChannelError(req, info, e);
                if (relayRequestInterceptor != null) {
                    relayRequestInterceptor.postResponse(info, e);
                }
                if (!shouldRetry(req, info, e, CommonConstants.retryTimes - retryParam.getRetry())) {
                    break;
                }
            }
        }

        // 所有重试失败
        logRetrySummary(req);

        // 记录失败性能采样
        if (lastError != null && info != null) {
            recordPerfSample(info, false, 0);
        }

        writeApiError(resp, lastError, relayFormat);
        } finally {
            // 失败时返还预扣费（成功结算后 BillingSession.settled=true，refund 无副作用）
            if (info.getBilling() != null) {
                info.getBilling().refund();
            }
        }
    }

    /**
     * 重试时从 ChannelSelectService 重新选择渠道      */
    private Channel selectChannelForRetry(HttpServletRequest req, RelayInfo info, RetryParam retryParam) {
        Object[] result = ChannelSelectService.cacheGetRandomSatisfiedChannel(retryParam, channelService);
        Channel channel = (Channel) result[0];
        String selectGroup = (String) result[1];

        if (channel == null) {
            log.warn("重试选渠道失败：group={}, model={}, retry={}",
                    retryParam.getTokenGroup(), retryParam.getModelName(), retryParam.getRetry());
        } else {
            log.debug("重试选中渠道：channel={}(#{}) group={}", channel.getName(), channel.getId(), selectGroup);
        }
        return channel;
    }

    /**
     * 重试时重新设置渠道上下文到 request attributes，
     */
    private void setupChannelContextOnRetry(HttpServletRequest req, Channel channel, RelayInfo info) {
        setAttr(req, ContextKeyConstants.CHANNEL_ID, channel.getId());
        setAttr(req, ContextKeyConstants.CHANNEL_NAME, channel.getName());
        setAttr(req, ContextKeyConstants.CHANNEL_TYPE, channel.getType());
        setAttr(req, ContextKeyConstants.CHANNEL_BASE_URL, channel.getBaseUrl());

        // Key 选择（单 key 或号池轮询）
        ChannelInfoDTO channelInfo = parseChannelInfo(channel);
        int[] keyIndex = new int[1];
        String key = selectKey(channel, channelInfo, keyIndex);
        setAttr(req, ContextKeyConstants.CHANNEL_KEY, key);
        setAttr(req, ContextKeyConstants.CHANNEL_IS_MULTI_KEY, channelInfo != null && channelInfo.isMultiKey());
        if (channelInfo != null && channelInfo.isMultiKey()) {
            req.setAttribute("channel_multi_key_index", keyIndex[0]);
            info.setChannelIsMultiKey(true);
            info.setChannelMultiKeyIndex(keyIndex[0]);
        } else {
            info.setChannelIsMultiKey(false);
            info.setChannelMultiKeyIndex(0);
        }

        setAttr(req, ContextKeyConstants.CHANNEL_ORGANIZATION, channel.getOpenaiOrganization());
        setAttr(req, ContextKeyConstants.CHANNEL_SETTING, channel.getSetting());
        setAttr(req, ContextKeyConstants.CHANNEL_OTHER_SETTING, channel.getOtherInfo());
        setAttr(req, ContextKeyConstants.CHANNEL_MODEL_MAPPING, channel.getModelMapping());
        setAttr(req, ContextKeyConstants.CHANNEL_STATUS_CODE_MAPPING, channel.getStatusCodeMapping());
        setAttr(req, ContextKeyConstants.CHANNEL_AUTO_BAN, channel.getAutoBan());

        // 同步 RelayInfo 中的渠道字段
        info.setChannelId(channel.getId());
        info.setChannelType(channel.getType());
        info.setChannelBaseUrl(channel.getBaseUrl());
        info.setApiKey(key);
    }

    /**
     * 判断错误是否为 key 级错误（换 key 可能有效）
     */
    private boolean isKeyLevelError(RelayException error) {
        if (error == null) return false;
        int code = error.getStatusCode();
        return code == 401 || code == 429;
    }

    /**
     * 尝试在当前渠道上换到下一个未试过的 key（号池切换）
     *
     * @return true 表示成功换到新 key；false 表示当前渠道不是多 key 或所有 key 都已试过
     */
    @SuppressWarnings("unchecked")
    private boolean tryNextKeyOnSameChannel(HttpServletRequest req, RelayInfo info) {
        if (!info.isChannelIsMultiKey()) {
            return false;
        }

        // 获取当前渠道实体（从上下文或数据库）
        Integer channelId = info.getChannelId();
        if (channelId == null) return false;
        Channel channel = channelService.getById(channelId);
        if (channel == null) return false;

        // 解析 key 列表
        List<String> keys = parseKeyList(channel.getKey());
        if (keys.size() <= 1) return false;

        // 记录已试过的 key index
        List<Integer> triedKeyIndices = (List<Integer>) req.getAttribute("tried_key_indices");
        if (triedKeyIndices == null) {
            triedKeyIndices = new ArrayList<>();
            // 当前 key index 也加入已试列表
            if (info.getChannelMultiKeyIndex() >= 0) {
                triedKeyIndices.add(info.getChannelMultiKeyIndex());
            }
        }

        // 解析 channelInfo 获取禁用状态
        ChannelInfoDTO channelInfo = parseChannelInfo(channel);
        Map<Integer, Integer> statusList = channelInfo != null ? channelInfo.getMultiKeyStatusList() : null;

        // 找下一个未试过且未禁用的 key
        int currentIdx = info.getChannelMultiKeyIndex();
        for (int i = 1; i <= keys.size(); i++) {
            int idx = (currentIdx + i) % keys.size();
            if (triedKeyIndices.contains(idx)) continue;
            // 跳过已禁用的 key
            if (statusList != null) {
                Integer status = statusList.get(idx);
                if (status != null && status != CommonConstants.CHANNEL_STATUS_ENABLED) continue;
            }
            // 找到可用的 key
            triedKeyIndices.add(idx);
            req.setAttribute("tried_key_indices", triedKeyIndices);
            info.setApiKey(keys.get(idx));
            info.setChannelMultiKeyIndex(idx);
            setAttr(req, ContextKeyConstants.CHANNEL_KEY, keys.get(idx));
            req.setAttribute("channel_multi_key_index", idx);
            return true;
        }

        return false;
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
        if (key.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(key, String[].class);
                return Arrays.asList(arr);
            } catch (Exception e) {
                log.debug("multi-key JSON 解析失败: {}", e.getMessage());
            }
        }
        if (key.contains("\n")) {
            return Arrays.stream(key.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(key);
    }

    /**
     * 从渠道 key 中选择一个启用的 key（单 key 或号池轮询）
     */
    private String selectKey(Channel channel, ChannelInfoDTO channelInfo, int[] outKeyIndex) {
        outKeyIndex[0] = 0;
        if (channel.getKey() == null || channel.getKey().isEmpty()) {
            return "";
        }
        if (channelInfo == null || !channelInfo.isMultiKey()) {
            String key = channel.getKey().trim();
            if (key.startsWith("[")) {
                try {
                    String[] arr = Convert.toJavaBean(key, String[].class);
                    return arr.length > 0 ? arr[0] : "";
                } catch (Exception e) {
                    log.debug("single-key JSON 解析失败: {}", e.getMessage());
                }
            }
            int newlineIdx = key.indexOf('\n');
            return newlineIdx > 0 ? key.substring(0, newlineIdx).trim() : key;
        }

        List<String> keys = parseKeyList(channel.getKey());
        if (keys.isEmpty()) return "";

        int size = keys.size();
        int startIndex = channelInfo.getMultiKeyPollingIndex() % size;
        Map<Integer, Integer> statusList = channelInfo.getMultiKeyStatusList();

        for (int i = 0; i < size; i++) {
            int idx = (startIndex + i) % size;
            if (statusList != null) {
                Integer status = statusList.get(idx);
                if (status != null && status != CommonConstants.CHANNEL_STATUS_ENABLED) continue;
            }
            outKeyIndex[0] = idx;
            return keys.get(idx);
        }
        outKeyIndex[0] = 0;
        return keys.get(0);
    }

    /**
     * 记录已尝试渠道 ID 到 request attribute      */
    @SuppressWarnings("unchecked")
    private void addUsedChannel(HttpServletRequest req, int channelId) {
        List<Integer> usedChannels = (List<Integer>) req.getAttribute("use_channel");
        if (usedChannels == null) {
            usedChannels = new ArrayList<>();
        }
        if (!usedChannels.contains(channelId)) {
            usedChannels.add(channelId);
        }
        req.setAttribute("use_channel", usedChannels);
    }

    /**
     * 错误分类决策——判断是否应继续重试      * <p>
     * 决策链（与 Go 完全一致）：
     * 1. nil → false
     * 2. 亲和性失败跳过重试 → false
     * 3. ChannelError（渠道级错误）→ true
     * 4. SkipRetry 标记 → false
     * 5. 剩余重试次数 ≤ 0 → false
     * 6. 指定了特定渠道 → false
     * 7. 2xx → false
     * 8. <100 或 >599 → true（异常状态码）
     * 9. AlwaysSkipRetryCode → false
     * 10. ShouldRetryByStatusCode → true/false
     */
    private boolean shouldRetry(HttpServletRequest req, RelayInfo info, RelayException error, int remainingRetries) {
        if (error == null) return false;

        // 亲和性失败跳过重试
        if (yaoshu.token.service.ChannelAffinityService.shouldSkipRetryAfterAffinityFailure(req)) {
            return false;
        }

        // 剩余重试次数
        if (remainingRetries <= 0) return false;

        // 指定了特定渠道（specific_channel_id）
        if (req.getAttribute("specific_channel_id") != null) return false;

        // 委托 SPI 进行状态码级重试决策（ChannelError/SkipRetry/状态码范围）
        if (relayRetryStrategy != null) {
            return relayRetryStrategy.shouldRetry(info, error, remainingRetries);
        }
        return false;
    }

    /**
     * 处理渠道级错误：自动禁渠道 + 错误日志落库      */
    private void processChannelError(HttpServletRequest req, RelayInfo info, RelayException error) {
        log.error("中继路径渠道错误：statusCode={}, errorCode={}, message={}",
                error.getStatusCode(), error.getErrorCode(), truncateMsg(error.getMessage()));

        // 委托 SPI 处理渠道级错误（自动禁用渠道）
        if (channelHealthHandler != null) {
            channelHealthHandler.onChannelError(info, error);
        }

        // 错误日志落库
        if (CommonConstants.isMasterNode && isRecordErrorLog(error)) {
            recordErrorLog(req, error);
        }
    }

    /**
     * 判断是否应记录错误日志      */
    private boolean isRecordErrorLog(RelayException error) {
        if (error == null) return false;
        return error.isRecordErrorLog();  // RelayException 默认 recordErrorLog=true
    }

    /**
     * 错误日志落库      */
    private void recordErrorLog(HttpServletRequest req, RelayException error) {
        try {
            Log log = new Log();
            log.setUserId(toInt(req.getAttribute("id")));
            log.setChannelId(toInt(req.getAttribute(ContextKeyConstants.CHANNEL_ID)));
            log.setModelName((String) req.getAttribute("original_model"));
            log.setTokenName((String) req.getAttribute("token_name"));
            log.setContent(error.maskSensitiveErrorWithStatusCode());
            log.setTokenId(toInt(req.getAttribute("token_id")));
            log.setUsername((String) req.getAttribute("username"));
            log.setGroup((String) req.getAttribute("group"));

            // 使用时长
            Long startTime = (Long) req.getAttribute("request_start_time");
            if (startTime != null) {
                log.setUseTime((int) ((System.currentTimeMillis() - startTime) / 1000));
            } else {
                log.setUseTime(0);
            }

            log.setIsStream(Boolean.TRUE.equals(req.getAttribute("is_stream")));

            // request_id
            Object requestId = req.getAttribute("request_id");
            if (requestId instanceof String sid) {
                log.setRequestId(sid);
            }

            log.setCreatedAt(Instant.now().getEpochSecond());
            log.setType(5);  // LogTypeError 
            // other 字段：记录错误上下文
            Map<String, Object> other = new LinkedHashMap<>();
            other.put("error_type", error.getErrorType());
            other.put("error_code", error.getErrorCode());
            other.put("status_code", error.getStatusCode());
            other.put("channel_id", req.getAttribute(ContextKeyConstants.CHANNEL_ID));
            other.put("channel_type", req.getAttribute(ContextKeyConstants.CHANNEL_TYPE));
            other.put("request_path", req.getRequestURI());

            @SuppressWarnings("unchecked")
            List<Integer> useChannel = (List<Integer>) req.getAttribute("use_channel");
            Map<String, Object> adminInfo = new LinkedHashMap<>();
            adminInfo.put("use_channel", useChannel);
            other.put("admin_info", adminInfo);

            log.setOther(Convert.toJSONString(other));

            logMapper.insert(log);
        } catch (Exception e) {
            log.error("记录错误日志失败", e);
        }
    }

    /**
     * 输出重试总结日志      */
    @SuppressWarnings("unchecked")
    private void logRetrySummary(HttpServletRequest req) {
        List<Integer> useChannel = (List<Integer>) req.getAttribute("use_channel");
        if (useChannel != null && useChannel.size() > 1) {
            StringBuilder sb = new StringBuilder("重试：");
            for (int i = 0; i < useChannel.size(); i++) {
                if (i > 0) sb.append("->");
                sb.append(useChannel.get(i));
            }
            log.info(sb.toString());
        }
    }

    /** 安全设置 request attribute */
    private void setAttr(HttpServletRequest req, String key, Object value) {
        if (value != null) req.setAttribute(key, value);
    }

    /** 截断过长错误信息用于日志 */
    private static String truncateMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    /**
     * 根据 relayMode 分发 openai 格式请求
     */
    private void dispatchByMode(HttpServletRequest req, HttpServletResponse resp, RelayInfo info) {
        int mode = info.getRelayMode();
        if (mode == RelayModeEnum.IMAGES_GENERATIONS || mode == RelayModeEnum.IMAGES_EDITS) {
            imageHandler.imageHelper(req, resp, info);
        } else if (mode == RelayModeEnum.AUDIO_SPEECH
                || mode == RelayModeEnum.AUDIO_TRANSCRIPTION
                || mode == RelayModeEnum.AUDIO_TRANSLATION) {
            audioHandler.audioHelper(req, resp, info);
        } else if (mode == RelayModeEnum.RERANK) {
            rerankHandler.rerankHelper(req, resp, info);
        } else if (mode == RelayModeEnum.EMBEDDINGS) {
            embeddingHandler.embeddingHelper(req, resp, info);
        } else if (mode == RelayModeEnum.RESPONSES || mode == RelayModeEnum.RESPONSES_COMPACT) {
            responsesHandler.responsesHelper(req, resp, info);
        } else {
            // 默认：Chat Completions
            compatibleHandler.textHelper(req, resp, info);
        }
    }

    /**
     * 性能采样（成功/失败路径共用）
     */
    private void recordPerfSample(RelayInfo info, boolean success, long outputTokens) {
        try {
            long startMs = info.getStartTime() != null
                    ? info.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            long firstRespMs = info.getFirstResponseTime() != null
                    ? info.getFirstResponseTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            perfMetricsService.recordRelaySample(
                    info.getOriginModelName(),
                    info.getUsingGroup() != null ? info.getUsingGroup() : "default",
                    info.isStream(),
                    info.hasSendResponse(),
                    startMs,
                    firstRespMs,
                    success,
                    outputTokens);
        } catch (Exception ignore) {
            // 性能采样不影响主流程
        }
    }

    /**
     * 将 RelayException 写入 HTTP 响应（OpenAI / Claude 兼容格式）
     */
    private void writeApiError(HttpServletResponse resp, RelayException error, String relayFormat) {
        try {
            resp.setStatus(error.getStatusCode() != 0 ? error.getStatusCode() : 500);
            resp.setContentType("application/json;charset=UTF-8");

            Map<String, Object> errorBody = new LinkedHashMap<>();
            if ("claude".equals(relayFormat)) {
                Map<String, Object> claudeErr = new LinkedHashMap<>();
                claudeErr.put("type", "error");
                claudeErr.put("error", error.toClaudeError());
                resp.getWriter().write(Convert.toJSONString(claudeErr));
            } else {
                errorBody.put("error", error.toOpenAIError());
                resp.getWriter().write(Convert.toJSONString(errorBody));
            }
        } catch (Exception e) {
            log.error("Failed to write API error response", e);
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
