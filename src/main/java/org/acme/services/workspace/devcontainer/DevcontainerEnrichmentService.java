package org.acme.services.workspace.devcontainer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import java.io.StringWriter;
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

    public record EnrichmentResult(String featuresJson, List<String> vscodeExtensions,
            List<String> featureInstallOrder) {
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
            Map<String, Integer> featurePriorities = new LinkedHashMap<>();
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
                    featurePriorities.put(entry.id(), entry.installPriority());
                    extensions.addAll(entry.vscodeExtensions());
                }
            }

            // Map detected tools to features
            for (String tool : discovery.tools()) {
                DevcontainerFeatureCatalog.FeatureEntry entry = catalog.findFeatureForTool(tool);
                if (entry != null) {
                    features.putIfAbsent(entry.id(), Map.of());
                    featurePriorities.putIfAbsent(entry.id(), entry.installPriority());
                }
            }

            String featuresJson = buildFeaturesJson(features);
            List<String> extensionsList = new ArrayList<>(extensions);
            List<String> installOrder = buildInstallOrder(featurePriorities);

            outputConsumer.accept("[enrichment] Features: " + features.keySet());
            if (!extensionsList.isEmpty()) {
                outputConsumer.accept("[enrichment] VS Code extensions: " + extensionsList);
            }

            return new EnrichmentResult(featuresJson, extensionsList, installOrder);
        } catch (Exception e) {
            Log.warnf(e, "Enrichment failed for %s, falling back to minimal config", worktreePath);
            outputConsumer.accept("[enrichment] Failed: " + e.getMessage() + ", using minimal config");
            return fallback();
        }
    }

    private EnrichmentResult fallback() {
        return new EnrichmentResult(null, List.of(), List.of());
    }

    private List<String> buildInstallOrder(Map<String, Integer> featurePriorities) {
        if (featurePriorities.size() <= 1) return List.of();
        return featurePriorities.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> e.getKey().replaceFirst(":\\d+$", ""))
                .toList();
    }

    private String buildFeaturesJson(Map<String, Map<String, String>> features) {
        if (features.isEmpty()) return null;
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

}
