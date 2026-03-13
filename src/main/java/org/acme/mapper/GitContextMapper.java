package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.GitContextDto;
import org.acme.models.jpa.entity.GitContextEntity;
import org.acme.models.jpa.entity.ProjectEntity;

@ApplicationScoped
public class GitContextMapper {

    public GitContextDto toDto(GitContextEntity entity) {
        GitContextDto dto = new GitContextDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.description = entity.description;
        dto.type = entity.type;
        dto.content = entity.content;
        dto.repositoryUrl = entity.repositoryUrl;
        dto.branch = entity.branch;
        return dto;
    }

    public GitContextEntity toEntity(GitContextDto dto, ProjectEntity project) {
        GitContextEntity entity = new GitContextEntity();
        entity.name = dto.name;
        entity.description = dto.description;
        entity.type = dto.type;
        entity.content = dto.content;
        entity.repositoryUrl = dto.repositoryUrl;
        entity.branch = dto.branch;
        entity.project = project;
        return entity;
    }

    public void updateEntity(GitContextDto dto, GitContextEntity entity) {
        entity.name = dto.name;
        entity.description = dto.description;
        entity.type = dto.type;
        entity.content = dto.content;
        entity.repositoryUrl = dto.repositoryUrl;
        entity.branch = dto.branch;
    }
}
