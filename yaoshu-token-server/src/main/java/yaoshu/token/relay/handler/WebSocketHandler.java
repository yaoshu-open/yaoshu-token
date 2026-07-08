package yaoshu.token.relay.handler;

import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.pojo.dto.OpenAIError;
import yaoshu.token.pojo.dto.RealtimeEvent;
import yaoshu.token.relay.RelayAdaptor;
import yaoshu.token.relay.channel.ApiRequestExecutor;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAudioRealtimeHandler;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.BillingService;
import yaoshu.token.service.QuotaService;
import yaoshu.token.service.TokenCounterService;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * WebSocket 中转处理器  */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final TokenCounterService tokenCounterService;
    private final QuotaService quotaService;
    private final BillingService billingService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        RelayInfo info = getRelayInfo(session);
        info.setClientWsSession(session);

        IAdaptor adaptor = RelayAdaptor.getAdaptor(info.getApiType());
        if (adaptor == null) {
            closeWithError(session, info,
                    CompatibleHandler.newApiError("invalid api type: " + info.getApiType(),
                            ErrorCode.INVALID_REQUEST, 400, true));
            return;
        }
        adaptor.init(info);

        WebSocket.Listener upstreamListener = new UpstreamWebSocketListener(session, info);
        try {
            info.setTargetWsSession(ApiRequestExecutor.doWssRequest(adaptor, info, info.getClientHeaders(), upstreamListener));
        } catch (Exception e) {
            closeWithError(session, info,
                    CompatibleHandler.newApiError(e, "do_request_failed", 500, false));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RelayInfo info = getRelayInfo(session);
        if (info.getTargetWsSession() == null) {
            closeWithError(session, info,
                    CompatibleHandler.newApiError("target websocket is not connected", "bad_response", 500, false));
            return;
        }
        OpenAIAudioRealtimeHandler.handleClientEvent(info, message.getPayload(), tokenCounterService);
        info.getTargetWsSession().sendText(message.getPayload(), true).join();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        RelayInfo info = getRelayInfo(session);
        closeWithError(session, info, CompatibleHandler.newApiError(exception, "websocket_error", 500, false));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        RelayInfo info = getRelayInfo(session);
        closeRealtimeSession(info, session, status);
    }

    private RelayInfo getRelayInfo(WebSocketSession session) {
        Object info = session.getAttributes().get(RelayInfo.class.getName());
        if (!(info instanceof RelayInfo relayInfo)) {
            throw CompatibleHandler.newApiError("missing realtime relay info", "invalid_request", 400, true);
        }
        return relayInfo;
    }

    private void closeWithError(WebSocketSession session, RelayInfo info, RelayException error) throws Exception {
        if (session.isOpen()) {
            RealtimeEvent event = new RealtimeEvent();
            event.setType(RealtimeEvent.TYPE_ERROR);
            OpenAIError openAIError = error.toOpenAIError();
            event.setError(openAIError);
            session.sendMessage(new TextMessage(Convert.toJSONString(event)));
        }
        closeRealtimeSession(info, session, CloseStatus.SERVER_ERROR);
    }

    private void closeRealtimeSession(RelayInfo info, WebSocketSession session, CloseStatus status) throws Exception {
        if (!info.getRealtimeClosed().compareAndSet(false, true)) {
            return;
        }
        OpenAIAudioRealtimeHandler.flushPendingUsage(info, quotaService, billingService);
        if (info.getTargetWsSession() != null) {
            try {
                info.getTargetWsSession().sendClose(WebSocket.NORMAL_CLOSURE, status != null ? status.getReason() : "normal").join();
            } catch (Exception e) {
                log.debug("Failed to close upstream websocket: {}", e.getMessage());
            }
        }
        if (session != null && session.isOpen()) {
            session.close(status != null ? status : CloseStatus.NORMAL);
        }
    }

    private final class UpstreamWebSocketListener implements WebSocket.Listener {

        private final WebSocketSession clientSession;
        private final RelayInfo info;

        private UpstreamWebSocketListener(WebSocketSession clientSession, RelayInfo info) {
            this.clientSession = clientSession;
            this.info = info;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            info.setTargetWsSession(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                info.setFirstResponseTime();
                String payload = data.toString();
                OpenAIAudioRealtimeHandler.handleUpstreamEvent(
                        info, payload, tokenCounterService, quotaService, billingService);
                if (clientSession.isOpen()) {
                    clientSession.sendMessage(new TextMessage(payload, last));
                }
            } catch (Exception e) {
                try {
                    closeWithError(clientSession, info,
                            CompatibleHandler.newApiError(e, "do_response_failed", 500, false));
                } catch (Exception closeException) {
                    log.debug("Failed to close websocket after upstream error: {}", closeException.getMessage());
                }
            } finally {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            try {
                closeRealtimeSession(info, clientSession, new CloseStatus(statusCode, reason));
            } catch (Exception e) {
                log.debug("Failed to close realtime session after upstream close: {}", e.getMessage());
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            try {
                closeWithError(clientSession, info,
                        CompatibleHandler.newApiError(error, "websocket_error", 500, false));
            } catch (Exception e) {
                log.debug("Failed to close realtime session after upstream onError: {}", e.getMessage());
            }
        }
    }
}
