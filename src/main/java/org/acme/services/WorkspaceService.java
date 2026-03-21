package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.acme.dto.WorkspaceDto;
import org.acme.mapper.WorkspaceMapper;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.workspace.WorkspaceManager;
import org.jboss.logging.Logger;

@ApplicationScoped
@Transactional
public class WorkspaceService {

    private static final Logger LOG = Logger.getLogger(WorkspaceService.class);

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceManager workspaceManager;

    public WorkspaceEntity create(WorkspaceDto dto) {
        WorkspaceEntity entity = workspaceMapper.toEntity(dto);
        if (entity.git == null) {
            throw new NotFoundException("Git not found");
        }

        entity.isCloneInProgress = false;
        entity.persist();

        return entity;
    }

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
