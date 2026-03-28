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
import org.acme.dto.GitChangedFileDto;
import org.acme.dto.WorkspaceDto;
import org.acme.mapper.WorkspaceMapper;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.WorkspaceService;
import org.acme.services.workspace.Workspace;
import org.acme.services.workspace.WorkspaceCommand;
import org.acme.services.workspace.WorkspaceHealthStatus;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.ArrayList;
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
    WorkspaceManagerResolver workspaceManagerResolver;

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
        Workspace workspace = workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
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
        return workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
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
        Workspace workspace = workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
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
        Workspace workspace = workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
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
    @Path("/{id}/git/changed-files")
    public List<GitChangedFileDto> changedFiles(@PathParam("id") Long id) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            return List.of();
        }
        Workspace workspace = workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
                .orElseThrow(NotFoundException::new);

        String output = workspace.exec("git", "status", "--porcelain");
        List<GitChangedFileDto> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.isBlank()) continue;
            GitChangedFileDto dto = new GitChangedFileDto();
            dto.status = line.substring(0, 2).trim();
            dto.path = line.substring(3);
            files.add(dto);
        }
        return files;
    }

    @GET
    @Path("/{id}/git/diff")
    @Produces(MediaType.TEXT_PLAIN)
    public String diff(@PathParam("id") Long id, @QueryParam("path") String filePath) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (entity.workspaceId == null) {
            throw new BadRequestException("Workspace not provisioned");
        }
        Workspace workspace = workspaceManagerResolver.resolve(entity.executionMode)
                .getWorkspace(entity.workspaceId)
                .orElseThrow(NotFoundException::new);

        if (filePath != null && !filePath.isBlank()) {
            String diff = workspace.exec("git", "diff", "HEAD", "--", filePath);
            if (diff.isBlank()) {
                diff = buildNewFileDiff(workspace, filePath);
            }
            return diff;
        }

        String trackedDiff = workspace.exec("git", "diff", "HEAD");
        String untrackedStatus = workspace.exec("git", "status", "--porcelain");
        StringBuilder result = new StringBuilder(trackedDiff);
        for (String line : untrackedStatus.split("\n")) {
            if (line.startsWith("??")) {
                String path = line.substring(3);
                String newFileDiff = buildNewFileDiff(workspace, path);
                if (!newFileDiff.isEmpty()) {
                    if (!result.isEmpty()) result.append("\n");
                    result.append(newFileDiff);
                }
            }
        }
        return result.toString();
    }

    private String buildNewFileDiff(Workspace workspace, String filePath) {
        String content = workspace.exec("cat", filePath);
        if (content.isEmpty()) {
            return "";
        }
        String[] lines = content.split("\n", -1);
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(filePath).append(" b/").append(filePath).append("\n");
        diff.append("new file mode 100644\n");
        diff.append("--- /dev/null\n");
        diff.append("+++ b/").append(filePath).append("\n");
        diff.append("@@ -0,0 +1,").append(lines.length).append(" @@\n");
        for (String line : lines) {
            diff.append("+").append(line).append("\n");
        }
        return diff.toString();
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
                        wsId != null ? broadcaster.subscribe(Channel.WORKSPACE, wsId) : Multi.createFrom().empty());
    }
}
