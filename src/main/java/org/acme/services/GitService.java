package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;

import java.util.Map;

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
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, null);

        entity.persist();

        String localPath = gitManager.cloneRepository(entity.url, entity.branch);
        entity.localPath = localPath;
        entity.persist();

        return entity;
    }

    public GitEntity update(GitDto dto, GitEntity entity) {
        gitMapper.updateEntity(dto, entity);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, entity.id);

        gitManager.setRemoteUrl(entity.localPath, entity.url);
        entity.persist();
        return entity;
    }

    private void normalizeBranch(GitEntity entity) {
        if (entity.branch == null || entity.branch.isBlank()) {
            entity.branch = "";
        }
    }

    private void checkDuplicate(String url, String branch, Long excludeId) {
        GitEntity existing = GitEntity.find("url = ?1 and branch = ?2", url, branch).firstResult();
        if (existing != null && !existing.id.equals(excludeId)) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "A git repository with this URL and branch already exists"))
                            .build()
            );
        }
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
