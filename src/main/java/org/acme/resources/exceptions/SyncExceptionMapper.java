package org.acme.resources.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.acme.services.sync.SyncException;

import java.util.Map;

@Provider
public class SyncExceptionMapper implements ExceptionMapper<SyncException> {

    @Override
    public Response toResponse(SyncException exception) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
