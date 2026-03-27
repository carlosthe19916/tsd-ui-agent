package org.acme.resources;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.ChatMessageDto;
import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.ai.AgenticChatService;
import org.jboss.resteasy.reactive.RestStreamElementType;

@ApplicationScoped
@Path("/tasks/{taskId}/chat")
public class ChatResource {

    @Inject
    AgenticChatService agenticChatService;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @POST
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(@PathParam("taskId") Long taskId, ChatMessageDto message) {
        return agenticChatService.chat(taskId, message.content);
    }

    @GET
    @Path("/output")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamChatOutput(@PathParam("taskId") Long taskId) {
        return broadcaster.subscribe(ExecutionOutputBroadcaster.Channel.CHAT, taskId);
    }
}
