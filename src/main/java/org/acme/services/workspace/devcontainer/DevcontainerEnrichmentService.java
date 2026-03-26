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

    public record EnrichmentResult(String featuresJson, List<String> vscodeExtensions, List<String> featureInstallOrder) {
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

            record FeatureWithOrder(String id, Map<String, String> options, int installOrder) {}
            List<FeatureWithOrder> collectedFeatures = new ArrayList<>();
            Set<String> extensions = new LinkedHashSet<>();

            // Map detected languages to features
            for (String language : discovery.languages()) {
                DevcontainerFeatureCatalog.LanguageEntry entry = catalog.findLanguage(language);
                if (entry != null) {
                    Map<String, String> options = new LinkedHashMap<>();
                    String version = discovery.versions().get(language);
                    if (version != null && entry.feature().versionOption() != null) {
                        options.put(entry.feature().versionOption(), version);
                    }
                    collectedFeatures.add(new FeatureWithOrder(entry.feature().id(), options, entry.feature().installOrder()));
                    if (entry.vscodeExtensions() != null) {
                        extensions.addAll(entry.vscodeExtensions());
                    }
                }
            }

            // Map detected tools to features
            for (String tool : discovery.tools()) {
                DevcontainerFeatureCatalog.ToolEntry entry = catalog.findTool(tool);
                if (entry != null && collectedFeatures.stream().noneMatch(f -> f.id().equals(entry.feature().id()))) {
                    collectedFeatures.add(new FeatureWithOrder(entry.feature().id(), Map.of(), entry.feature().installOrder()));
                }
            }

            // Always include node (required by coding agent), git and common-utils
            DevcontainerFeatureCatalog.LanguageEntry nodeEntry = catalog.findLanguage("node");
            if (nodeEntry != null && collectedFeatures.stream().noneMatch(f -> f.id().equals(nodeEntry.feature().id()))) {
                collectedFeatures.add(new FeatureWithOrder(nodeEntry.feature().id(), Map.of(), nodeEntry.feature().installOrder()));
                if (nodeEntry.vscodeExtensions() != null) {
                    extensions.addAll(nodeEntry.vscodeExtensions());
                }
            }

            for (String toolName : List.of("git", "common-utils")) {
                DevcontainerFeatureCatalog.ToolEntry entry = catalog.findTool(toolName);
                if (entry != null && collectedFeatures.stream().noneMatch(f -> f.id().equals(entry.feature().id()))) {
                    collectedFeatures.add(new FeatureWithOrder(entry.feature().id(), Map.of(), entry.feature().installOrder()));
                }
            }

            // Sort by installOrder — apt-dependent features (node, python) first, then others, then tools
            collectedFeatures.sort(java.util.Comparator.comparingInt(FeatureWithOrder::installOrder));

            // Build ordered features map
            Map<String, Map<String, String>> features = new LinkedHashMap<>();
            for (FeatureWithOrder f : collectedFeatures) {
                features.put(f.id(), f.options());
            }

            String featuresJson = buildFeaturesJson(features);
            List<String> extensionsList = new ArrayList<>(extensions);

            outputConsumer.accept("[enrichment] Features: " + features.keySet());
            if (!extensionsList.isEmpty()) {
                outputConsumer.accept("[enrichment] VS Code extensions: " + extensionsList);
            }

            List<String> featureInstallOrder = features.isEmpty() ? null : new ArrayList<>(features.keySet());
            return new EnrichmentResult(featuresJson, extensionsList, featureInstallOrder);
        } catch (Exception e) {
            Log.warnf(e, "Enrichment failed for %s, falling back to minimal config", worktreePath);
            outputConsumer.accept("[enrichment] Failed: " + e.getMessage() + ", using minimal config");
            return fallback();
        }
    }

    private EnrichmentResult fallback() {
        return new EnrichmentResult(null, List.of(), null);
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
