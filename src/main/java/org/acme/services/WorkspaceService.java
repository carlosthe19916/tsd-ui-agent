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
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceRequest;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WorkspaceService {

    private static final Logger LOG = Logger.getLogger(WorkspaceService.class);

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceManager workspaceManager;

    @Inject
    TransactionManager transactionManager;

    @Transactional
    public WorkspaceEntity create(WorkspaceDto dto) {
        WorkspaceEntity entity = workspaceMapper.toEntity(dto);
        if (entity.git == null) {
            throw new NotFoundException("Git not found");
        }

        entity.isProvisioningInProgress = true;
        entity.persist();

        Long workspaceEntityId = entity.id;
        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        Thread.startVirtualThread(() -> doProvision(workspaceEntityId));
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register provisioning callback", e);
        }

        return entity;
    }

    void doProvision(Long workspaceEntityId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            record ProvisionContext(String gitUrl, String gitBranch, String gitToken, String forkUrl) {}

            ProvisionContext context = QuarkusTransaction.requiringNew().call(() -> {
                WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                if (entity == null || entity.git == null) {
                    LOG.warnf("Workspace entity %d or its git not found during provisioning", workspaceEntityId);
                    return null;
                }
                return new ProvisionContext(
                        entity.git.url,
                        entity.git.branch,
                        entity.git.credential != null ? entity.git.credential.token : null,
                        entity.git.forkUrl
                );
            });

            if (context == null) {
                return;
            }

            WorkspaceRequest request = new WorkspaceRequest(context.gitUrl(), context.gitBranch(), context.gitToken(), context.forkUrl());
            Workspace ws = workspaceManager.provision(request);

            QuarkusTransaction.requiringNew().run(() -> {
                WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                if (entity != null) {
                    entity.workspaceId = ws.id();
                    entity.isProvisioningInProgress = false;
                    entity.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to provision workspace %d", workspaceEntityId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    WorkspaceEntity entity = WorkspaceEntity.findById(workspaceEntityId);
                    if (entity != null) {
                        entity.provisioningError = e.getMessage();
                        entity.isProvisioningInProgress = false;
                        entity.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to record provisioning error for workspace %d", workspaceEntityId);
            }
        } finally {
            requestContext.terminate();
        }
    }

    @Transactional
    public void delete(WorkspaceEntity entity) {
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
