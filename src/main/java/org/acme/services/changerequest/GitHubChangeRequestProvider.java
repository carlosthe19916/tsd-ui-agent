package org.acme.services.changerequest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.services.changerequest.github.CreatePullRequest;
import org.acme.services.changerequest.github.GitHubApi;
import org.acme.services.changerequest.github.PullRequestResponse;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

import java.net.URI;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class GitHubChangeRequestProvider implements ChangeRequestProvider {

    private static final Logger LOG = Logger.getLogger(GitHubChangeRequestProvider.class);

    @Override
    public boolean supports(GitVendorType vendorType) {
        return vendorType == GitVendorType.GITHUB;
    }

    @Override
    public String buildAuthenticatedPushUrl(String gitUrl, String token) {
        String host = GitManager.extractHost(gitUrl);
        String ownerRepo = GitManager.extractOwnerRepo(gitUrl);
        return "https://x-access-token:" + token + "@" + host + "/" + ownerRepo + ".git";
    }

    @Override
    public ChangeRequestResult createChangeRequest(ChangeRequestParams params) throws Exception {
        GitHubApi api = buildClient(params);
        String ownerRepo = params.ownerRepo();
        String owner = ownerRepo.substring(0, ownerRepo.indexOf('/'));
        String repo = ownerRepo.substring(ownerRepo.indexOf('/') + 1);

        String head = computeHead(params);

        try {
            PullRequestResponse pr = api.createPullRequest(owner, repo,
                    new CreatePullRequest(params.title(), head, params.baseBranch(), params.description()));
            return new ChangeRequestResult(pr.htmlUrl());
        } catch (ClientWebApplicationException e) {
            Response response = e.getResponse();
            String body = response != null ? response.readEntity(String.class) : "no response body";
            LOG.errorf("GitHub API error creating PR (head=%s, base=%s): %d — %s", head, params.baseBranch(), e.getResponse().getStatus(), body);
            throw e;
        }
    }

    @Override
    public ChangeRequestResult findExistingChangeRequest(ChangeRequestParams params) throws Exception {
        GitHubApi api = buildClient(params);
        String ownerRepo = params.ownerRepo();
        String owner = ownerRepo.substring(0, ownerRepo.indexOf('/'));
        String repo = ownerRepo.substring(ownerRepo.indexOf('/') + 1);

        String head = computeHead(params);

        List<PullRequestResponse> prs = api.listPullRequests(owner, repo, head, "open");
        if (prs.isEmpty()) {
            throw new IllegalStateException("PR already exists but could not be found via API");
        }
        return new ChangeRequestResult(prs.getFirst().htmlUrl());
    }

    private String computeHead(ChangeRequestParams params) {
        String head = params.branchName();
        if (params.forkUrl() != null) {
            String forkOwnerRepo = GitManager.extractOwnerRepo(params.forkUrl());
            String forkOwner = forkOwnerRepo.contains("/")
                    ? forkOwnerRepo.substring(0, forkOwnerRepo.indexOf('/'))
                    : forkOwnerRepo;
            head = forkOwner + ":" + params.branchName();
        }
        return head;
    }

    @Override
    public String fetchPullRequestTemplate(ChangeRequestParams params) {
        GitHubApi api = buildClient(params);
        String ownerRepo = params.ownerRepo();
        String owner = ownerRepo.substring(0, ownerRepo.indexOf('/'));
        String repo = ownerRepo.substring(ownerRepo.indexOf('/') + 1);

        String[] templatePaths = {
                ".github/PULL_REQUEST_TEMPLATE.md",
                ".github/pull_request_template.md",
                "PULL_REQUEST_TEMPLATE.md",
                "pull_request_template.md",
                "docs/pull_request_template.md"
        };

        for (String path : templatePaths) {
            try {
                JsonNode node = api.getContents(owner, repo, path);
                String content = node.path("content").asText("");
                if (!content.isBlank()) {
                    String decoded = new String(Base64.getMimeDecoder().decode(content));
                    if (!decoded.isBlank()) {
                        LOG.debugf("Found PR template at %s for %s", path, ownerRepo);
                        return decoded;
                    }
                }
            } catch (ClientWebApplicationException e) {
                if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                    continue;
                }
                LOG.debugf("Error fetching PR template at %s: %s", path, e.getMessage());
            } catch (Exception e) {
                LOG.debugf("Error fetching PR template at %s: %s", path, e.getMessage());
            }
        }
        return null;
    }

    private GitHubApi buildClient(ChangeRequestParams params) {
        String host = GitManager.extractHost(params.gitUrl());
        String apiBase = "github.com".equals(host)
                ? "https://api.github.com"
                : "https://" + host + "/api/v3";

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(apiBase));
        if (params.token() != null) {
            builder.clientHeadersFactory((inbound, outbound) -> {
                outbound.add("Authorization", "Bearer " + params.token());
                return outbound;
            });
        }
        return builder.build(GitHubApi.class);
    }
}
