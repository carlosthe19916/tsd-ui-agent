package org.acme.services.workspace.devcontainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.acme.services.devcontainer.DevcontainerSpec;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DevcontainerTemplateTest {

    @Inject
    ObjectMapper objectMapper;

    private String render(DevcontainerSpec config) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
    }

    private DevcontainerSpec minimal() {
        DevcontainerSpec config = new DevcontainerSpec();
        config.image = "node:20";
        config.remoteUser = "node";
        config.workspaceFolder = "/workspaces/trees/abc123";
        config.containerEnv = Map.of("DEVCONTAINER", "true");
        config.runArgs = List.of("--tmpfs=/tmp:rw,exec,nosuid,nodev,mode=1777");
        config.postCreateCommand = "echo hello";
        return config;
    }

    @Test
    void testMinimalTemplate() throws Exception {
        String rendered = render(minimal());

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertEquals("node:20", json.getString("image"));
        assertEquals("node", json.getString("remoteUser"));
        assertEquals("/workspaces/trees/abc123", json.getString("workspaceFolder"));
        assertEquals("echo hello", json.getString("postCreateCommand"));
        assertEquals("true", json.getJsonObject("containerEnv").getString("DEVCONTAINER"));
        assertTrue(json.containsKey("runArgs"));
        assertFalse(json.containsKey("features"));
        assertFalse(json.containsKey("customizations"));
        assertFalse(json.containsKey("mounts"));
        assertFalse(json.containsKey("postStartCommand"));
        assertFalse(json.containsKey("appPort"));
    }

    @Test
    void testTemplateWithFeatures() throws Exception {
        DevcontainerSpec config = minimal();
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/xyz";
        config.postCreateCommand = "echo setup";
        config.features = new LinkedHashMap<>();
        config.features.put("ghcr.io/devcontainers/features/java:1", Map.of("version", "25"));
        config.features.put("ghcr.io/devcontainers/features/git:1", Map.of());

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertTrue(json.containsKey("features"));
        JsonObject features = json.getJsonObject("features");
        assertTrue(features.containsKey("ghcr.io/devcontainers/features/java:1"));
        assertEquals("25", features.getJsonObject("ghcr.io/devcontainers/features/java:1").getString("version"));
        assertTrue(features.containsKey("ghcr.io/devcontainers/features/git:1"));
        assertFalse(json.containsKey("customizations"));
        assertFalse(json.containsKey("overrideFeatureInstallOrder"));
    }

    @Test
    void testTemplateWithVscodeExtensions() throws Exception {
        DevcontainerSpec config = minimal();
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/ext";
        config.postCreateCommand = "echo setup";
        config.setVscodeExtensions(List.of("vscjava.vscode-java-pack", "ms-python.python"));

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertTrue(json.containsKey("customizations"));
        JsonObject vscode = json.getJsonObject("customizations").getJsonObject("vscode");
        List<String> extensions = vscode.getJsonArray("extensions").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("vscjava.vscode-java-pack", "ms-python.python"), extensions);
    }

    @Test
    void testTemplateWithFeaturesAndExtensions() throws Exception {
        DevcontainerSpec config = new DevcontainerSpec();
        config.image = "mcr.microsoft.com/devcontainers/base:ubuntu";
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/full";
        config.runArgs = List.of("--tmpfs=/tmp:rw,exec,nosuid,nodev,mode=1777");
        config.postCreateCommand = "npm install";

        Map<String, String> envVars = new LinkedHashMap<>();
        envVars.put("DEVCONTAINER", "true");
        envVars.put("MY_VAR", "my_value");
        config.containerEnv = envVars;

        config.mounts = List.of("source=vol1,target=/home/vscode/.config,type=volume");

        config.features = new LinkedHashMap<>();
        config.features.put("ghcr.io/devcontainers/features/java:1", Map.of("version", "21"));
        config.features.put("ghcr.io/devcontainers/features/node:1", Map.of("version", "20"));
        config.features.put("ghcr.io/devcontainers/features/common-utils:2", Map.of());

        config.setVscodeExtensions(List.of("vscjava.vscode-java-pack", "dbaeumer.vscode-eslint"));

        config.overrideFeatureInstallOrder = List.of(
                "ghcr.io/devcontainers/features/node:1",
                "ghcr.io/devcontainers/features/java:1",
                "ghcr.io/devcontainers/features/common-utils:2");

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        // Image
        assertEquals("mcr.microsoft.com/devcontainers/base:ubuntu", json.getString("image"));

        // Features with versions
        JsonObject features = json.getJsonObject("features");
        assertEquals(3, features.size());
        assertEquals("21", features.getJsonObject("ghcr.io/devcontainers/features/java:1").getString("version"));
        assertEquals("20", features.getJsonObject("ghcr.io/devcontainers/features/node:1").getString("version"));
        assertTrue(features.getJsonObject("ghcr.io/devcontainers/features/common-utils:2").isEmpty());

        // VS Code extensions
        List<String> extensions = json.getJsonObject("customizations")
                .getJsonObject("vscode")
                .getJsonArray("extensions").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("vscjava.vscode-java-pack", "dbaeumer.vscode-eslint"), extensions);

        // Env vars
        JsonObject env = json.getJsonObject("containerEnv");
        assertEquals("true", env.getString("DEVCONTAINER"));
        assertEquals("my_value", env.getString("MY_VAR"));

        // Mounts
        assertEquals(1, json.getJsonArray("mounts").size());
        assertEquals("source=vol1,target=/home/vscode/.config,type=volume",
                json.getJsonArray("mounts").getString(0));

        // Override feature install order
        assertTrue(json.containsKey("overrideFeatureInstallOrder"));
        List<String> installOrder = json.getJsonArray("overrideFeatureInstallOrder").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("ghcr.io/devcontainers/features/node:1",
                "ghcr.io/devcontainers/features/java:1",
                "ghcr.io/devcontainers/features/common-utils:2"), installOrder);
    }

    @Test
    void testTemplateWithoutImage() throws Exception {
        DevcontainerSpec config = minimal();
        config.image = null;
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/noimg";
        config.postCreateCommand = "echo setup";

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertFalse(json.containsKey("image"));
        assertEquals("vscode", json.getString("remoteUser"));
    }

    @Test
    void testTemplateWithPostStartCommandAndAppPort() throws Exception {
        DevcontainerSpec config = minimal();
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/opencode";
        config.postCreateCommand = "echo create";
        config.postStartCommand = "opencode serve --port 3000";
        config.waitFor = "postStartCommand";
        config.appPort = List.of(3000);

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertEquals("echo create", json.getString("postCreateCommand"));
        assertEquals("opencode serve --port 3000", json.getString("postStartCommand"));
        assertEquals("postStartCommand", json.getString("waitFor"));
        assertEquals(3000, json.getJsonArray("appPort").getInt(0));
    }

    @Test
    void testTemplateWithFeaturesNoVersionOption() throws Exception {
        DevcontainerSpec config = minimal();
        config.workspaceFolder = "/workspaces/trees/noversion";
        config.postCreateCommand = "echo setup";
        config.features = new LinkedHashMap<>();
        config.features.put("ghcr.io/devcontainers/features/common-utils:2", Map.of());
        config.features.put("ghcr.io/devcontainers/features/git:1", Map.of());

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        JsonObject features = json.getJsonObject("features");
        assertEquals(2, features.size());
        assertTrue(features.getJsonObject("ghcr.io/devcontainers/features/common-utils:2").isEmpty());
        assertTrue(features.getJsonObject("ghcr.io/devcontainers/features/git:1").isEmpty());
    }

    @Test
    void testTemplateMultipleEnvVars() throws Exception {
        DevcontainerSpec config = minimal();
        config.workspaceFolder = "/workspaces/trees/multi";
        config.postCreateCommand = "echo setup";
        Map<String, String> envVars = new LinkedHashMap<>();
        envVars.put("DEVCONTAINER", "true");
        envVars.put("FOO", "bar");
        envVars.put("BAZ", "qux");
        config.containerEnv = envVars;

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        JsonObject env = json.getJsonObject("containerEnv");
        assertEquals(3, env.size());
        assertEquals("true", env.getString("DEVCONTAINER"));
        assertEquals("bar", env.getString("FOO"));
        assertEquals("qux", env.getString("BAZ"));
    }

    @Test
    void testTemplateSingleVscodeExtension() throws Exception {
        DevcontainerSpec config = minimal();
        config.workspaceFolder = "/workspaces/trees/single-ext";
        config.postCreateCommand = "echo setup";
        config.setVscodeExtensions(List.of("ms-python.python"));

        String rendered = render(config);
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        List<String> extensions = json.getJsonObject("customizations")
                .getJsonObject("vscode")
                .getJsonArray("extensions").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("ms-python.python"), extensions);
    }

    @Test
    void testRenderedJsonIsValid() throws Exception {
        DevcontainerSpec config = new DevcontainerSpec();
        config.image = "node:20";
        config.remoteUser = "vscode";
        config.workspaceFolder = "/workspaces/trees/valid";
        config.runArgs = List.of("--tmpfs=/tmp:rw,exec,nosuid,nodev,mode=1777");
        config.postCreateCommand = "echo create";
        config.postStartCommand = "echo start";
        config.waitFor = "postStartCommand";
        config.appPort = List.of(8080);

        Map<String, String> envVars = new LinkedHashMap<>();
        envVars.put("DEVCONTAINER", "true");
        envVars.put("KEY", "value");
        config.containerEnv = envVars;

        config.mounts = List.of("source=vol,target=/home/vscode/.data,type=volume");

        config.features = new LinkedHashMap<>();
        config.features.put("ghcr.io/devcontainers/features/java:1", Map.of("version", "25"));

        config.setVscodeExtensions(List.of("vscjava.vscode-java-pack", "ms-python.python"));

        config.overrideFeatureInstallOrder = List.of("ghcr.io/devcontainers/features/java:1");

        String rendered = render(config);

        // Should not throw — proves the config renders valid JSON
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();
        assertNotNull(json);
        // image, remoteUser, workspaceFolder, overrideFeatureInstallOrder, features, customizations, runArgs, containerEnv, mounts, postCreateCommand, postStartCommand, waitFor, appPort
        assertEquals(13, json.size());
        assertTrue(json.containsKey("runArgs"));
        assertEquals("--tmpfs=/tmp:rw,exec,nosuid,nodev,mode=1777",
                json.getJsonArray("runArgs").getString(0));
    }
}
