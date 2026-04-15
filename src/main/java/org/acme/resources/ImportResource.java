package org.acme.resources;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.ImportDto;
import org.acme.services.ImportService;
import org.acme.services.TaskSyncService;

@ApplicationScoped
@Path("/import")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    ImportService importService;

    @Inject
    TaskSyncService taskSyncService;

    @POST
    @Transactional(Transactional.TxType.NEVER)
    public Response importData(@Valid @NotNull ImportDto importDto) {
        ImportService.ImportResult importResult = QuarkusTransaction.requiringNew()
                .call(() -> importService.doImport(importDto));

        // Trigger syncs after the transaction has committed
        for (Long projectId : importResult.projectIdsToSync()) {
            taskSyncService.triggerSync(projectId);
        }

        return Response.status(Response.Status.CREATED)
                .entity(importResult.result())
                .build();
    }
}
