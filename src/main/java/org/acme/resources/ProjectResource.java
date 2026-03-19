package org.acme.resources;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
import org.acme.dto.ProjectDto;
import org.acme.dto.ProjectGitMappingDto;
import org.acme.dto.TestConnectionDto;
import org.acme.mapper.ProjectGitMappingMapper;
import org.acme.mapper.ProjectMapper;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.ProjectGitMappingEntity;
import org.acme.models.jpa.entity.SyncStatus;
import org.acme.services.ProjectService;
import org.acme.services.TaskSyncService;
import org.acme.services.sync.SyncException;
import org.acme.services.sync.SyncManager;

import java.util.List;
import java.util.Map;
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
    ProjectService projectService;

    @Inject
    TaskSyncService taskSyncService;

    @Inject
    SyncManager syncManager;

    @Inject
    ProjectGitMappingMapper mappingMapper;

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

    @POST
    @Path("/{id}/sync")
    @Transactional(Transactional.TxType.NEVER)
    public Response sync(@PathParam("id") Long id) {
        ProjectDto[] result = new ProjectDto[1];
        boolean[] conflict = new boolean[1];
        QuarkusTransaction.requiringNew().run(() -> {
            ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                    .orElseThrow(NotFoundException::new);
            if (entity.syncStatus == SyncStatus.SYNCHRONIZATION_IN_PROGRESS) {
                conflict[0] = true;
                result[0] = projectMapper.toDto(entity);
                return;
            }
            entity.syncStatus = SyncStatus.SYNCHRONIZATION_IN_PROGRESS;
            entity.persist();
            result[0] = projectMapper.toDto(entity);
        });
        if (conflict[0]) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(result[0])
                    .build();
        }
        taskSyncService.triggerSync(id);
        return Response.accepted(result[0]).build();
    }

    @POST
    @Path("/test-query")
    public Response testQuery(@Valid TestConnectionDto dto) {
        CredentialEntity cred = (CredentialEntity) CredentialEntity.findByIdOptional(dto.credentialId)
                .orElseThrow(NotFoundException::new);

        try {
            syncManager.testQuery(dto.type, dto.apiUrl, dto.query, cred.token);
            return Response.ok(Map.of("status", "ok")).build();
        } catch (SyncException e) {
            return Response.status(422)
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/test-connection")
    public Response testConnection(@Valid TestConnectionDto dto) {
        CredentialEntity cred = (CredentialEntity) CredentialEntity.findByIdOptional(dto.credentialId)
                .orElseThrow(NotFoundException::new);

        try {
            syncManager.testConnection(dto.type, dto.apiUrl, dto.query, cred.token);
            return Response.ok(Map.of("status", "ok")).build();
        } catch (SyncException e) {
            return Response.status(422)
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    // Git Mapping sub-resource endpoints

    @GET
    @Path("/{id}/git-mappings")
    public List<ProjectGitMappingDto> listMappings(@PathParam("id") Long id) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return ProjectGitMappingEntity.<ProjectGitMappingEntity>list("project", project)
                .stream()
                .map(mappingMapper::toDto)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/{id}/git-mappings")
    public Response createMapping(@PathParam("id") Long id, @Valid ProjectGitMappingDto dto) {
        ProjectEntity project = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        ProjectGitMappingEntity entity = mappingMapper.toEntity(dto, project);
        entity.persist();
        return Response.status(Response.Status.CREATED)
                .entity(mappingMapper.toDto(entity))
                .build();
    }

    @DELETE
    @Path("/{id}/git-mappings/{mappingId}")
    public Response deleteMapping(@PathParam("id") Long id, @PathParam("mappingId") Long mappingId) {
        ProjectGitMappingEntity mapping = ProjectGitMappingEntity.findById(mappingId);
        if (mapping == null || !mapping.project.id.equals(id)) {
            throw new NotFoundException();
        }
        mapping.delete();
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        ProjectEntity entity = (ProjectEntity) ProjectEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        entity.delete();
        return Response.noContent().build();
    }
}
