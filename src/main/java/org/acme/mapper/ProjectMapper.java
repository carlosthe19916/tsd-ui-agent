package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.GitDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;

@ApplicationScoped
public class ProjectMapper {

    public ProjectDto toDto(ProjectEntity entity) {
        ProjectDto dto = new ProjectDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.apiUrl = entity.apiUrl;
        dto.query = entity.query;
        dto.type = entity.type;
        dto.git = toGitDto(entity.git);
        dto.credentialId = entity.credential.id;
        dto.syncStatus = entity.syncStatus;
        dto.lastSyncAt = entity.lastSyncAt;
        return dto;
    }

    private GitDto toGitDto(GitEntity entity) {
        GitDto dto = new GitDto();
        dto.url = entity.url;
        dto.branch = entity.branch;
        return dto;
    }
}
