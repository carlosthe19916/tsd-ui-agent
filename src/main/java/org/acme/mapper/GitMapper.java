package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.GitDto;
import org.acme.models.jpa.entity.GitEntity;

@ApplicationScoped
public class GitMapper {

    public GitDto toDto(GitEntity entity) {
        GitDto dto = new GitDto();
        dto.id = entity.id;
        dto.url = entity.url;
        dto.branch = entity.branch;
        dto.forkUrl = entity.forkUrl;
        return dto;
    }

    public GitEntity toEntity(GitDto dto) {
        GitEntity entity = new GitEntity();
        entity.url = dto.url;
        entity.branch = dto.branch;
        entity.forkUrl = dto.forkUrl;
        return entity;
    }

    public void updateEntity(GitDto dto, GitEntity entity) {
        entity.url = dto.url;
        entity.branch = dto.branch;
        entity.forkUrl = dto.forkUrl;
    }
}
