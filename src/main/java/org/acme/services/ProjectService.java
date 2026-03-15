package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.ProjectEntity;

@Transactional
@ApplicationScoped
public class ProjectService {

    public ProjectEntity create(ProjectDto dto) {
        CredentialEntity credential = (CredentialEntity) CredentialEntity.findByIdOptional(dto.credential.id)
                .orElseThrow(NotFoundException::new);

        ProjectEntity entity = new ProjectEntity();
        entity.name = dto.name;
        entity.apiUrl = dto.apiUrl;
        entity.query = dto.query;
        entity.type = dto.type;
        entity.credential = credential;
        entity.persist();

        return entity;
    }

    public ProjectEntity update(ProjectEntity entity, ProjectDto dto) {
        entity.name = dto.name;
        entity.apiUrl = dto.apiUrl;
        entity.query = dto.query;
        entity.type = dto.type;
        entity.persist();
        return entity;
    }
}
