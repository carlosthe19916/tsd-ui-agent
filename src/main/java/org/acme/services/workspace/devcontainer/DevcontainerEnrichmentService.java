package org.acme.services.workspace.devcontainer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonArrayBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@ApplicationScoped
public class DevcontainerEnrichmentService {

    @Inject
    DevcontainerDiscoveryService discoveryService;

    @Inject
    DevcontainerFeatureCatalog catalog;

    public record EnrichmentResult(String featuresJson, List<String> vscodeExtensions) {
    }

    public EnrichmentResult enrich(Path worktreePath, Consumer<String> outputConsumer) {
        try {
            outputConsumer.accept("[enrichment] Scanning repository for languages and tools...");
            DiscoveryResult discovery = discoveryService.discover(worktreePath);

            outputConsumer.accept("[enrichment] Detected languages: " + discovery.languages());
            outputConsumer.accept("[enrichment] Detected tools: " + discovery.tools());
            if (!discovery.versions().isEmpty()) {
                outputConsumer.accept("[enrichment] Inferred versions: " + discovery.versions());
            }

            Map<String, Map<String, String>> features = new LinkedHashMap<>();
            Set<String> extensions = new LinkedHashSet<>();

            // Map detected languages to features
            for (String language : discovery.languages()) {
                DevcontainerFeatureCatalog.FeatureEntry entry = catalog.findFeatureForLanguage(language);
                if (entry != null) {
                    Map<String, String> options = new LinkedHashMap<>();
                    String version = discovery.versions().get(language);
                    if (version != null && entry.versionOption() != null) {
                        options.put(entry.versionOption(), version);
                    }
                    features.put(entry.id(), options);
                    extensions.addAll(entry.vscodeExtensions());
                }
            }

            // Map detected tools to features
            for (String tool : discovery.tools()) {
                DevcontainerFeatureCatalog.FeatureEntry entry = catalog.findFeatureForTool(tool);
                if (entry != null) {
                    features.putIfAbsent(entry.id(), Map.of());
                }
            }

            // Always include git and common-utils
            DevcontainerFeatureCatalog.FeatureEntry git = catalog.findFeatureForTool("git");
            if (git != null) features.putIfAbsent(git.id(), Map.of());
            DevcontainerFeatureCatalog.FeatureEntry commonUtils = catalog.findFeatureForTool("common-utils");
            if (commonUtils != null) features.putIfAbsent(commonUtils.id(), Map.of());

            String featuresJson = buildFeaturesJson(features);
            List<String> extensionsList = new ArrayList<>(extensions);

            outputConsumer.accept("[enrichment] Features: " + features.keySet());
            if (!extensionsList.isEmpty()) {
                outputConsumer.accept("[enrichment] VS Code extensions: " + extensionsList);
            }

            // Write sidecar metadata
            writeSidecarMetadata(worktreePath, discovery, features, extensionsList);

            return new EnrichmentResult(featuresJson, extensionsList);
        } catch (Exception e) {
            Log.warnf(e, "Enrichment failed for %s, falling back to minimal config", worktreePath);
            outputConsumer.accept("[enrichment] Failed: " + e.getMessage() + ", using minimal config");
            return fallback();
        }
    }

    private EnrichmentResult fallback() {
        DevcontainerFeatureCatalog.FeatureEntry git = catalog.findFeatureForTool("git");
        String gitId = git != null ? git.id() : "ghcr.io/devcontainers/features/git:1";
        return new EnrichmentResult(
                "{ \"" + gitId + "\": {} }",
                List.of());
    }

    private String buildFeaturesJson(Map<String, Map<String, String>> features) {
        JsonObjectBuilder root = Json.createObjectBuilder();
        for (Map.Entry<String, Map<String, String>> entry : features.entrySet()) {
            JsonObjectBuilder options = Json.createObjectBuilder();
            for (Map.Entry<String, String> opt : entry.getValue().entrySet()) {
                options.add(opt.getKey(), opt.getValue());
            }
            root.add(entry.getKey(), options);
        }
        StringWriter sw = new StringWriter();
        Json.createWriter(sw).writeObject(root.build());
        return sw.toString();
    }

    private void writeSidecarMetadata(Path worktreePath, DiscoveryResult discovery,
            Map<String, Map<String, String>> features, List<String> extensions) {
        try {
            JsonObjectBuilder meta = Json.createObjectBuilder();
            JsonArrayBuilder langs = Json.createArrayBuilder();
            discovery.languages().forEach(langs::add);
            meta.add("languages", langs);

            JsonArrayBuilder toolsArr = Json.createArrayBuilder();
            discovery.tools().forEach(toolsArr::add);
            meta.add("tools", toolsArr);

            JsonObjectBuilder versionsObj = Json.createObjectBuilder();
            discovery.versions().forEach(versionsObj::add);
            meta.add("versions", versionsObj);

            JsonArrayBuilder featuresArr = Json.createArrayBuilder();
            features.keySet().forEach(featuresArr::add);
            meta.add("features", featuresArr);

            JsonArrayBuilder extsArr = Json.createArrayBuilder();
            extensions.forEach(extsArr::add);
            meta.add("vscodeExtensions", extsArr);

            StringWriter sw = new StringWriter();
            Json.createWriter(sw).writeObject(meta.build());

            Path sidecar = worktreePath.resolve("enrichment-metadata.json");
            Files.writeString(sidecar, sw.toString());
            Log.debugf("Wrote enrichment metadata to %s", sidecar);
        } catch (IOException e) {
            Log.warnf("Failed to write enrichment metadata: %s", e.getMessage());
        }
    }
}
