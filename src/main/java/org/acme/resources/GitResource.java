package org.acme.resources;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
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
import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.GitEntity;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.GitService;
import org.jboss.resteasy.reactive.RestStreamElementType;

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
    ExecutionOutputBroadcaster broadcaster;

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

    @GET
    @Path("/{id}/output")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Transactional(TxType.NOT_SUPPORTED)
    public Multi<String> streamProvisionOutput(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> {
                    GitEntity entity = (GitEntity) GitEntity.findByIdOptional(id)
                            .orElseThrow(NotFoundException::new);
                    return entity.isProvisioningInProgress ? id : null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(gitId ->
                        gitId != null ? broadcaster.subscribe(Channel.GIT, gitId) : Multi.createFrom().empty());
    }

}
