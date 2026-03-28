package org.acme.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

import io.smallrye.common.annotation.Blocking;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.terminal.TerminalService;
import org.acme.services.terminal.TerminalSession;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@WebSocket(path = "/ws/terminal/{workspaceEntityId}")
public class TerminalWebSocket {

    private static final Logger LOG = Logger.getLogger(TerminalWebSocket.class);
    private static final char SOH = '\u0001';
    private static final TypedKey<String> SESSION_KEY = TypedKey.forString("terminalSessionId");

    @Inject
    TerminalService terminalService;

    @Inject
    ObjectMapper objectMapper;

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

            Thread.ofVirtual().name("terminal-reader-" + session.id()).start(() -> {
                try {
                    InputStream is = session.process().getInputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        connection.sendTextAndAwait(output);
                    }
                    int exitCode = session.process().waitFor();
                    connection.sendTextAndAwait(SOH + "{\"type\":\"exit\",\"code\":" + exitCode + "}");
                    connection.close().subscribe().with(v -> {}, e -> {});
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        LOG.debugf("Terminal reader ended for connection %s: %s", connection.id(), e.getMessage());
                    }
                }
            });

            return SOH + "{\"type\":\"ready\"}";
        } catch (Exception e) {
            LOG.errorf(e, "Failed to open terminal WebSocket");
            return SOH + "{\"type\":\"error\",\"message\":\"Failed to start terminal: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    @Blocking
    @OnTextMessage
    public void onMessage(WebSocketConnection connection, String message) {
        String sessionId = (String) connection.userData().get(SESSION_KEY);
        if (sessionId == null) {
            return;
        }

        try {
            if (!message.isEmpty() && message.charAt(0) == SOH) {
                String json = message.substring(1);
                JsonNode node = objectMapper.readTree(json);
                String type = node.path("type").asText();
                if ("resize".equals(type)) {
                    int cols = node.path("cols").asInt();
                    int rows = node.path("rows").asInt();
                    terminalService.resize(sessionId, cols, rows);
                }
            } else {
                terminalService.writeToSession(sessionId, message.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            LOG.debugf("Error processing terminal input: %s", e.getMessage());
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        String sessionId = (String) connection.userData().get(SESSION_KEY);
        if (sessionId != null) {
            LOG.infof("Terminal WebSocket closed, destroying session %s (connection %s)",
                    sessionId, connection.id());
            terminalService.destroySession(sessionId);
        }
    }
}
