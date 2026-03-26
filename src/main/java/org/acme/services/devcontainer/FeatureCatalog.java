package org.acme.services.devcontainer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FeatureCatalog {

    public record Catalog(
            Map<String, LanguageEntry> languages,
            Map<String, ToolEntry> tools) {
    }

    public record LanguageEntry(
            List<String> extensions,
            List<String> manifests,
            FeatureRef feature,
            List<String> vscodeExtensions) {
    }

    public record ToolEntry(
            List<String> indicators,
            FeatureRef feature) {
    }

    public record FeatureRef(
            String id,
            @JsonProperty("versionOption") String versionOption,
            @JsonProperty("installOrder") int installOrder) {
    }

    private Catalog catalog;

    @PostConstruct
    void init() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("devcontainer-features-catalog.json")) {
            if (is == null) {
                throw new IllegalStateException("devcontainer-features-catalog.json not found on classpath");
            }
            catalog = new ObjectMapper().readValue(is, Catalog.class);
            Log.infof("Loaded devcontainer feature catalog: %d languages, %d tools", catalog.languages().size(), catalog.tools().size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load devcontainer feature catalog", e);
        }
    }

    public LanguageEntry findLanguage(String language) {
        return catalog.languages().get(language.toLowerCase());
    }

    public ToolEntry findTool(String tool) {
        return catalog.tools().get(tool.toLowerCase());
    }

    public String languageForExtension(String extension) {
        String ext = extension.toLowerCase();
        for (Map.Entry<String, LanguageEntry> entry : catalog.languages().entrySet()) {
            if (entry.getValue().extensions() != null && entry.getValue().extensions().contains(ext)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Map<String, List<String>> getToolIndicators() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ToolEntry> entry : catalog.tools().entrySet()) {
            if (entry.getValue().indicators() != null && !entry.getValue().indicators().isEmpty()) {
                result.put(entry.getKey(), entry.getValue().indicators());
            }
        }
        return result;
    }

    public Map<String, List<String>> getAllLanguageManifests() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LanguageEntry> entry : catalog.languages().entrySet()) {
            if (entry.getValue().manifests() != null && !entry.getValue().manifests().isEmpty()) {
                result.put(entry.getKey(), entry.getValue().manifests());
            }
        }
        return result;
    }
}
