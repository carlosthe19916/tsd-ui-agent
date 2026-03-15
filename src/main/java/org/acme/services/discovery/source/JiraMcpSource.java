package org.acme.services.discovery.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.discovery.RequirementContext;
import org.acme.services.discovery.RequirementSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JiraMcpSource implements RequirementSource {

    private static final Logger LOG = Logger.getLogger(JiraMcpSource.class);

    @Inject
    @McpClientName("jira")
    McpClient mcpClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "tsd-agent.discovery.jira-mcp.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public String name() {
        return "jira-mcp";
    }

    @Override
    public boolean supports(TaskEntity task) {
        return enabled && task.type == SourceType.JIRA;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public List<RequirementContext.Comment> fetchComments(TaskEntity task) {
        try {
            String args = objectMapper.writeValueAsString(Map.of(
                    "issueKey", task.externalId
            ));

            var request = ToolExecutionRequest.builder()
                    .name("get_issue_comments")
                    .arguments(args)
                    .build();

            var result = mcpClient.executeTool(request);
            return parseComments(result.resultText());
        } catch (Exception e) {
            LOG.warnf(e, "Jira MCP source failed for task %s", task.externalId);
            return List.of();
        }
    }

    private List<RequirementContext.Comment> parseComments(String json) {
        List<RequirementContext.Comment> comments = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode commentsNode = root.has("comments") ? root.get("comments") : root;
            if (commentsNode.isArray()) {
                for (JsonNode node : commentsNode) {
                    String author = node.path("author").path("displayName").asText("unknown");
                    String body = node.path("body").isTextual()
                            ? node.path("body").asText("")
                            : extractAdfText(node.path("body"));
                    String createdAt = node.path("created").asText(null);
                    Instant instant = null;
                    if (createdAt != null) {
                        try {
                            instant = Instant.parse(createdAt);
                        } catch (Exception ignored) {}
                    }
                    comments.add(new RequirementContext.Comment(author, body, instant));
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to parse Jira MCP comments response");
        }
        return comments;
    }

    private String extractAdfText(JsonNode adf) {
        if (adf == null || adf.isNull()) return "";
        StringBuilder sb = new StringBuilder();
        collectText(adf, sb);
        return sb.toString().strip();
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node.has("text")) {
            sb.append(node.get("text").asText());
        }
        if (node.has("content")) {
            for (JsonNode child : node.get("content")) {
                collectText(child, sb);
            }
            String type = node.path("type").asText("");
            if ("paragraph".equals(type) || "heading".equals(type)) {
                sb.append("\n");
            }
        }
    }
}
