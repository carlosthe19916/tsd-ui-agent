package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.ProjectGitMappingEntity;
import org.acme.models.jpa.entity.TaskEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProjectGitMappingService {

    public Optional<GitEntity> resolveFromGitHub(ProjectEntity project) {
        String apiUrl = project.apiUrl;
        String prefix = "https://api.github.com/repos/";
        if (apiUrl != null && apiUrl.startsWith(prefix)) {
            String ownerRepo = apiUrl.substring(prefix.length());
            GitEntity git = GitEntity.find("url like ?1", "%" + ownerRepo + "%").firstResult();
            return Optional.ofNullable(git);
        }
        return Optional.empty();
    }

    public Optional<GitEntity> resolveFromMapping(TaskEntity task) {
        String prefix = extractPrefix(task.externalId);

        List<ProjectGitMappingEntity> mappings = ProjectGitMappingEntity
                .list("project = ?1 and space = ?2", task.project, prefix);

        Set<String> taskLabels = parseLabels(task.labels);

        return mappings.stream()
                .filter(m -> matchesLabels(m, taskLabels))
                .map(m -> m.git)
                .findFirst();
    }

    private String extractPrefix(String externalId) {
        if (externalId == null) return "";
        int idx = externalId.indexOf('-');
        return idx > 0 ? externalId.substring(0, idx) : externalId;
    }

    private Set<String> parseLabels(String labels) {
        if (labels == null || labels.isBlank()) return Collections.emptySet();
        return Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean matchesLabels(ProjectGitMappingEntity mapping, Set<String> taskLabels) {
        if (mapping.labels == null || mapping.labels.isBlank()) return true;
        Set<String> required = parseLabels(mapping.labels);
        return taskLabels.containsAll(required);
    }
}
