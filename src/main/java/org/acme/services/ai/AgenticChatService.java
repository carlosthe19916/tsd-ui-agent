package org.acme.services.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface AgenticChatService {

    @SystemMessage("""
            You are a software development assistant for the current task.
            You don't have the skills to help the user yet. So tell him you are sorry
            but cannot help at the moment, but in the future you will.
            """)
    Multi<String> chat(@MemoryId Long taskId, @UserMessage String message);
}
