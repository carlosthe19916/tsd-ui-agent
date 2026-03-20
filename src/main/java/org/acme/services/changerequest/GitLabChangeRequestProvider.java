package org.acme.services.changerequest;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.services.git.GitManager;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.models.Constants;

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
        try (GitLabApi api = buildClient(params)) {
            MergeRequestParams mrParams = new MergeRequestParams()
                    .withSourceBranch(params.branchName())
                    .withTargetBranch(params.baseBranch())
                    .withTitle(params.title())
                    .withDescription(params.description());

            if (params.forkUrl() != null) {
                String forkOwnerRepo = GitManager.extractOwnerRepo(params.forkUrl());
                Project fork = api.getProjectApi().getProject(forkOwnerRepo);
                mrParams.withTargetProjectId(fork.getId());
            }

            MergeRequest mr = api.getMergeRequestApi()
                    .createMergeRequest(params.ownerRepo(), mrParams);
            return new ChangeRequestResult(mr.getWebUrl());
        }
    }

    @Override
    public ChangeRequestResult findExistingChangeRequest(ChangeRequestParams params) throws Exception {
        try (GitLabApi api = buildClient(params)) {
            Project project = api.getProjectApi().getProject(params.ownerRepo());
            MergeRequestFilter filter = new MergeRequestFilter()
                    .withProjectId(project.getId())
                    .withSourceBranch(params.branchName())
                    .withTargetBranch(params.baseBranch())
                    .withState(Constants.MergeRequestState.OPENED);

            List<MergeRequest> mrs = api.getMergeRequestApi().getMergeRequests(filter);

            if (mrs.isEmpty()) {
                throw new IllegalStateException("MR already exists but could not be found via API");
            }
            return new ChangeRequestResult(mrs.getFirst().getWebUrl());
        }
    }

    private GitLabApi buildClient(ChangeRequestParams params) {
        String host = GitManager.extractHost(params.gitUrl());
        String hostBase = "https://" + host;
        return new GitLabApi(hostBase, params.token());
    }
}
