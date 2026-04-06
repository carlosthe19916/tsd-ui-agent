package org.acme.resources;

import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

import io.smallrye.common.annotation.Blocking;
import io.vertx.core.buffer.Buffer;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.terminal.TerminalService;
import org.acme.services.terminal.TerminalSession;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

@WebSocket(path = "/ws/terminal/{workspaceEntityId}")
public class TerminalWebSocket {

    private static final Logger LOG = Logger.getLogger(TerminalWebSocket.class);
    private static final char SOH = '\u0001';
    private static final TypedKey<String> SESSION_KEY = TypedKey.forString("terminalSessionId");
    private static final TypedKey<java.net.http.WebSocket> UPSTREAM_KEY = new TypedKey<>("upstreamWs");

    @Inject
    TerminalService terminalService;

    @Blocking
    @Transactional
    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        try {
            Long workspaceEntityId = Long.valueOf(connection.pathParam("workspaceEntityId"));
            LOG.infof("Terminal WebSocket opened for workspace entity %d (connection %s)",
                    workspaceEntityId, connection.id());

            WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
            if (entity == null) {
                LOG.warnf("Workspace entity %d not found", workspaceEntityId);
                return SOH + "{\"type\":\"error\",\"message\":\"Workspace not found\"}";
            }
            if (entity.workspaceId == null || entity.workspaceId.isBlank()) {
                LOG.warnf("Workspace entity %d not provisioned", workspaceEntityId);
                return SOH + "{\"type\":\"error\",\"message\":\"Workspace not provisioned\"}";
            }
            if (entity.isProvisioningInProgress) {
                return SOH + "{\"type\":\"error\",\"message\":\"Workspace is still provisioning\"}";
            }

            TerminalSession session = terminalService.createSession(entity, 80, 24);
            connection.userData().put(SESSION_KEY, session.id());

            // Connect upstream to ttyd WebSocket
            URI ttydUri = URI.create("ws://localhost:" + session.port() + "/ws");
            HttpClient httpClient = HttpClient.newHttpClient();

            java.net.http.WebSocket upstream = httpClient.newWebSocketBuilder()
                    .subprotocols("tty")
                    .buildAsync(ttydUri, new Listener() {

                        private final StringBuilder textBuffer = new StringBuilder();

                        @Override
                        public void onOpen(java.net.http.WebSocket webSocket) {
                            // ttyd requires an auth token as the first message (empty JSON for no-auth)
                            webSocket.sendText("{}", true);
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onBinary(java.net.http.WebSocket webSocket, ByteBuffer data, boolean last) {
                            byte[] bytes = new byte[data.remaining()];
                            data.get(bytes);
                            connection.sendBinaryAndAwait(Buffer.buffer(bytes));
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                            textBuffer.append(data);
                            if (last) {
                                String text = textBuffer.toString();
                                textBuffer.setLength(0);
                                connection.sendTextAndAwait(text);
                            }
                            webSocket.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
                            connection.sendTextAndAwait(SOH + "{\"type\":\"exit\",\"code\":0}");
                            connection.close().subscribe().with(v -> {}, e -> {});
                            return null;
                        }

                        @Override
                        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                            LOG.debugf("Upstream ttyd WebSocket error: %s", error.getMessage());
                            connection.sendTextAndAwait(
                                    SOH + "{\"type\":\"error\",\"message\":\"Terminal connection lost\"}");
                            connection.close().subscribe().with(v -> {}, e -> {});
                        }
                    })
                    .join();

            connection.userData().put(UPSTREAM_KEY, upstream);

            return SOH + "{\"type\":\"ready\"}";
        } catch (Exception e) {
            LOG.errorf(e, "Failed to open terminal WebSocket");
            return SOH + "{\"type\":\"error\",\"message\":\"Failed to start terminal: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    @OnBinaryMessage
    public void onBinaryMessage(WebSocketConnection connection, Buffer message) {
        java.net.http.WebSocket upstream = connection.userData().get(UPSTREAM_KEY);
        if (upstream != null) {
            ByteBuffer data = ByteBuffer.wrap(message.getBytes());
            upstream.sendBinary(data, true);
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        String sessionId = connection.userData().get(SESSION_KEY);
        java.net.http.WebSocket upstream = connection.userData().get(UPSTREAM_KEY);

        if (upstream != null) {
            upstream.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "client disconnected");
        }
        if (sessionId != null) {
            LOG.infof("Terminal WebSocket closed, destroying session %s (connection %s)",
                    sessionId, connection.id());
            terminalService.destroySession(sessionId);
        }
    }
}
