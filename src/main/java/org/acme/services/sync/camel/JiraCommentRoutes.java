package org.acme.services.sync.camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncException;
import org.acme.services.sync.camel.processor.JiraUrlProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JiraCommentRoutes extends RouteBuilder {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    throw new SyncException("Jira comment fetch failed: " + cause.getMessage(), cause);
                });

        from("direct:jira-fetch-comments")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    String issueKey = exchange.getIn().getHeader("issueKey", String.class);

                    String url = apiUrl;
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    exchange.getIn().setHeader("CamelHttpUrl",
                            url + "/rest/api/3/issue/" + issueKey + "/comment");
                    exchange.getIn().setHeader("Authorization", JiraUrlProcessor.jiraAuth(token));
                    exchange.getIn().setHeader("Accept", "application/json");
                })
                .to("direct:http-get")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode commentsNode = root.has("comments") ? root.get("comments") : root;
                    List<ExternalIssueContext.Comment> comments = new ArrayList<>();
                    if (commentsNode.isArray()) {
                        for (JsonNode node : commentsNode) {
                            String author = node.path("author").path("displayName").asText("unknown");
                            String text = extractText(node.path("body"));
                            comments.add(new ExternalIssueContext.Comment(author, text, null));
                        }
                    }
                    exchange.getIn().setBody(comments);
                });

        from("direct:jira-fetch-labels")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    String issueKey = exchange.getIn().getHeader("issueKey", String.class);

                    String url = apiUrl;
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    exchange.getIn().setHeader("CamelHttpUrl",
                            url + "/rest/api/3/issue/" + issueKey);
                    exchange.getIn().setHeader("Authorization", JiraUrlProcessor.jiraAuth(token));
                    exchange.getIn().setHeader("Accept", "application/json");
                    exchange.getIn().setHeader(Exchange.HTTP_QUERY, "fields=labels");
                })
                .to("direct:http-get")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    JsonNode root = objectMapper.readTree(body);
                    List<String> labels = new ArrayList<>();
                    JsonNode labelsNode = root.path("fields").path("labels");
                    if (labelsNode.isArray()) {
                        for (JsonNode node : labelsNode) {
                            labels.add(node.asText());
                        }
                    }
                    exchange.getIn().setBody(labels);
                });
    }

    private String extractText(JsonNode adf) {
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
