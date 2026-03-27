package org.acme.services.changerequest;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.services.changerequest.gitlab.CreateMergeRequest;
import org.acme.services.changerequest.gitlab.GitLabApi;
import org.acme.services.changerequest.gitlab.MergeRequestResponse;
import org.acme.services.git.GitManager;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class GitLabChangeRequestProvider implements ChangeRequestProvider {

    @Override
    public boolean supports(GitVendorType vendorType) {
        return vendorType == GitVendorType.GITLAB;
    }

    @Override
    public String buildAuthenticatedPushUrl(String gitUrl, String token) {
        String host = GitManager.extractHost(gitUrl);
        String ownerRepo = GitManager.extractOwnerRepo(gitUrl);
        return "https://oauth2:" + token + "@" + host + "/" + ownerRepo + ".git";
    }

    @Override
    public ChangeRequestResult createChangeRequest(ChangeRequestParams params) throws Exception {
        GitLabApi api = buildClient(params);
        String upstreamEncodedPath = encodePath(params.ownerRepo());

        String sourceEncodedPath = upstreamEncodedPath;
        Long targetProjectId = null;

        if (params.forkUrl() != null) {
            String forkOwnerRepo = GitManager.extractOwnerRepo(params.forkUrl());
            sourceEncodedPath = encodePath(forkOwnerRepo);
            targetProjectId = api.getProject(upstreamEncodedPath).id();
        }

        MergeRequestResponse mr = api.createMergeRequest(sourceEncodedPath,
                new CreateMergeRequest(
                        params.branchName(),
                        params.baseBranch(),
                        params.title(),
                        params.description(),
                        targetProjectId));
        String status = mr.state() != null && mr.state().equalsIgnoreCase("opened") ? "open" : mr.state();
        return new ChangeRequestResult(mr.webUrl(), mr.title(), status);
    }

    @Override
    public ChangeRequestResult findExistingChangeRequest(ChangeRequestParams params) throws Exception {
        GitLabApi api = buildClient(params);
        String sourceEncodedPath = encodePath(params.ownerRepo());

        if (params.forkUrl() != null) {
            String forkOwnerRepo = GitManager.extractOwnerRepo(params.forkUrl());
            sourceEncodedPath = encodePath(forkOwnerRepo);
        }

        List<MergeRequestResponse> mrs = api.listMergeRequests(
                sourceEncodedPath, params.branchName(), params.baseBranch(), "opened");

        if (mrs.isEmpty()) {
            throw new IllegalStateException("MR already exists but could not be found via API");
        }
        MergeRequestResponse mr = mrs.getFirst();
        String status = mr.state() != null && mr.state().equalsIgnoreCase("opened") ? "open" : mr.state();
        return new ChangeRequestResult(mr.webUrl(), mr.title(), status);
    }

    private static String encodePath(String ownerRepo) {
        return URLEncoder.encode(ownerRepo, StandardCharsets.UTF_8);
    }

    private GitLabApi buildClient(ChangeRequestParams params) {
        String host = GitManager.extractHost(params.gitUrl());
        String hostBase = "https://" + host;

        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(hostBase));
        if (params.token() != null) {
            builder.clientHeadersFactory((inbound, outbound) -> {
                outbound.add("PRIVATE-TOKEN", params.token());
                return outbound;
            });
        }
        return builder.build(GitLabApi.class);
    }
}
