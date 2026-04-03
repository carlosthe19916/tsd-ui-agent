package org.acme.services.github.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class LabelConfig {

    private final List<LabelDefinition> labels;
    private final Map<String, LabelDefinition> labelsByName;

    LabelConfig() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("github-labels.yaml")) {
            if (is == null) {
                throw new IllegalStateException("github-labels.yaml not found on classpath");
            }
            var mapper = new ObjectMapper(new YAMLFactory());
            var config = mapper.readValue(is, LabelConfigFile.class);
            this.labels = Collections.unmodifiableList(config.labels);
            this.labelsByName = labels.stream()
                    .collect(Collectors.toMap(l -> l.name.toLowerCase(), l -> l));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load github-labels.yaml", e);
        }
    }

    public List<LabelDefinition> getLabels() {
        return labels;
    }

    public LabelDefinition findByName(String name) {
        return labelsByName.get(name.toLowerCase());
    }

    static class LabelConfigFile {
        public List<LabelDefinition> labels;
    }
}
