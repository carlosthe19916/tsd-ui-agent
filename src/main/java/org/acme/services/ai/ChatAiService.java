package org.acme.services.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

@RegisterAiService
public interface ChatAiService {

    @SystemMessage("""
            You are a helpful assistant for software development planning.
            You help refine requirement documents, identify acceptance criteria,
            and answer questions about tasks and their requirements.
            Be concise and actionable in your responses.
            When the user asks you to modify a requirement, output the complete updated requirement
            in Markdown format.
            """)
    Multi<String> chat(@MemoryId Long planId, @UserMessage String message);
}
