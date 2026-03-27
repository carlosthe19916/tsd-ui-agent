package org.acme.resources;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.ChatMessageDto;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.ai.AgenticChatService;
import org.acme.services.ai.TaskChatContext;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.jboss.resteasy.reactive.RestStreamElementType;

@ApplicationScoped
@Path("/tasks/{taskId}/chat")
public class ChatResource {

    @Inject
    AgenticChatService agenticChatService;

    @Inject
    TaskChatContext chatContext;

    @Inject
    WorkspaceManagerResolver workspaceManagerResolver;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @POST
    @Blocking
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chat(@PathParam("taskId") Long taskId, ChatMessageDto message) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);

        // Set up request-scoped context for @Tool methods
        chatContext.taskId = taskId;
        if (task.workspace != null && task.workspace.workspaceId != null) {
            chatContext.workspace = workspaceManagerResolver
                    .resolve(task.workspace.executionMode)
                    .getWorkspace(task.workspace.workspaceId)
                    .orElse(null);
        }

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
