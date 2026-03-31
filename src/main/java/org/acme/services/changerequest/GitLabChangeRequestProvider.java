package org.acme.services.changerequest;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.services.changerequest.gitlab.CreateMergeRequest;
import org.acme.services.changerequest.gitlab.GitLabApi;
import org.acme.services.changerequest.gitlab.MergeRequestResponse;
import org.acme.services.changerequest.gitlab.ProjectResponse;
import org.acme.services.git.GitManager;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@ApplicationScoped
public class GitLabChangeRequestProvider implements ChangeRequestProvider {

    private static final Logger LOG = Logger.getLogger(GitLabChangeRequestProvider.class);

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
        return new ChangeRequestResult(mr.webUrl());
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
        return new ChangeRequestResult(mrs.getFirst().webUrl());
    }

    @Override
    public String fetchPullRequestTemplate(ChangeRequestParams params) {
        GitLabApi api = buildClient(params);
        String upstreamEncodedPath = encodePath(params.ownerRepo());

        ProjectResponse project = api.getProject(upstreamEncodedPath);
        String ref = project.defaultBranch() != null ? project.defaultBranch() : "main";

        String[] templatePaths = {
                ".gitlab/merge_request_templates/Default.md",
                ".gitlab/merge_request_template.md"
        };

        for (String path : templatePaths) {
            try {
                String encodedFilePath = URLEncoder.encode(path, StandardCharsets.UTF_8);
                JsonNode node = api.getFile(upstreamEncodedPath, encodedFilePath, ref);
                String content = node.path("content").asText("");
                if (!content.isBlank()) {
                    String decoded = new String(Base64.getMimeDecoder().decode(content));
                    if (!decoded.isBlank()) {
                        LOG.debugf("Found MR template at %s for %s", path, params.ownerRepo());
                        return decoded;
                    }
                }
            } catch (ClientWebApplicationException e) {
                if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                    continue;
                }
                LOG.debugf("Error fetching MR template at %s: %s", path, e.getMessage());
            } catch (Exception e) {
                LOG.debugf("Error fetching MR template at %s: %s", path, e.getMessage());
            }
        }
        return null;
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
