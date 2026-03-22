package org.acme.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.WorkspaceDto;
import org.acme.mapper.WorkspaceMapper;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.WorkspaceService;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceManager;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/workspaces")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class WorkspaceResource {

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceService workspaceService;

    @Inject
    WorkspaceManager workspaceManager;

    @GET
    public List<WorkspaceDto> list(@QueryParam("gitId") Long gitId) {
        if (gitId != null) {
            return WorkspaceEntity.<WorkspaceEntity>list("git.id", gitId).stream()
                    .map(workspaceMapper::toDto)
                    .collect(Collectors.toList());
        }
        return WorkspaceEntity.<WorkspaceEntity>findAll().stream()
                .map(workspaceMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public WorkspaceDto get(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return workspaceMapper.toDto(entity);
    }

    @POST
    public Response create(WorkspaceDto dto) {
        if (dto.git == null || dto.git.id == null) {
            throw new BadRequestException("git.id is required");
        }

        WorkspaceEntity entity = workspaceService.create(dto);

        if (dto.task != null && dto.task.id != null) {
            TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(dto.task.id)
                    .orElse(null);
            if (task != null) {
                task.workspace = entity;
            }
        }

        return Response.status(Response.Status.CREATED)
                .entity(workspaceMapper.toDto(entity))
                .build();
    }

    @GET
    @Path("/{id}/status")
    public WorkspaceHealthStatus status(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            return WorkspaceHealthStatus.stopped("not provisioned");
        }
        return workspaceManager.healthStatus(entity.workspaceId);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        workspaceService.delete(entity);
        return Response.noContent().build();
    }
}
