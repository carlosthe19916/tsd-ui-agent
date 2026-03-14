package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class GitService {

    private static final Logger LOG = Logger.getLogger(GitService.class);

    @Inject
    GitManager gitManager;

    @Inject
    GitMapper gitMapper;

    public GitEntity create(GitDto dto) {
        GitEntity entity = gitMapper.toEntity(dto);
        entity.persist();

        String localPath = gitManager.cloneRepository(entity.url);
        entity.localPath = localPath;
        entity.persist();

        return entity;
    }

    public GitEntity update(GitDto dto, GitEntity entity) {
        gitMapper.updateEntity(dto, entity);
        gitManager.setRemoteUrl(entity.localPath, entity.url);
        entity.persist();
        return entity;
    }

    public void delete(GitEntity entity) {
        // Delete cloned directory
        if (entity.localPath != null) {
            try {
                gitManager.deleteClonedDirectory(entity.localPath);
            } catch (Exception e) {
                LOG.warnf("Failed to delete cloned directory %s: %s", entity.localPath, e.getMessage());
            }
        }

        entity.delete();
    }
}
