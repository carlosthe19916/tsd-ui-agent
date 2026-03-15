package org.acme.services.sync.camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GitHubCommentRoutes extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    throw new SyncException("GitHub comment fetch failed: " + cause.getMessage(), cause);
                });

        from("direct:github-fetch-comments")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    String owner = exchange.getIn().getHeader("owner", String.class);
                    String repo = exchange.getIn().getHeader("repo", String.class);
                    String issueNumber = exchange.getIn().getHeader("issueNumber", String.class);

                    String baseUrl = apiUrl != null ? apiUrl : "https://api.github.com";
                    exchange.getIn().setHeader("CamelHttpUrl",
                            baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments");
                    exchange.getIn().setHeader("Authorization", "Bearer " + token);
                    exchange.getIn().setHeader("Accept", "application/vnd.github+json");
                    exchange.getIn().setHeader(Exchange.HTTP_QUERY, "per_page=100");
                })
                .to("direct:http-get")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    JsonNode root = objectMapper.readTree(body);
                    List<ExternalIssueContext.Comment> comments = new ArrayList<>();
                    if (root.isArray()) {
                        for (JsonNode node : root) {
                            String author = node.path("user").path("login").asText("unknown");
                            String text = node.path("body").asText("");
                            String createdAt = node.path("created_at").asText(null);
                            Instant instant = createdAt != null ? Instant.parse(createdAt) : null;
                            comments.add(new ExternalIssueContext.Comment(author, text, instant));
                        }
                    }
                    exchange.getIn().setBody(comments);
                });

        from("direct:github-fetch-labels")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    String owner = exchange.getIn().getHeader("owner", String.class);
                    String repo = exchange.getIn().getHeader("repo", String.class);
                    String issueNumber = exchange.getIn().getHeader("issueNumber", String.class);

                    String baseUrl = apiUrl != null ? apiUrl : "https://api.github.com";
                    exchange.getIn().setHeader("CamelHttpUrl",
                            baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber);
                    exchange.getIn().setHeader("Authorization", "Bearer " + token);
                    exchange.getIn().setHeader("Accept", "application/vnd.github+json");
                })
                .to("direct:http-get")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    JsonNode root = objectMapper.readTree(body);
                    List<String> labels = new ArrayList<>();
                    JsonNode labelsNode = root.path("labels");
                    if (labelsNode.isArray()) {
                        for (JsonNode node : labelsNode) {
                            labels.add(node.path("name").asText());
                        }
                    }
                    exchange.getIn().setBody(labels);
                });
    }
}
