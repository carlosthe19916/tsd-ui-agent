package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.ProjectGitMappingDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.ProjectGitMappingEntity;

import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class ProjectGitMappingMapper {

    @Inject
    GitMapper gitMapper;

    public ProjectGitMappingDto toDto(ProjectGitMappingEntity entity) {
        ProjectGitMappingDto dto = new ProjectGitMappingDto();
        dto.id = entity.id;
        dto.projectId = entity.project.id;
        dto.gitId = entity.git.id;
        dto.space = entity.space;
        dto.labels = entity.labels != null ? Arrays.asList(entity.labels.split(",")) : List.of();
        return dto;
    }

    public ProjectGitMappingEntity toEntity(ProjectGitMappingDto dto, ProjectEntity project) {
        ProjectGitMappingEntity entity = new ProjectGitMappingEntity();
        entity.project = project;
        entity.git = GitEntity.findById(dto.gitId);
        entity.space = dto.space;
        entity.labels = dto.labels != null && !dto.labels.isEmpty() ? String.join(",", dto.labels) : null;
        return entity;
    }
}
