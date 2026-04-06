package org.acme.services.terminal;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TtydPortAllocator {

    private static final Logger LOG = Logger.getLogger(TtydPortAllocator.class);

    @ConfigProperty(name = "tsd-agent.ttyd.port-range-start", defaultValue = "14000")
    int portRangeStart;

    @ConfigProperty(name = "tsd-agent.ttyd.port-range-end", defaultValue = "14099")
    int portRangeEnd;

    private final ConcurrentHashMap<String, Integer> allocatedPorts = new ConcurrentHashMap<>();

    public synchronized int allocate(String sessionId) {
        for (int port = portRangeStart; port <= portRangeEnd; port++) {
            if (allocatedPorts.containsValue(port)) {
                continue;
            }
            if (!isPortFree(port)) {
                continue;
            }
            allocatedPorts.put(sessionId, port);
            LOG.infof("Allocated ttyd port %d for session %s", port, sessionId);
            return port;
        }
        throw new RuntimeException("No free ports in range " + portRangeStart + "-" + portRangeEnd);
    }

    public void release(String sessionId) {
        Integer port = allocatedPorts.remove(sessionId);
        if (port != null) {
            LOG.infof("Released ttyd port %d for session %s", port, sessionId);
        }
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
