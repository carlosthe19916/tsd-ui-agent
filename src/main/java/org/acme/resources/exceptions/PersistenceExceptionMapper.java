package org.acme.resources.exceptions;

import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class PersistenceExceptionMapper implements ExceptionMapper<PersistenceException> {

    @Override
    public Response toResponse(PersistenceException exception) {
        if (isUniqueConstraintViolation(exception)) {
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Duplicate entry: a record with the same unique fields already exists"))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "A database error occurred"))
                .build();
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        while (throwable != null) {
            String message = throwable.getMessage();
            if (message != null) {
                String upper = message.toUpperCase();
                if (upper.contains("UNIQUE") || upper.contains("DUPLICATE") || upper.contains("CONSTRAINT")) {
                    return true;
                }
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
