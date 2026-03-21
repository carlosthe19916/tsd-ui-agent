package org.acme.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.mapper.GitMapper;
import org.acme.mapper.WorkspaceMapper;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.GitService;
import org.acme.services.WorkspaceService;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/gits")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class GitResource {

    @Inject
    GitMapper gitMapper;

    @Inject
    GitService gitService;

    @Inject
    WorkspaceMapper workspaceMapper;

    @Inject
    WorkspaceService workspaceService;

    @GET
    public List<GitDto> list() {
        return GitEntity.<GitEntity>findAll().stream()
                .map(gitMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public GitDto get(@PathParam("id") Long id) {
        GitEntity entity = (GitEntity) GitEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return gitMapper.toDto(entity);
    }

    @POST
    public Response create(@Valid GitDto dto) {
        GitEntity entity = gitService.create(dto);
        return Response.status(Response.Status.CREATED)
                .entity(gitMapper.toDto(entity))
                .build();
    }

    @PUT
    @Path("/{id}")
    public GitDto update(@PathParam("id") Long id, @Valid GitDto dto) {
        GitEntity entity = (GitEntity) GitEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        GitEntity updated = gitService.update(dto, entity);
        return gitMapper.toDto(updated);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        GitEntity entity = (GitEntity) GitEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        gitService.delete(entity);
        return Response.noContent().build();
    }

    // Workspace sub-resource endpoints

    @GET
    @Path("/{gitId}/workspaces")
    public List<WorkspaceDto> listWorkspaces(@PathParam("gitId") Long gitId) {
        return WorkspaceEntity.<WorkspaceEntity>list("git.id", gitId).stream()
                .map(workspaceMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{gitId}/workspaces/{wsId}")
    public WorkspaceDto getWorkspace(@PathParam("gitId") Long gitId, @PathParam("wsId") Long wsId) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(wsId)
                .orElseThrow(NotFoundException::new);
        return workspaceMapper.toDto(entity);
    }

    @POST
    @Path("/{gitId}/workspaces")
    public Response createWorkspace(@PathParam("gitId") Long gitId, @Valid WorkspaceDto dto) {
        GitDto git = new GitDto();
        git.id = gitId;
        dto.git = git;
        WorkspaceEntity entity = workspaceService.create(dto);
        return Response.status(Response.Status.CREATED)
                .entity(workspaceMapper.toDto(entity))
                .build();
    }

    @PATCH
    @Path("/{gitId}/workspaces/{wsId}")
    public WorkspaceDto patchWorkspace(@PathParam("gitId") Long gitId, @PathParam("wsId") Long wsId,
            WorkspaceDto dto) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(wsId)
                .orElseThrow(NotFoundException::new);
        workspaceMapper.patchEntity(dto, entity);
        return workspaceMapper.toDto(entity);
    }

    @DELETE
    @Path("/{gitId}/workspaces/{wsId}")
    public Response deleteWorkspace(@PathParam("gitId") Long gitId, @PathParam("wsId") Long wsId) {
        WorkspaceEntity entity = (WorkspaceEntity) WorkspaceEntity.findByIdOptional(wsId)
                .orElseThrow(NotFoundException::new);
        workspaceService.delete(entity);
        return Response.noContent().build();
    }
}
