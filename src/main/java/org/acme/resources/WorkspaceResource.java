package org.acme.resources;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.BadRequestException;
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
import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.WorkspaceService;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceCommand;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceManager;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/workspaces")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class WorkspaceResource {

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceService workspaceService;

    @Inject
    WorkspaceManager workspaceManager;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @GET
    public List<WorkspaceDto> list(@QueryParam("gitId") Long gitId, @QueryParam("hasTask") Boolean hasTask) {
        StringBuilder jpql = new StringBuilder("SELECT w FROM WorkspaceEntity w WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (gitId != null) {
            jpql.append(" AND w.git.id = :gitId");
            params.put("gitId", gitId);
        }
        if (hasTask != null) {
            if (hasTask) {
                jpql.append(" AND EXISTS (SELECT t FROM TaskEntity t WHERE t.workspace = w)");
            } else {
                jpql.append(" AND NOT EXISTS (SELECT t FROM TaskEntity t WHERE t.workspace = w)");
            }
        }

        return WorkspaceEntity.<WorkspaceEntity>find(jpql.toString(), params).stream()
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
        Workspace workspace = workspaceManager.getWorkspace(entity.workspaceId)
                .orElseThrow(NotFoundException::new);
        return workspace.healthStatus();
    }

    @GET
    @Path("/{id}/commands")
    public List<WorkspaceCommand> commands(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            return List.of();
        }
        return workspaceManager.getWorkspace(entity.workspaceId)
                .map(Workspace::commands)
                .orElse(List.of());
    }

    @POST
    @Path("/{id}/start")
    public Response start(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            throw new BadRequestException("Workspace not provisioned");
        }
        Workspace workspace = workspaceManager.getWorkspace(entity.workspaceId)
                .orElseThrow(NotFoundException::new);
        workspace.start();
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/stop")
    public Response stop(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            throw new BadRequestException("Workspace not provisioned");
        }
        Workspace workspace = workspaceManager.getWorkspace(entity.workspaceId)
                .orElseThrow(NotFoundException::new);
        workspace.stop();
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        workspaceService.delete(entity);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/configuration")
    public Response configuration(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            throw new NotFoundException("Workspace not provisioned");
        }
        return workspaceManager.getConfiguration(entity.workspaceId)
                .map(content -> Response.ok(content, MediaType.APPLICATION_JSON).build())
                .orElseThrow(() -> new NotFoundException("Configuration not found"));
    }

    @GET
    @Path("/{id}/output")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Transactional(TxType.NOT_SUPPORTED)
    public Multi<String> streamProvisionOutput(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
                    WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                            .orElseThrow(NotFoundException::new);
                    return entity.isProvisioningInProgress ? id : null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(wsId ->
                        wsId != null ? broadcaster.subscribe(wsId) : Multi.createFrom().empty());
    }
}
