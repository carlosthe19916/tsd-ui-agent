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
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.acme.dto.CredentialDto;
import org.acme.mapper.CredentialMapper;
import org.acme.models.jpa.entity.CredentialEntity;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/credentials")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CredentialResource {

    @Inject
    CredentialMapper credentialMapper;

    @GET
    public List<CredentialDto> list() {
        return CredentialEntity.<CredentialEntity>findAll().stream()
                .map(credentialMapper::toDto)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public CredentialDto get(@PathParam("id") Long id) {
        CredentialEntity entity = (CredentialEntity) CredentialEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return credentialMapper.toDto(entity);
    }

    @POST
    public Response create(@Valid CredentialDto dto) {
        if (dto.token == null) {
            throw new BadRequestException("Token is required");
        }

        // Check for duplicate name
        long count = CredentialEntity.count("name", dto.name);
        if (count > 0) {
            throw new BadRequestException("Credential with name '" + dto.name + "' already exists");
        }

        CredentialEntity entity = credentialMapper.toEntity(dto);
        entity.persist();
        return Response.status(Response.Status.CREATED)
                .entity(credentialMapper.toDto(entity))
                .build();
    }

    @PUT
    @Path("/{id}")
    public CredentialDto update(@PathParam("id") Long id, @Valid CredentialDto dto) {
        CredentialEntity entity = (CredentialEntity) CredentialEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        // Check for duplicate name (excluding current entity)
        long count = CredentialEntity.count("name = ?1 and id <> ?2", dto.name, id);
        if (count > 0) {
            throw new BadRequestException("Credential with name '" + dto.name + "' already exists");
        }

        credentialMapper.updateEntity(dto, entity);
        entity.persist();
        return credentialMapper.toDto(entity);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        CredentialEntity entity = (CredentialEntity) CredentialEntity.findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        entity.delete();
        return Response.noContent().build();
    }
}
