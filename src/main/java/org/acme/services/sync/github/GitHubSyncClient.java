package org.acme.services.sync.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.ExternalIssueContext;
import org.acme.services.sync.SyncException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GitHubSyncClient {

    public List<ExternalIssue> fetchIssues(String apiUrl, String query, String token) {
        URI uri = URI.create(apiUrl);
        String apiBase = uri.getScheme() + "://" + uri.getAuthority();
        String ownerRepo = extractOwnerRepo(uri.getPath());
        String[] parts = ownerRepo.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        GitHubSyncApi api = buildClient(apiBase, token);

        try {
            boolean isSearch = query != null && !query.isBlank();
            List<GitHubIssue> allIssues = new ArrayList<>();

            if (isSearch) {
                GitHubSearchResult result = api.searchIssues("repo:" + ownerRepo + " " + query, 100);
                if (result.items() != null) {
                    allIssues.addAll(result.items());
                }
            } else {
                int page = 1;
                while (true) {
                    GitHubIssue[] issues = api.listIssues(owner, repo, "all", 100, page);
                    Collections.addAll(allIssues, issues);
                    if (issues.length < 100) break;
                    page++;
                }
            }

            return allIssues.stream().map(this::toExternalIssue).toList();
        } catch (SyncException e) {
            throw e;
        } catch (WebApplicationException e) {
            throw toSyncException("GitHub sync failed", e);
        } catch (Exception e) {
            throw new SyncException("GitHub sync failed: " + e.getMessage(), e);
        }
    }

    public void testConnection(String apiUrl, String token) {
        URI uri = URI.create(apiUrl);
        String apiBase = uri.getScheme() + "://" + uri.getAuthority();
        String ownerRepo = extractOwnerRepo(uri.getPath());
        String[] parts = ownerRepo.split("/", 2);

        GitHubSyncApi api = buildClient(apiBase, token);
        try {
            api.getRepo(parts[0], parts[1]);
        } catch (WebApplicationException e) {
            throw toSyncException("GitHub connection test failed", e);
        } catch (Exception e) {
            throw new SyncException("GitHub connection test failed: " + e.getMessage(), e);
        }
    }

    public void testQuery(String apiUrl, String query, String token) {
        URI uri = URI.create(apiUrl);
        String apiBase = uri.getScheme() + "://" + uri.getAuthority();
        String ownerRepo = extractOwnerRepo(uri.getPath());
        String[] parts = ownerRepo.split("/", 2);

        GitHubSyncApi api = buildClient(apiBase, token);
        try {
            if (query != null && !query.isBlank()) {
                api.searchIssues("repo:" + ownerRepo + " " + query, 1);
            } else {
                api.listIssues(parts[0], parts[1], "all", 1, 1);
            }
        } catch (WebApplicationException e) {
            throw toSyncException("GitHub query test failed", e);
        } catch (Exception e) {
            throw new SyncException("GitHub query test failed: " + e.getMessage(), e);
        }
    }

    public List<ExternalIssueContext.Comment> fetchComments(String apiBase, String owner, String repo,
                                                            int issueNumber, String token) {
        GitHubSyncApi api = buildClient(apiBase, token);
        try {
            JsonNode root = api.listComments(owner, repo, issueNumber, 100);
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
            return comments;
        } catch (WebApplicationException e) {
            throw toSyncException("GitHub comment fetch failed", e);
        } catch (Exception e) {
            throw new SyncException("GitHub comment fetch failed: " + e.getMessage(), e);
        }
    }

    public List<String> fetchLabels(String apiBase, String owner, String repo,
                                    int issueNumber, String token) {
        GitHubSyncApi api = buildClient(apiBase, token);
        try {
            JsonNode root = api.getIssue(owner, repo, issueNumber);
            List<String> labels = new ArrayList<>();
            JsonNode labelsNode = root.path("labels");
            if (labelsNode.isArray()) {
                for (JsonNode node : labelsNode) {
                    labels.add(node.path("name").asText());
                }
            }
            return labels;
        } catch (WebApplicationException e) {
            throw toSyncException("GitHub label fetch failed", e);
        } catch (Exception e) {
            throw new SyncException("GitHub label fetch failed: " + e.getMessage(), e);
        }
    }

    private GitHubSyncApi buildClient(String apiBase, String token) {
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(apiBase));
        if (token != null) {
            builder.clientHeadersFactory((inbound, outbound) -> {
                outbound.add("Authorization", "Bearer " + token);
                return outbound;
            });
        }
        return builder.build(GitHubSyncApi.class);
    }

    public static String extractOwnerRepo(String path) {
        String after = path.replaceFirst("^/repos/", "");
        after = after.replaceFirst("/$", "");
        return after;
    }

    private ExternalIssue toExternalIssue(GitHubIssue gh) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = String.valueOf(gh.number());
        ext.url = gh.htmlUrl();
        ext.title = gh.title();
        ext.description = gh.body();
        ext.externalStatus = gh.state();
        ext.labels = gh.labels() != null ? gh.labels().stream().map(GitHubIssue.GitHubLabel::name).toList() : List.of();
        ext.createdAt = gh.createdAt() != null ? Instant.parse(gh.createdAt()) : null;
        ext.updatedAt = gh.updatedAt() != null ? Instant.parse(gh.updatedAt()) : null;
        return ext;
    }

    private SyncException toSyncException(String prefix, WebApplicationException e) {
        String body = e.getResponse().readEntity(String.class);
        int status = e.getResponse().getStatus();
        String message = (body != null && !body.isBlank())
                ? prefix + " (status " + status + "): " + body
                : prefix + ": " + e.getMessage();
        return new SyncException(message, e);
    }
}
