package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
public class GitService {

    private static final Logger LOG = Logger.getLogger(GitService.class);

    @Inject
    GitManager gitManager;

    @Inject
    GitMapper gitMapper;

    @Inject
    WorkspaceService workspaceService;

    public GitEntity create(GitDto dto) {
        GitEntity entity = gitMapper.toEntity(dto);
        entity.credential = resolveCredential(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, null);

        entity.persist();

        return entity;
    }

    public GitEntity update(GitDto dto, GitEntity entity) {
        String oldForkUrl = entity.forkUrl;
        gitMapper.updateEntity(dto, entity);
        entity.credential = resolveCredential(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, entity.id);

        List<WorkspaceEntity> workspaces = WorkspaceEntity.list("git", entity);
        for (WorkspaceEntity ws : workspaces) {
            if (ws.localPath != null) {
                gitManager.setRemoteUrl(ws.localPath, entity.url);

                String newForkUrl = entity.forkUrl;
                boolean hadFork = oldForkUrl != null && !oldForkUrl.isBlank();
                boolean hasFork = newForkUrl != null && !newForkUrl.isBlank();

                if (!hadFork && hasFork) {
                    gitManager.addForkRemote(ws.localPath, newForkUrl);
                } else if (hadFork && hasFork && !oldForkUrl.equals(newForkUrl)) {
                    gitManager.setForkRemoteUrl(ws.localPath, newForkUrl);
                } else if (hadFork && !hasFork) {
                    gitManager.removeForkRemote(ws.localPath);
                }
            }
        }

        entity.persist();
        return entity;
    }

    public void delete(GitEntity entity) {
        List<WorkspaceEntity> workspaces = WorkspaceEntity.list("git", entity);
        for (WorkspaceEntity ws : workspaces) {
            workspaceService.delete(ws);
        }

        entity.delete();
    }

    private CredentialEntity resolveCredential(GitDto dto) {
        if (dto.credential == null || dto.credential.id == null) {
            return null;
        }
        return (CredentialEntity) CredentialEntity.findByIdOptional(dto.credential.id)
                .orElseThrow(() -> new NotFoundException("Credential not found"));
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
}
