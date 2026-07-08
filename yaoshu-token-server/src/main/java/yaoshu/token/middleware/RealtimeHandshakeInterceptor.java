package yaoshu.token.middleware;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.pojo.dto.ChannelOtherSettingsDTO;
import yaoshu.token.pojo.dto.ChannelSettingsDTO;
import yaoshu.token.pojo.dto.RelayFormat;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Realtime 握手拦截器：将 Filter 链写入的上下文转为 RelayInfo。
 */
@Component
@Slf4j
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        RelayInfo info = buildRelayInfo(httpRequest);
        info.setClientHeaders(extractClientHeaders(httpRequest));
        info.setClientWsSession(null);
        attributes.put(RelayInfo.class.getName(), info);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    @SuppressWarnings("unchecked")
    private RelayInfo buildRelayInfo(HttpServletRequest req) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(LocalDateTime.now());
        info.setRelayMode(RelayModeEnum.REALTIME);
        info.setStream(true);
        info.setRelayFormat(RelayFormat.OPENAI_REALTIME);
        info.setRequestURLPath(buildRequestPath(req));

        Integer channelId = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_ID);
        Integer channelType = (Integer) req.getAttribute(ContextKeyConstants.CHANNEL_TYPE);
        if (channelId != null) info.setChannelId(channelId);
        if (channelType != null) info.setChannelType(channelType);

        info.setChannelBaseUrl((String) req.getAttribute(ContextKeyConstants.CHANNEL_BASE_URL));
        info.setApiKey((String) req.getAttribute(ContextKeyConstants.CHANNEL_KEY));
        info.setOrganization((String) req.getAttribute(ContextKeyConstants.CHANNEL_ORGANIZATION));
        info.setApiType(channelType != null ? channelType : 0);

        Object settingObj = req.getAttribute(ContextKeyConstants.CHANNEL_SETTING);
        if (settingObj instanceof ChannelSettingsDTO cs) info.setChannelSetting(cs);
        Object otherSettingObj = req.getAttribute(ContextKeyConstants.CHANNEL_OTHER_SETTING);
        if (otherSettingObj instanceof ChannelOtherSettingsDTO cos) info.setChannelOtherSettings(cos);

        info.setTokenId(toInt(req.getAttribute(ContextKeyConstants.TOKEN_ID)));
        info.setUserId(toInt(req.getAttribute(ContextKeyConstants.USER_ID)));
        info.setTokenGroup((String) req.getAttribute(ContextKeyConstants.TOKEN_GROUP));
        info.setTokenKey((String) req.getAttribute(ContextKeyConstants.TOKEN_KEY));
        info.setTokenUnlimited(Boolean.TRUE.equals(req.getAttribute(ContextKeyConstants.TOKEN_UNLIMITED_QUOTA)));
        info.setUserGroup((String) req.getAttribute(ContextKeyConstants.USER_GROUP));
        info.setUsingGroup((String) req.getAttribute(ContextKeyConstants.USING_GROUP));
        info.setUserEmail((String) req.getAttribute(ContextKeyConstants.USER_EMAIL));
        info.setUserQuota(toInt(req.getAttribute(ContextKeyConstants.USER_QUOTA)));
        info.setChannelCreateTime(toLong(req.getAttribute(ContextKeyConstants.CHANNEL_CREATE_TIME)));

        String model = req.getParameter("model");
        info.setOriginModelName(model);
        info.setUpstreamModelName(resolveUpstreamModelName(req, model));
        info.setModelMapped(info.getUpstreamModelName() != null && !info.getUpstreamModelName().equals(model));
        return info;
    }

    private static String resolveUpstreamModelName(HttpServletRequest req, String model) {
        String mappingJson = (String) req.getAttribute(ContextKeyConstants.CHANNEL_MODEL_MAPPING);
        if (mappingJson != null && !mappingJson.isEmpty() && !"{}".equals(mappingJson) && model != null) {
            try {
                Map<String, Object> mapping = Convert.toJSONObject(mappingJson);
                Object mapped = mapping.get(model);
                if (mapped != null && !mapped.toString().isEmpty()) {
                    return mapped.toString();
                }
            } catch (Exception e) {
                log.debug("realtime model_mapping 解析失败: {}", e.getMessage());
            }
        }
        return model;
    }

    private static Map<String, String> extractClientHeaders(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, req.getHeader(name));
        }
        return headers;
    }

    private static String buildRequestPath(HttpServletRequest req) {
        String path = req.getRequestURI();
        String query = req.getQueryString();
        if (query == null || query.isEmpty()) {
            return path;
        }
        return path + "?" + query;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
