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
import org.acme.dto.WorkspaceDto;
import org.acme.mapper.WorkspaceMapper;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.WorkspaceManager;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class WorkspaceService {

    private static final Logger LOG = Logger.getLogger(WorkspaceService.class);

    @Inject
    GitManager gitManager;

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceManager workspaceManager;

    @Inject
    TransactionManager transactionManager;

    public WorkspaceEntity create(WorkspaceDto dto) {
        WorkspaceEntity entity = workspaceMapper.toEntity(dto);
        if (entity.git == null) {
            throw new NotFoundException("Git not found");
        }

        entity.isCloneInProgress = true;
        entity.persist();

        Long workspaceEntityId = entity.id;
        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        triggerClone(workspaceEntityId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register clone callback", e);
        }

        return entity;
    }

    void triggerClone(Long workspaceEntityId) {
        Thread.startVirtualThread(() -> doClone(workspaceEntityId));
    }

    void doClone(Long workspaceEntityId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            record CloneContext(String url, String branch, String forkUrl) {}

            CloneContext context = QuarkusTransaction.requiringNew().call(() -> {
                WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                if (entity == null || entity.git == null) {
                    LOG.warnf("Workspace entity %d or its git not found during clone", workspaceEntityId);
                    return null;
                }
                return new CloneContext(entity.git.url, entity.git.branch, entity.git.forkUrl);
            });

            if (context == null) {
                return;
            }

            String localPath = gitManager.cloneRepository(context.url(), context.branch());

            if (context.forkUrl() != null && !context.forkUrl().isBlank()) {
                gitManager.addForkRemote(localPath, context.forkUrl());
            }

            QuarkusTransaction.requiringNew().run(() -> {
                WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                if (entity == null) {
                    LOG.warnf("Workspace entity %d not found after clone", workspaceEntityId);
                    return;
                }
                entity.localPath = localPath;
                entity.isCloneInProgress = false;
                entity.persist();
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to clone git repository for workspace %d", workspaceEntityId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                    if (entity == null) {
                        return;
                    }
                    entity.cloneError = e.getMessage();
                    entity.isCloneInProgress = false;
                    entity.persist();
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to record clone error for workspace %d", workspaceEntityId);
            }
        } finally {
            requestContext.terminate();
        }
    }

    public void delete(WorkspaceEntity entity) {
        if (entity.localPath != null) {
            try {
                gitManager.deleteClonedDirectory(entity.localPath);
            } catch (Exception e) {
                LOG.warnf("Failed to delete cloned directory %s: %s", entity.localPath, e.getMessage());
            }
        }

        if (entity.workspaceId != null) {
            try {
                workspaceManager.destroy(entity.workspaceId);
            } catch (Exception e) {
                LOG.warnf("Failed to destroy runtime workspace %s: %s", entity.workspaceId, e.getMessage());
            }
        }

        entity.delete();
    }
}
