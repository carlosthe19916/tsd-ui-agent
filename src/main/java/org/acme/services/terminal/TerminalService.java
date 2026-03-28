package org.acme.services.terminal;

import com.pty4j.WinSize;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TerminalService {

    private static final Logger LOG = Logger.getLogger(TerminalService.class);

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    public TerminalSession createSession(WorkspaceEntity entity, int cols, int rows) throws IOException {
        var manager = workspaceManagerResolver.resolve(entity.executionMode);
        var workspace = manager.getWorkspace(entity.workspaceId)
                .orElseThrow(() -> new IllegalStateException("Workspace not found: " + entity.workspaceId));

        var process = workspace.createPtyProcess(cols, rows);
        var sessionId = UUID.randomUUID().toString();
        var session = new TerminalSession(sessionId, process);
        sessions.put(sessionId, session);

        LOG.infof("Terminal session %s created for workspace %s", sessionId, entity.workspaceId);
        return session;
    }

    public void resize(String sessionId, int cols, int rows) {
        var session = sessions.get(sessionId);
        if (session != null) {
            session.process().setWinSize(new WinSize(cols, rows));
        }
    }

    public void writeToSession(String sessionId, byte[] data) throws IOException {
        var session = sessions.get(sessionId);
        if (session != null) {
            var os = session.process().getOutputStream();
            os.write(data);
            os.flush();
        }
    }

    public void destroySession(String sessionId) {
        var session = sessions.remove(sessionId);
        if (session != null) {
            session.process().destroyForcibly();
            LOG.infof("Terminal session %s destroyed", sessionId);
        }
    }

    @PreDestroy
    void cleanup() {
        sessions.values().forEach(session -> {
            try {
                session.process().destroyForcibly();
            } catch (Exception e) {
                LOG.warnf("Failed to destroy terminal session %s: %s", session.id(), e.getMessage());
            }
        });
        sessions.clear();
    }

}
