package org.acme.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.CredentialDto;
import org.acme.models.jpa.entity.CredentialEntity;

@ApplicationScoped
public class CredentialMapper {

    public CredentialDto toDto(CredentialEntity entity) {
        CredentialDto dto = new CredentialDto();
        dto.id = entity.id;
        dto.name = entity.name;
        return dto;
    }

    public CredentialEntity toEntity(CredentialDto dto) {
        CredentialEntity entity = new CredentialEntity();
        entity.name = dto.name;
        entity.token = dto.token;
        return entity;
    }

    public void updateEntity(CredentialDto dto, CredentialEntity entity) {
        entity.name = dto.name;
        if (dto.token != null) {
            entity.token = dto.token;
        }
    }
}
