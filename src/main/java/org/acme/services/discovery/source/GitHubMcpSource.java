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
public class GitHubMcpSource implements RequirementSource {

    private static final Logger LOG = Logger.getLogger(GitHubMcpSource.class);

    @Inject
    @McpClientName("github")
    McpClient mcpClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "tsd-agent.discovery.github-mcp.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public String name() {
        return "github-mcp";
    }

    @Override
    public boolean supports(TaskEntity task) {
        return enabled && task.type == SourceType.GITHUB;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public List<RequirementContext.Comment> fetchComments(TaskEntity task) {
        try {
            String owner = extractOwner(task.url);
            String repo = extractRepo(task.url);
            String issueNumber = extractIssueNumber(task.externalId);

            String args = objectMapper.writeValueAsString(Map.of(
                    "owner", owner,
                    "repo", repo,
                    "issue_number", Integer.parseInt(issueNumber)
            ));

            var request = ToolExecutionRequest.builder()
                    .name("list_issue_comments")
                    .arguments(args)
                    .build();

            var result = mcpClient.executeTool(request);
            return parseComments(result.resultText());
        } catch (Exception e) {
            LOG.warnf(e, "GitHub MCP source failed for task %s", task.externalId);
            return List.of();
        }
    }

    private List<RequirementContext.Comment> parseComments(String json) {
        List<RequirementContext.Comment> comments = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String author = node.path("user").path("login").asText("unknown");
                    String body = node.path("body").asText("");
                    String createdAt = node.path("created_at").asText(null);
                    Instant instant = createdAt != null ? Instant.parse(createdAt) : null;
                    comments.add(new RequirementContext.Comment(author, body, instant));
                }
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to parse GitHub MCP comments response");
        }
        return comments;
    }

    static String extractOwner(String url) {
        // URL like https://github.com/owner/repo/issues/123
        String[] parts = url.replaceFirst("https?://[^/]+/", "").split("/");
        return parts.length > 0 ? parts[0] : "";
    }

    static String extractRepo(String url) {
        String[] parts = url.replaceFirst("https?://[^/]+/", "").split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    static String extractIssueNumber(String externalId) {
        // externalId might be just a number or contain prefix
        return externalId.replaceAll("[^0-9]", "");
    }
}
