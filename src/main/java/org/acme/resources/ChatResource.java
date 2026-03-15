package org.acme.resources;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.ChatMessageDto;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.ai.ChatAiService;
import org.jboss.resteasy.reactive.RestStreamElementType;

@ApplicationScoped
@Path("/tasks/{taskId}/plan/chat")
public class ChatResource {

    @Inject
    ChatAiService chatAiService;

    @POST
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(@PathParam("taskId") Long taskId, ChatMessageDto message) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException();
        }
        return chatAiService.chat(task.plan.id, message.content);
    }
}
