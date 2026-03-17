package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.CredentialEntity;
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

    @Inject
    TransactionManager transactionManager;

    public GitEntity create(GitDto dto) {
        GitEntity entity = gitMapper.toEntity(dto);
        entity.credential = resolveCredential(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, null);

        entity.isCloneInProgress = true;
        entity.persist();

        Long gitId = entity.id;
        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        triggerClone(gitId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register clone callback", e);
        }

        return entity;
    }

    void triggerClone(Long gitId) {
        Thread.startVirtualThread(() -> doClone(gitId));
    }

    void doClone(Long gitId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Read entity fields in a short transaction
            record CloneContext(String url, String branch, String forkUrl) {}

            CloneContext context = QuarkusTransaction.requiringNew().call(() -> {
                GitEntity entity = GitEntity.findById(gitId);
                if (entity == null) {
                    LOG.warnf("Git entity %d not found during clone", gitId);
                    return null;
                }
                return new CloneContext(entity.url, entity.branch, entity.forkUrl);
            });

            if (context == null) {
                return;
            }

            // Phase 2: Long-running clone (no transaction)
            String localPath = gitManager.cloneRepository(context.url(), context.branch());

            if (context.forkUrl() != null && !context.forkUrl().isBlank()) {
                gitManager.addForkRemote(localPath, context.forkUrl());
            }

            // Phase 3: Write result in a short transaction
            QuarkusTransaction.requiringNew().run(() -> {
                GitEntity entity = GitEntity.findById(gitId);
                if (entity == null) {
                    LOG.warnf("Git entity %d not found after clone", gitId);
                    return;
                }
                entity.localPath = localPath;
                entity.isCloneInProgress = false;
                entity.persist();
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to clone git repository %d", gitId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    GitEntity entity = GitEntity.findById(gitId);
                    if (entity == null) {
                        return;
                    }
                    entity.cloneError = e.getMessage();
                    entity.isCloneInProgress = false;
                    entity.persist();
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to record clone error for git %d", gitId);
            }
        } finally {
            requestContext.terminate();
        }
    }

    public GitEntity update(GitDto dto, GitEntity entity) {
        String oldForkUrl = entity.forkUrl;
        gitMapper.updateEntity(dto, entity);
        entity.credential = resolveCredential(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, entity.id);

        gitManager.setRemoteUrl(entity.localPath, entity.url);

        String newForkUrl = entity.forkUrl;
        boolean hadFork = oldForkUrl != null && !oldForkUrl.isBlank();
        boolean hasFork = newForkUrl != null && !newForkUrl.isBlank();

        if (!hadFork && hasFork) {
            gitManager.addForkRemote(entity.localPath, newForkUrl);
        } else if (hadFork && hasFork && !oldForkUrl.equals(newForkUrl)) {
            gitManager.setForkRemoteUrl(entity.localPath, newForkUrl);
        } else if (hadFork && !hasFork) {
            gitManager.removeForkRemote(entity.localPath);
        }

        entity.persist();
        return entity;
    }

    public void delete(GitEntity entity) {
        if (entity.localPath != null) {
            try {
                gitManager.deleteClonedDirectory(entity.localPath);
            } catch (Exception e) {
                LOG.warnf("Failed to delete cloned directory %s: %s", entity.localPath, e.getMessage());
            }
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
