package org.acme.services.changerequest;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.services.changerequest.github.CreatePullRequest;
import org.acme.services.changerequest.github.GitHubApi;
import org.acme.services.changerequest.github.PullRequestResponse;
import org.acme.services.git.GitManager;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

import java.net.URI;
import java.util.List;

@ApplicationScoped
public class GitHubChangeRequestProvider implements ChangeRequestProvider {

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

        PullRequestResponse pr = api.createPullRequest(owner, repo,
                new CreatePullRequest(params.title(), head, params.baseBranch(), params.description()));
        return new ChangeRequestResult(pr.htmlUrl());
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
