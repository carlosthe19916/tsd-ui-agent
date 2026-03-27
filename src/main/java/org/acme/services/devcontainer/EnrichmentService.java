package org.acme.services.devcontainer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@ApplicationScoped
public class EnrichmentService {

    @Inject
    DiscoveryService discoveryService;

    @Inject
    FeatureCatalog catalog;

    public record EnrichmentResult(Map<String, Map<String, String>> features, List<String> vscodeExtensions, List<String> featureInstallOrder) {
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

            // Node first (required by coding agent) — must be installed before other features
            FeatureCatalog.LanguageEntry nodeEntry = catalog.findLanguage("node");
            if (nodeEntry != null) {
                features.put(nodeEntry.feature().id(), Map.of());
                if (nodeEntry.vscodeExtensions() != null) {
                    extensions.addAll(nodeEntry.vscodeExtensions());
                }
            }

            // Map detected languages to features
            for (String language : discovery.languages()) {
                FeatureCatalog.LanguageEntry entry = catalog.findLanguage(language);
                if (entry != null) {
                    Map<String, String> options = new LinkedHashMap<>();
                    String version = discovery.versions().get(language);
                    if (version != null) {
                        options.put("version", version);
                    }
                    // put or replace (node may already be present with empty options)
                    features.put(entry.feature().id(), options);
                    if (entry.vscodeExtensions() != null) {
                        extensions.addAll(entry.vscodeExtensions());
                    }
                }
            }

            // Map detected tools to features
            for (String tool : discovery.tools()) {
                FeatureCatalog.ToolEntry entry = catalog.findTool(tool);
                if (entry != null && !features.containsKey(entry.feature().id())) {
                    features.put(entry.feature().id(), Map.of());
                }
            }

            // Always include git and common-utils
            for (String toolName : List.of("git", "common-utils")) {
                FeatureCatalog.ToolEntry entry = catalog.findTool(toolName);
                if (entry != null && !features.containsKey(entry.feature().id())) {
                    features.put(entry.feature().id(), Map.of());
                }
            }

            List<String> extensionsList = new ArrayList<>(extensions);

            outputConsumer.accept("[enrichment] Features: " + features.keySet());
            if (!extensionsList.isEmpty()) {
                outputConsumer.accept("[enrichment] VS Code extensions: " + extensionsList);
            }

            List<String> featureInstallOrder = features.isEmpty() ? null : new ArrayList<>(features.keySet());
            return new EnrichmentResult(features.isEmpty() ? null : features, extensionsList, featureInstallOrder);
        } catch (Exception e) {
            Log.warnf(e, "Enrichment failed for %s, falling back to minimal config", worktreePath);
            outputConsumer.accept("[enrichment] Failed: " + e.getMessage() + ", using minimal config");
            return fallback();
        }
    }

    private EnrichmentResult fallback() {
        return new EnrichmentResult(null, List.of(), null);
    }

}
