package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;

@Transactional
@ApplicationScoped
public class ProjectService {

    public ProjectEntity create(ProjectDto dto) {
        GitEntity git = new GitEntity();
        git.name = dto.git.name;
        git.url = dto.git.url;
        git.platform = dto.git.platform;
        git.persist();

        CredentialEntity credential = new CredentialEntity();
        credential.name = dto.credential.name;
        credential.type = dto.credential.type;
        credential.token = dto.credential.token;
        credential.username = dto.credential.username;
        credential.persist();

        ProjectEntity entity = new ProjectEntity();
        entity.name = dto.name;
        entity.description = dto.description;
        entity.url = dto.url;
        entity.query = dto.query;
        entity.type = dto.type;
        entity.git = git;
        entity.credential = credential;
        entity.persist();

        return entity;
    }

    public ProjectEntity update(ProjectEntity entity, ProjectDto dto) {
        entity.name = dto.name;
        entity.description = dto.description;
        entity.url = dto.url;
        entity.query = dto.query;
        entity.type = dto.type;
        entity.persist();
        return entity;
    }
}
