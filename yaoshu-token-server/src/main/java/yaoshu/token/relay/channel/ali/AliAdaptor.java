package yaoshu.token.relay.channel.ali;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 阿里渠道适配器  * <p>
 * 继承 OpenAIAdaptor，覆写 URL 构建、请求头设置、图片/Rerank 请求转换与响应处理。
 * 支持通义千问（Chat/Embedding）、通义万相（图片生成/编辑）、Rerank。
 */
@SuppressWarnings("unchecked")
@Slf4j
public class AliAdaptor extends OpenAIAdaptor {

    /** 是否为同步图片模型（多模态生成），异步模型需轮询任务 */
    private boolean isSyncImageModel = false;

    @Override
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("Authorization", "Bearer " + info.getApiKey());

        // 流式请求需要 X-DashScope-SSE
        if (info.isStream()) {
            headers.put("X-DashScope-SSE", "enable");
        }

        // 图片生成（异步）需要 X-DashScope-Async
        if (info.getRelayMode() == RelayModeEnum.IMAGES_GENERATIONS) {
            if (!isSyncImageModelByName(info.getOriginModelName())) {
                headers.put("X-DashScope-Async", "enable");
            }
        }

        // 图片编辑
        if (info.getRelayMode() == RelayModeEnum.IMAGES_EDITS) {
            if (AliImageWanHandler.isWanModel(info.getOriginModelName())) {
                headers.put("X-DashScope-Async", "enable");
            }
            headers.put("Content-Type", "application/json");
        }

        return headers;
    }

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String baseUrl = info.getChannelBaseUrl();

        // Claude 格式
        if ("claude".equals(info.getRelayFormat())) {
            if (supportsAliAnthropicMessages(info.getUpstreamModelName())) {
                return baseUrl + "/apps/anthropic/v1/messages";
            }
            return baseUrl + "/compatible-mode/v1/chat/completions";
        }

        switch (info.getRelayMode()) {
            case RelayModeEnum.EMBEDDINGS:
                return baseUrl + "/compatible-mode/v1/embeddings";
            case RelayModeEnum.RERANK:
                return baseUrl + "/api/v1/services/rerank/text-rerank/text-rerank";
            case RelayModeEnum.RESPONSES:
                return baseUrl + "/api/v2/apps/protocols/compatible-mode/v1/responses";
            case RelayModeEnum.IMAGES_GENERATIONS:
                if (isSyncImageModelByName(info.getOriginModelName())) {
                    return baseUrl + "/api/v1/services/aigc/multimodal-generation/generation";
                }
                return baseUrl + "/api/v1/services/aigc/text2image/image-synthesis";
            case RelayModeEnum.IMAGES_EDITS:
                if (AliImageWanHandler.isOldWanModel(info.getOriginModelName())) {
                    return baseUrl + "/api/v1/services/aigc/image2image/image-synthesis";
                } else if (AliImageWanHandler.isWanModel(info.getOriginModelName())) {
                    return baseUrl + "/api/v1/services/aigc/image-generation/generation";
                }
                return baseUrl + "/api/v1/services/aigc/multimodal-generation/generation";
            case RelayModeEnum.COMPLETIONS:
                return baseUrl + "/compatible-mode/v1/completions";
            default:
                return baseUrl + "/compatible-mode/v1/chat/completions";
        }
    }

    @Override
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");
        GeneralOpenAIRequest openAIRequest = (GeneralOpenAIRequest) request;
        return AliTextHandler.requestOpenAI2Ali(openAIRequest);
    }

    @Override
    public Object convertRerankRequest(RelayInfo info, int relayMode, RerankRequest rerankRequest) throws Exception {
        return AliRerankHandler.convertRerankRequest(rerankRequest);
    }

    @Override
    public Object convertImageRequest(RelayInfo info, OpenAIImageDTO imageRequest) throws Exception {
        if (info.getRelayMode() == RelayModeEnum.IMAGES_GENERATIONS) {
            if (isSyncImageModelByName(info.getOriginModelName())) {
                this.isSyncImageModel = true;
            }
            return AliImageHandler.oaiImage2AliImageRequest(info, imageRequest, isSyncImageModel);
        }
        if (info.getRelayMode() == RelayModeEnum.IMAGES_EDITS) {
            if (AliImageWanHandler.isOldWanModel(info.getOriginModelName())) {
                // 旧版 Wan 编辑需要表单数据，此处按无表单处理（表单场景由外层处理）
                return AliImageHandler.oaiImage2AliImageRequest(info, imageRequest, false);
            }
            if (isSyncImageModelByName(info.getOriginModelName())) {
                this.isSyncImageModel = !AliImageWanHandler.isWanModel(info.getOriginModelName());
            }
            return AliImageHandler.oaiImage2AliImageRequest(info, imageRequest, isSyncImageModel);
        }
        throw new UnsupportedOperationException("unsupported image relay mode: " + info.getRelayMode());
    }

    @Override
    public Object convertEmbeddingRequest(RelayInfo info, EmbeddingDTO embeddingRequest) throws Exception {
        return embeddingRequest; // passthrough
    }

    @Override
    public IAdaptor.DoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception {
        int relayMode = info.getRelayMode();

        switch (relayMode) {
            case RelayModeEnum.IMAGES_GENERATIONS:
            case RelayModeEnum.IMAGES_EDITS: {
                HttpResponse<InputStream> httpResponse = (HttpResponse<InputStream>) resp;
                Usage usage = AliImageHandler.aliImageHandler(info, httpResponse, isSyncImageModel);
                return DoResponseResult.success(usage);
            }
            case RelayModeEnum.RERANK: {
                HttpResponse<InputStream> httpResponse = (HttpResponse<InputStream>) resp;
                Usage usage = AliRerankHandler.rerankHandler(info, httpResponse);
                return DoResponseResult.success(usage);
            }
            default:
                // Chat / Embedding / Completions → 委托给 OpenAI 标准响应处理
                return super.doResponse(info, resp);
        }
    }

    @Override
    public List<String> getModelList() {
        return AliConstant.MODEL_LIST;
    }

    @Override
    public String getChannelName() {
        return AliConstant.CHANNEL_NAME;
    }

    // ======================== 内部辅助方法 ========================

    /** 同步图片模型判断（z-image/qwen-image/wan2.6） */
    private static boolean isSyncImageModelByName(String modelName) {
        if (modelName == null) return false;
        return modelName.contains("z-image")
                || modelName.contains("qwen-image")
                || modelName.contains("wan2.6");
    }

    /** 阿里 Anthropic Messages 协议支持判断 */
    private static boolean supportsAliAnthropicMessages(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) return false;
        String normalized = modelName.toLowerCase().trim();
        String[] patterns = {"qwen", "deepseek-v4", "kimi", "glm", "minimax-m"};
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) return true;
        }
        return false;
    }
}
