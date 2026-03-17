package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;

@ApplicationScoped
public class GitMapper {

    public GitDto toDto(GitEntity entity) {
        GitDto dto = new GitDto();
        dto.id = entity.id;
        dto.url = entity.url;
        dto.branch = entity.branch;
        dto.forkUrl = entity.forkUrl;
        dto.credential = toCredentialDto(entity.credential);
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

    private CredentialDto toCredentialDto(CredentialEntity entity) {
        if (entity == null) {
            return null;
        }
        CredentialDto dto = new CredentialDto();
        dto.id = entity.id;
        dto.name = entity.name;
        return dto;
    }
}
