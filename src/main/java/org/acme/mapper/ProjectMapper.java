package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;

@ApplicationScoped
public class ProjectMapper {

    public ProjectDto toDto(ProjectEntity entity) {
        ProjectDto dto = new ProjectDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.description = entity.description;
        dto.url = entity.url;
        dto.query = entity.query;
        dto.type = entity.type;
        dto.git = toGitDto(entity.git);
        dto.credential = toCredentialDto(entity.credential);
        return dto;
    }

    private GitDto toGitDto(GitEntity entity) {
        GitDto dto = new GitDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.url = entity.url;
        dto.platform = entity.platform;
        return dto;
    }

    private CredentialDto toCredentialDto(CredentialEntity entity) {
        CredentialDto dto = new CredentialDto();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.type = entity.type;
        dto.username = entity.username;
        return dto;
    }
}
