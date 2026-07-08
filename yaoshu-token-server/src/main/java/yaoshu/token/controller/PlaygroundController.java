package yaoshu.token.controller;

import ai.yue.library.base.convert.Convert;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.pojo.dto.ChannelOtherSettingsDTO;
import yaoshu.token.pojo.dto.ChannelSettingsDTO;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.handler.CompatibleHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Playground 控制器  * <p>
 * Go 路由：POST /pg/chat/completions → Playground → GenRelayInfo → setupContextForToken → Relay
 * 认证：UserAuth（Sa-Token 会话）+ SystemPerformanceCheck + Distribute（Filter 层处理）
 * <p>
 * 鉴权链路（Java 翻译）：
 * 1. AuthFilter（order=200）：/pg/* 走 Sa-Token 会话认证，注入 id/username/role/group
 * 2. DistributorFilter（order=500）：依据 group 进行渠道分发，设置渠道上下文
 * 3. PlaygroundController：构建临时 Token 上下文（非持久化  */
@Slf4j
@RestController
@RequestMapping("/pg")
@RequiredArgsConstructor
public class PlaygroundController {

    private final RelayController relayController;

    /**
     * Playground Chat Completions      * <p>
     * Go 逻辑：生成临时 Token → 设置上下文 → 调用 relay.Relay(c, RelayFormatOpenAI)
     * Java：Filter 层已完成鉴权与分发，Controller 构建临时 Token 上下文 + RelayInfo，委托 RelayController.dispatchRelay 走完整重试编排。
     */
    @PostMapping("/chat/completions")
    public void chatCompletions(HttpServletRequest request, HttpServletResponse response) {
        RelayInfo info = buildPlaygroundRelayInfo(request);
        info.setRelayMode(RelayModeEnum.CHAT_COMPLETIONS);
        relayController.dispatchRelay(request, response, info, "openai");
    }

    /**
     * 从 request attributes 构建 RelayInfo（对齐 RelayController.buildRelayInfo）
     * <p>
     * 与 RelayController 的差异：Playground 走 Sa-Token 会话认证（无 API Key Token），
     * 因此创建临时 Token 上下文，而非从 request attribute 读取。      */
    @SuppressWarnings("unchecked")
    private RelayInfo buildPlaygroundRelayInfo(HttpServletRequest req) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());

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
                Map<String, Object> headerOverride = Convert.toJSONObject(headerOverrideJson);
                info.setHeadersOverride(headerOverride);
            } catch (Exception e) {
                log.debug("解析 header override JSON 失败", e);
            }
        }
        String paramOverrideJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_PARAM_OVERRIDE);
        if (paramOverrideJson != null && !paramOverrideJson.isEmpty()) {
            try {
                Map<String, Object> paramOverride = Convert.toJSONObject(paramOverrideJson);
                info.setParamOverride(paramOverride);
            } catch (Exception e) {
                log.debug("解析 param override JSON 失败", e);
            }
        }

        // 临时 Token 上下文
        // Playground 走 Sa-Token 会话认证，无持久化 Token，创建临时 Token 供 Relay 计费/限流链路使用
        Integer userId = toInt(req.getAttribute("id"));
        String group = (String) req.getAttribute(ContextKeyConstants.USING_GROUP);
        if (group == null || group.isEmpty()) {
            group = (String) req.getAttribute("group");
        }
        info.setUserId(userId);
        info.setTokenId(0);  // 临时 Token，无 ID         info.setTokenGroup(group);
        info.setTokenKey("playground-" + (group != null ? group : "default"));
        info.setTokenUnlimited(true);  // Playground 临时 Token（tokenId=0）无数据库记录，设为 unlimited 让计费信任旁路生效，基于用户额度计费

        // 原始模型名（DistributorFilter 解析的 model 参数）
        String modelName = (String) req.getAttribute("original_model");
        if (modelName != null && !modelName.isEmpty()) {
            info.setOriginModelName(modelName);
        }

        // 请求体解析（对齐 RelayController.parseRequestBody，确保 info.setRequest() 填充）
        parseRequestBody(req, info);

        // Playground 路径转换：/pg/chat/completions → /v1/chat/completions
        String requestPath = req.getRequestURI();
        if (requestPath.startsWith("/pg")) {
            info.setPlayground(true);
            requestPath = "/v1" + requestPath.substring(3);
        }
        info.setRequestURLPath(requestPath);
        return info;
    }

    /**
     * 请求体解析，对应 RelayController.parseRequestBody
     * <p>
     * 优先复用 DistributorFilter 缓存的 parsed body，兜底自行读取。
     * 提取 model 名并填充 RelayInfo.originModelName / upstreamModelName。
     */
    private void parseRequestBody(HttpServletRequest req, RelayInfo info) {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.contains("application/json")) return;

        try {
            // 优先复用 DistributorFilter 缓存的 parsed body
            Map<String, Object> body = (Map<String, Object>) req.getAttribute(ContextKeyConstants.PARSED_REQUEST_BODY);
            if (body == null) {
                // 兜底：自行读取（readAllBytes() 配合 RepeatedlyReadServletRequestWrapper 可重复读取）
                body = Convert.toJSONObject(
                        new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
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

    private Integer toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return null;
    }
}
