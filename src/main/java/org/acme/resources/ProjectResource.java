package org.acme.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitContextDto;
import org.acme.dto.GitDto;
import org.acme.dto.ProjectDto;
import org.acme.mapper.GitContextMapper;
import org.acme.mapper.ProjectMapper;
import org.acme.models.jpa.entity.GitContextEntity;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.services.ProjectService;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    ProjectMapper projectMapper;

    @Inject
    GitContextMapper gitContextMapper;

    @Inject
    ProjectService projectService;

    @GET
    public List<ProjectDto> list() {
        return ProjectEntity.<ProjectEntity>findAll().stream()
                .map(projectMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public ProjectDto get(@PathParam("id") Long id) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return projectMapper.toDto(entity);
    }

    @POST
    public Response create(@Valid ProjectDto dto) {
        ProjectEntity entity = projectService.create(dto);
        return Response.status(Response.Status.CREATED)
                .entity(projectMapper.toDto(entity))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ProjectDto update(@PathParam("id") Long id, @Valid ProjectDto dto) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        projectService.update(entity, dto);
        return projectMapper.toDto(entity);
    }

    @PUT
    @Path("/{id}/git")
    public ProjectDto updateGit(@PathParam("id") Long id, @Valid GitDto dto) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        entity.git.name = dto.name;
        entity.git.url = dto.url;
        entity.git.platform = dto.platform;
        entity.git.persist();
        return projectMapper.toDto(entity);
    }

    @PUT
    @Path("/{id}/credential")
    public ProjectDto updateCredential(@PathParam("id") Long id, @Valid CredentialDto dto) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        entity.credential.name = dto.name;
        entity.credential.type = dto.type;
        entity.credential.token = dto.token;
        entity.credential.username = dto.username;
        entity.credential.persist();
        return projectMapper.toDto(entity);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        entity.delete();
        return Response.noContent().build();
    }

    // Context sub-resource endpoints

    @GET
    @Path("/{id}/context")
    public List<GitContextDto> listContexts(@PathParam("id") Long id) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return GitContextEntity.<GitContextEntity>list("git", project.git).stream()
                .map(gitContextMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}/context/{contextId}")
    public GitContextDto getContext(@PathParam("id") Long id, @PathParam("contextId") Long contextId) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        GitContextEntity context = (GitContextEntity) GitContextEntity.findByIdOptional(contextId)
                .orElseThrow(NotFoundException::new);
        if (!context.git.id.equals(project.git.id)) {
            throw new NotFoundException();
        }
        return gitContextMapper.toDto(context);
    }

    @POST
    @Path("/{id}/context")
    public Response createContext(@PathParam("id") Long id, @Valid GitContextDto dto) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        GitContextEntity entity = gitContextMapper.toEntity(dto, project.git);
        entity.persist();
        return Response.status(Response.Status.CREATED)
                .entity(gitContextMapper.toDto(entity))
                .build();
    }

    @PUT
    @Path("/{id}/context/{contextId}")
    public GitContextDto updateContext(@PathParam("id") Long id, @PathParam("contextId") Long contextId,
            @Valid GitContextDto dto) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        GitContextEntity context = (GitContextEntity) GitContextEntity.findByIdOptional(contextId)
                .orElseThrow(NotFoundException::new);
        if (!context.git.id.equals(project.git.id)) {
            throw new NotFoundException();
        }
        gitContextMapper.updateEntity(dto, context);
        return gitContextMapper.toDto(context);
    }

    @DELETE
    @Path("/{id}/context/{contextId}")
    public Response deleteContext(@PathParam("id") Long id, @PathParam("contextId") Long contextId) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        GitContextEntity context = (GitContextEntity) GitContextEntity.findByIdOptional(contextId)
                .orElseThrow(NotFoundException::new);
        if (!context.git.id.equals(project.git.id)) {
            throw new NotFoundException();
        }
        context.delete();
        return Response.noContent().build();
    }
}
