package org.acme.services.workspace.devcontainer;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DevcontainerFeatureCatalog {

    private JsonObject catalog;

    @PostConstruct
    void init() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("devcontainer-features-catalog.json")) {
            if (is == null) {
                throw new IllegalStateException("devcontainer-features-catalog.json not found on classpath");
            }
            catalog = Json.createReader(is).readObject();
            Log.infof("Loaded devcontainer feature catalog: %d languages, %d tools",
                    catalog.getJsonObject("languages").size(),
                    catalog.getJsonObject("tools").size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load devcontainer feature catalog", e);
        }
    }

    public record FeatureEntry(String id, String versionOption, int installPriority, List<String> vscodeExtensions) {
    }

    public FeatureEntry findFeatureForLanguage(String language) {
        JsonObject languages = catalog.getJsonObject("languages");
        JsonObject lang = languages.getJsonObject(language.toLowerCase());
        if (lang == null) {
            return null;
        }
        JsonObject feature = lang.getJsonObject("feature");
        String id = feature.getString("id");
        String versionOption = feature.containsKey("versionOption") ? feature.getString("versionOption") : null;
        int installPriority = feature.containsKey("installPriority") ? feature.getInt("installPriority") : 1;
        List<String> extensions = lang.containsKey("vscodeExtensions")
                ? lang.getJsonArray("vscodeExtensions").stream()
                        .map(v -> ((JsonString) v).getString())
                        .toList()
                : List.of();
        return new FeatureEntry(id, versionOption, installPriority, extensions);
    }

    public FeatureEntry findFeatureForTool(String tool) {
        JsonObject tools = catalog.getJsonObject("tools");
        JsonObject toolObj = tools.getJsonObject(tool.toLowerCase());
        if (toolObj == null) {
            return null;
        }
        JsonObject feature = toolObj.getJsonObject("feature");
        int installPriority = feature.containsKey("installPriority") ? feature.getInt("installPriority") : 1;
        return new FeatureEntry(feature.getString("id"), null, installPriority, List.of());
    }

    public String languageForExtension(String extension) {
        JsonObject languages = catalog.getJsonObject("languages");
        for (Map.Entry<String, JsonValue> entry : languages.entrySet()) {
            JsonObject lang = entry.getValue().asJsonObject();
            List<String> extensions = lang.getJsonArray("extensions").stream()
                    .map(v -> ((JsonString) v).getString())
                    .toList();
            if (extensions.contains(extension.toLowerCase())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Map<String, List<String>> getToolIndicators() {
        JsonObject tools = catalog.getJsonObject("tools");
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : tools.entrySet()) {
            JsonObject tool = entry.getValue().asJsonObject();
            if (tool.containsKey("indicators")) {
                List<String> indicators = tool.getJsonArray("indicators").stream()
                        .map(v -> ((JsonString) v).getString())
                        .toList();
                result.put(entry.getKey(), indicators);
            }
        }
        return result;
    }

    public Map<String, List<String>> getAllLanguageManifests() {
        JsonObject languages = catalog.getJsonObject("languages");
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : languages.entrySet()) {
            JsonObject lang = entry.getValue().asJsonObject();
            if (lang.containsKey("manifests")) {
                List<String> manifests = lang.getJsonArray("manifests").stream()
                        .map(v -> ((JsonString) v).getString())
                        .toList();
                result.put(entry.getKey(), manifests);
            }
        }
        return result;
    }

}
