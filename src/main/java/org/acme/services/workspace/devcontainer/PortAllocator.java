package org.acme.services.workspace.devcontainer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@ApplicationScoped
public class PortAllocator {

    private static final Logger LOG = Logger.getLogger(PortAllocator.class);

    @ConfigProperty(name = "tsd-agent.devcontainer.port-range-start", defaultValue = "13000")
    int portRangeStart;

    @ConfigProperty(name = "tsd-agent.devcontainer.port-range-end", defaultValue = "13099")
    int portRangeEnd;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    private Path portsFilePath() {
        return Path.of(baseDir, "devcontainers", "ports.json");
    }

    public synchronized int allocate(String workspaceKey) {
        JsonObject ports = readPortsFile();

        if (ports.containsKey(workspaceKey)) {
            int existing = ports.getJsonObject(workspaceKey).getInt("port");
            LOG.debugf("Reusing existing port %d for workspace %s", existing, workspaceKey);
            return existing;
        }

        for (int port = portRangeStart; port <= portRangeEnd; port++) {
            if (isPortAssigned(ports, port)) {
                continue;
            }
            if (!isPortFree(port)) {
                continue;
            }

            JsonObjectBuilder entry = Json.createObjectBuilder()
                    .add("port", port)
                    .add("started", Instant.now().toString());

            JsonObjectBuilder updated = Json.createObjectBuilder();
            ports.forEach(updated::add);
            updated.add(workspaceKey, entry);

            writePortsFile(updated.build());
            LOG.infof("Allocated port %d for workspace %s", port, workspaceKey);
            return port;
        }

        throw new RuntimeException("No free ports in range " + portRangeStart + "-" + portRangeEnd);
    }

    public synchronized void release(String workspaceKey) {
        JsonObject ports = readPortsFile();
        if (!ports.containsKey(workspaceKey)) {
            return;
        }

        JsonObjectBuilder updated = Json.createObjectBuilder();
        ports.forEach((key, value) -> {
            if (!key.equals(workspaceKey)) {
                updated.add(key, value);
            }
        });

        writePortsFile(updated.build());
        LOG.infof("Released port for workspace %s", workspaceKey);
    }

    private boolean isPortAssigned(JsonObject ports, int port) {
        for (JsonValue value : ports.values()) {
            if (value instanceof JsonObject entry && entry.getInt("port") == port) {
                return true;
            }
        }
        return false;
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject readPortsFile() {
        Path path = portsFilePath();
        if (!Files.exists(path)) {
            return Json.createObjectBuilder().build();
        }
        try {
            String content = Files.readString(path);
            return Json.createReader(new StringReader(content)).readObject();
        } catch (Exception e) {
            LOG.warnf("Failed to read ports file, starting fresh: %s", e.getMessage());
            return Json.createObjectBuilder().build();
        }
    }

    private void writePortsFile(JsonObject ports) {
        try {
            Path path = portsFilePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, ports.toString());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to write ports file");
        }
    }
}
