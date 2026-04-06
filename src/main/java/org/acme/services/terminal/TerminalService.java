package org.acme.services.terminal;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TerminalService {

    private static final Logger LOG = Logger.getLogger(TerminalService.class);

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    @Inject
    TtydPortAllocator portAllocator;

    @ConfigProperty(name = "tsd-agent.ttyd.command", defaultValue = "ttyd")
    String ttydCommand;

    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    public TerminalSession createSession(WorkspaceEntity entity, int cols, int rows) throws IOException {
        var manager = workspaceManagerResolver.resolve(entity.executionMode);
        var workspace = manager.getWorkspace(entity.workspaceId)
                .orElseThrow(() -> new IllegalStateException("Workspace not found: " + entity.workspaceId));

        var sessionId = UUID.randomUUID().toString();
        int port = portAllocator.allocate(sessionId);

        try {
            var ttydInfo = workspace.startTtyd(ttydCommand, port);
            var session = new TerminalSession(sessionId, ttydInfo.process(), ttydInfo.port());
            sessions.put(sessionId, session);

            LOG.infof("Terminal session %s created for workspace %s on port %d", sessionId, entity.workspaceId, port);
            return session;
        } catch (IOException e) {
            portAllocator.release(sessionId);
            throw e;
        }
    }

    public TerminalSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void destroySession(String sessionId) {
        var session = sessions.remove(sessionId);
        if (session != null) {
            session.ttydProcess().destroyForcibly();
            portAllocator.release(sessionId);
            LOG.infof("Terminal session %s destroyed", sessionId);
        }
    }

    @PreDestroy
    void cleanup() {
        sessions.values().forEach(session -> {
            try {
                session.ttydProcess().destroyForcibly();
                portAllocator.release(session.id());
            } catch (Exception e) {
                LOG.warnf("Failed to destroy terminal session %s: %s", session.id(), e.getMessage());
            }
        });
        sessions.clear();
    }

}
