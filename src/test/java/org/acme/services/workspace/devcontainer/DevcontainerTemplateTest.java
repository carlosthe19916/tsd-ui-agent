package org.acme.services.workspace.devcontainer;

import io.quarkus.qute.RawString;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DevcontainerTemplateTest {

    @Test
    void testMinimalTemplate() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "node", "/workspaces/trees/abc123",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo hello", null, null, null, null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertEquals("node:20", json.getString("image"));
        assertEquals("node", json.getString("remoteUser"));
        assertEquals("/workspaces/trees/abc123", json.getString("workspaceFolder"));
        assertEquals("echo hello", json.getString("postCreateCommand"));
        assertEquals("true", json.getJsonObject("containerEnv").getString("DEVCONTAINER"));
        assertFalse(json.containsKey("features"));
        assertFalse(json.containsKey("customizations"));
        assertFalse(json.containsKey("mounts"));
        assertFalse(json.containsKey("postStartCommand"));
        assertFalse(json.containsKey("appPort"));
    }

    @Test
    void testTemplateWithFeatures() {
        String featuresJson = """
                {"ghcr.io/devcontainers/features/java:1": {"version": "25"}, "ghcr.io/devcontainers/features/git:1": {}}""";

        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "vscode", "/workspaces/trees/xyz",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo setup", null, null,
                new RawString(featuresJson), null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertTrue(json.containsKey("features"));
        JsonObject features = json.getJsonObject("features");
        assertTrue(features.containsKey("ghcr.io/devcontainers/features/java:1"));
        assertEquals("25", features.getJsonObject("ghcr.io/devcontainers/features/java:1").getString("version"));
        assertTrue(features.containsKey("ghcr.io/devcontainers/features/git:1"));
        assertFalse(json.containsKey("customizations"));
    }

    @Test
    void testTemplateWithVscodeExtensions() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "vscode", "/workspaces/trees/ext",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo setup", null, null, null,
                List.of("vscjava.vscode-java-pack", "ms-python.python"), null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertTrue(json.containsKey("customizations"));
        JsonObject vscode = json.getJsonObject("customizations").getJsonObject("vscode");
        List<String> extensions = vscode.getJsonArray("extensions").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("vscjava.vscode-java-pack", "ms-python.python"), extensions);
    }

    @Test
    void testTemplateWithFeaturesAndExtensions() {
        String featuresJson = """
                {"ghcr.io/devcontainers/features/java:1": {"version": "21"}, "ghcr.io/devcontainers/features/node:1": {"version": "20"}, "ghcr.io/devcontainers/features/common-utils:2": {}}""";

        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "mcr.microsoft.com/devcontainers/base:ubuntu", "vscode",
                "/workspaces/trees/full",
                List.of(
                        new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true"),
                        new DevcontainerWorkspaceManager.EnvVar("MY_VAR", "my_value")),
                List.of("source=vol1,target=/home/vscode/.config,type=volume"),
                "npm install", null, null,
                new RawString(featuresJson),
                List.of("vscjava.vscode-java-pack", "dbaeumer.vscode-eslint"), null
        ).render();

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
    }

    @Test
    void testTemplateWithoutImage() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                null, "vscode", "/workspaces/trees/noimg",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo setup", null, null, null, null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertFalse(json.containsKey("image"));
        assertEquals("vscode", json.getString("remoteUser"));
    }

    @Test
    void testTemplateWithPostStartCommandAndAppPort() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "vscode", "/workspaces/trees/opencode",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo create", "opencode serve --port 3000", List.of(3000),
                null, null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        assertEquals("echo create", json.getString("postCreateCommand"));
        assertEquals("opencode serve --port 3000", json.getString("postStartCommand"));
        assertEquals("postStartCommand", json.getString("waitFor"));
        assertEquals(3000, json.getJsonArray("appPort").getInt(0));
    }

    @Test
    void testTemplateWithFeaturesNoVersionOption() {
        String featuresJson = """
                {"ghcr.io/devcontainers/features/common-utils:2": {}, "ghcr.io/devcontainers/features/git:1": {}}""";

        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "node", "/workspaces/trees/noversion",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo setup", null, null,
                new RawString(featuresJson), null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        JsonObject features = json.getJsonObject("features");
        assertEquals(2, features.size());
        assertTrue(features.getJsonObject("ghcr.io/devcontainers/features/common-utils:2").isEmpty());
        assertTrue(features.getJsonObject("ghcr.io/devcontainers/features/git:1").isEmpty());
    }

    @Test
    void testTemplateMultipleEnvVars() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "node", "/workspaces/trees/multi",
                List.of(
                        new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true"),
                        new DevcontainerWorkspaceManager.EnvVar("FOO", "bar"),
                        new DevcontainerWorkspaceManager.EnvVar("BAZ", "qux")),
                null, "echo setup", null, null, null, null, null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        JsonObject env = json.getJsonObject("containerEnv");
        assertEquals(3, env.size());
        assertEquals("true", env.getString("DEVCONTAINER"));
        assertEquals("bar", env.getString("FOO"));
        assertEquals("qux", env.getString("BAZ"));
    }

    @Test
    void testTemplateSingleVscodeExtension() {
        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "node", "/workspaces/trees/single-ext",
                List.of(new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true")),
                null, "echo setup", null, null, null,
                List.of("ms-python.python"), null
        ).render();

        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();

        List<String> extensions = json.getJsonObject("customizations")
                .getJsonObject("vscode")
                .getJsonArray("extensions").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("ms-python.python"), extensions);
    }

    @Test
    void testRenderedJsonIsValid() {
        String featuresJson = """
                {"ghcr.io/devcontainers/features/java:1": {"version": "25"}}""";

        String rendered = DevcontainerWorkspaceManager.Templates.devcontainer(
                "node:20", "vscode", "/workspaces/trees/valid",
                List.of(
                        new DevcontainerWorkspaceManager.EnvVar("DEVCONTAINER", "true"),
                        new DevcontainerWorkspaceManager.EnvVar("KEY", "value")),
                List.of("source=vol,target=/home/vscode/.data,type=volume"),
                "echo create", "echo start", List.of(8080),
                new RawString(featuresJson),
                List.of("vscjava.vscode-java-pack", "ms-python.python"),
                List.of("ghcr.io/devcontainers/features/node", "ghcr.io/devcontainers/features/java")
        ).render();

        // Should not throw — proves the full template with all fields renders valid JSON
        JsonObject json = Json.createReader(new StringReader(rendered)).readObject();
        assertNotNull(json);
        assertEquals(12, json.size()); // image, remoteUser, workspaceFolder, features, overrideFeatureInstallOrder, customizations, containerEnv, mounts, postCreateCommand, postStartCommand, waitFor, appPort
        List<String> installOrder = json.getJsonArray("overrideFeatureInstallOrder").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
        assertEquals(List.of("ghcr.io/devcontainers/features/node", "ghcr.io/devcontainers/features/java"), installOrder);
    }
}
