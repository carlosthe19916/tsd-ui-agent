package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.git.GitException;
import org.acme.services.git.GitManager;
import org.acme.services.sync.SyncException;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ChangeRequestService {

    private static final Logger LOG = Logger.getLogger(ChangeRequestService.class);

    @Inject
    GitManager gitManager;

    @Inject
    ProducerTemplate template;

    public void triggerChangeRequest(Long taskId) {
        Thread.startVirtualThread(() -> doChangeRequest(taskId));
    }

    void doChangeRequest(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Collect data in a short transaction
            record ChangeRequestContext(
                    String worktreePath, String mainClonePath, String gitUrl,
                    String forkUrl, String taskTitle, String requirement,
                    Long planId, String gitToken, String gitBranch
            ) {}


            ChangeRequestContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null || task.plan.git == null) {
                    LOG.warnf("Task %d, plan, or git not found during change request", taskId);
                    return null;
                }

                String worktreePath = task.plan.worktreePath;
                String mainClonePath = task.plan.git.localPath;
                String gitUrl = task.plan.git.url;
                String forkUrl = task.plan.git.forkUrl;
                String taskTitle = task.title;
                String requirement = task.plan.requirement;
                Long planId = task.plan.id;
                String gitToken = task.plan.git.credential != null ? task.plan.git.credential.token : null;
                String gitBranch = task.plan.git.branch;

                return new ChangeRequestContext(
                        worktreePath, mainClonePath, gitUrl,
                        forkUrl, taskTitle, requirement,
                        planId, gitToken, gitBranch
                );
            });

            if (context == null) {
                return;
            }

            // Phase 2: Git operations and PR creation outside of any transaction
            try {
                gitManager.addAll(context.worktreePath());
                gitManager.commit(context.worktreePath(), context.taskTitle());
            } catch (GitException e) {
                LOG.infof("Task %d: No changes to commit, proceeding with push: %s", taskId, e.getMessage());
            }

            String baseBranch = (context.gitBranch() != null && !context.gitBranch().isBlank())
                    ? context.gitBranch()
                    : gitManager.getCurrentBranch(context.mainClonePath());
            String branchName = GitManager.planBranchName(context.planId());
            boolean isGitLab = context.gitUrl().contains("gitlab");

            String pushTargetUrl = context.forkUrl() != null ? context.forkUrl() : context.gitUrl();
            if (context.gitToken() != null) {
                String authenticatedUrl = buildAuthenticatedPushUrl(pushTargetUrl, context.gitToken(), isGitLab);
                gitManager.pushToUrl(context.worktreePath(), authenticatedUrl, "HEAD:" + branchName);
            } else if (context.forkUrl() == null) {
                gitManager.push(context.worktreePath(), "origin", branchName);
            } else {
                gitManager.push(context.worktreePath(), "fork", branchName);
            }
            String ownerRepo = GitManager.extractOwnerRepo(context.gitUrl());

            String apiUrl;
            String body;
            Map<String, Object> headers = new HashMap<>();
            String responseUrlField;
            String head = branchName;

            if (isGitLab) {
                // GitLab Merge Request API
                String gitlabHost = GitManager.extractHost(context.gitUrl());
                String gitlabApiBase = "https://" + gitlabHost + "/api/v4";
                String encodedProject = URLEncoder.encode(ownerRepo, StandardCharsets.UTF_8);
                apiUrl = gitlabApiBase + "/projects/" + encodedProject + "/merge_requests";

                if (context.forkUrl() != null) {
                    String forkOwnerRepo = GitManager.extractOwnerRepo(context.forkUrl());
                    String encodedFork = URLEncoder.encode(forkOwnerRepo, StandardCharsets.UTF_8);
                    String forkLookupUrl = gitlabApiBase + "/projects/" + encodedFork;

                    Map<String, Object> lookupHeaders = new HashMap<>();
                    lookupHeaders.put("CamelHttpUrl", forkLookupUrl);
                    if (context.gitToken() != null) {
                        lookupHeaders.put("PRIVATE-TOKEN", context.gitToken());
                    }
                    String forkResponse = template.requestBodyAndHeaders("direct:http-get", null, lookupHeaders, String.class);
                    int forkProjectId;
                    try (JsonReader fr = Json.createReader(new StringReader(forkResponse))) {
                        forkProjectId = fr.readObject().getInt("id");
                    }

                    body = Json.createObjectBuilder()
                            .add("title", context.taskTitle())
                            .add("description", context.requirement() != null ? context.requirement() : "")
                            .add("source_branch", branchName)
                            .add("target_branch", baseBranch)
                            .add("source_project_id", forkProjectId)
                            .build()
                            .toString();
                } else {
                    body = Json.createObjectBuilder()
                            .add("title", context.taskTitle())
                            .add("description", context.requirement() != null ? context.requirement() : "")
                            .add("source_branch", branchName)
                            .add("target_branch", baseBranch)
                            .build()
                            .toString();
                }

                headers.put("CamelHttpUrl", apiUrl);
                headers.put(Exchange.CONTENT_TYPE, "application/json");
                if (context.gitToken() != null) {
                    headers.put("PRIVATE-TOKEN", context.gitToken());
                }
                responseUrlField = "web_url";
            } else {
                // GitHub Pull Request API
                String githubHost = GitManager.extractHost(context.gitUrl());
                String githubApiBase = "github.com".equals(githubHost)
                        ? "https://api.github.com"
                        : "https://" + githubHost + "/api/v3";
                apiUrl = githubApiBase + "/repos/" + ownerRepo + "/pulls";

                if (context.forkUrl() != null) {
                    String forkOwnerRepo = GitManager.extractOwnerRepo(context.forkUrl());
                    String forkOwner = forkOwnerRepo.contains("/")
                            ? forkOwnerRepo.substring(0, forkOwnerRepo.indexOf('/'))
                            : forkOwnerRepo;
                    head = forkOwner + ":" + branchName;
                }

                body = Json.createObjectBuilder()
                        .add("title", context.taskTitle())
                        .add("body", context.requirement() != null ? context.requirement() : "")
                        .add("head", head)
                        .add("base", baseBranch)
                        .build()
                        .toString();

                headers.put("CamelHttpUrl", apiUrl);
                headers.put(Exchange.CONTENT_TYPE, "application/json");
                headers.put("Accept", "application/vnd.github+json");
                if (context.gitToken() != null) {
                    headers.put("Authorization", "Bearer " + context.gitToken());
                }
                responseUrlField = "html_url";
            }

            LOG.infof("Task %d: Creating %s — API URL: %s, source repo: %s, target repo: %s, " +
                            "source branch: %s, target branch: %s, fork flow: %b, auth: %s, body: %s",
                    taskId, isGitLab ? "MR" : "PR", headers.get("CamelHttpUrl"),
                    context.forkUrl() != null ? context.forkUrl() : context.gitUrl(),
                    context.gitUrl(), branchName, baseBranch,
                    context.forkUrl() != null,
                    headers.containsKey("PRIVATE-TOKEN") ? "PRIVATE-TOKEN" :
                            headers.containsKey("Authorization") ? "Bearer token" : "none",
                    body);

            String responseBody;
            try {
                responseBody = template.requestBodyAndHeaders("direct:http-post", body, headers, String.class);
            } catch (SyncException e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    LOG.infof("Task %d: %s already exists, fetching existing URL", taskId, isGitLab ? "MR" : "PR");
                    responseBody = fetchExistingPr(isGitLab, apiUrl, branchName, head, context.gitToken());
                } else {
                    throw e;
                }
            }

            String htmlUrl;
            try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
                JsonObject json = reader.readObject();
                htmlUrl = json.getString(responseUrlField);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse PR/MR API response: " + responseBody, e);
            }

            LOG.infof("Task %d: %s created at %s", taskId, isGitLab ? "MR" : "PR", htmlUrl);

            // Phase 3: Store success in a short transaction
            String finalHtmlUrl = htmlUrl;
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.isChangeRequestInProgress = false;
                    task.plan.changeRequestError = null;
                    task.plan.changeRequestUrl = finalHtmlUrl;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Change request failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.isChangeRequestInProgress = false;
                        task.plan.changeRequestError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set error status for task %d change request", taskId);
            }
        } finally {
            requestContext.terminate();
        }
    }

    static String buildAuthenticatedPushUrl(String gitUrl, String gitToken, boolean isGitLab) {
        String host = GitManager.extractHost(gitUrl);
        String ownerRepo = GitManager.extractOwnerRepo(gitUrl);
        String tokenUser = isGitLab ? "oauth2" : "x-access-token";
        return "https://" + tokenUser + ":" + gitToken + "@" + host + "/" + ownerRepo + ".git";
    }

    private String fetchExistingPr(boolean isGitLab, String apiUrl,
                                   String branchName, String head, String gitToken) {
        Map<String, Object> getHeaders = new HashMap<>();
        getHeaders.put("CamelHttpUrl", apiUrl);

        if (isGitLab) {
            getHeaders.put("CamelHttpQuery",
                    "source_branch=" + URLEncoder.encode(branchName, StandardCharsets.UTF_8) + "&state=opened");
            if (gitToken != null) {
                getHeaders.put("PRIVATE-TOKEN", gitToken);
            }
        } else {
            getHeaders.put("CamelHttpQuery",
                    "head=" + URLEncoder.encode(head, StandardCharsets.UTF_8) + "&state=open");
            getHeaders.put("Accept", "application/vnd.github+json");
            if (gitToken != null) {
                getHeaders.put("Authorization", "Bearer " + gitToken);
            }
        }

        String listResponse = template.requestBodyAndHeaders("direct:http-get", null, getHeaders, String.class);

        try (JsonReader reader = Json.createReader(new StringReader(listResponse))) {
            JsonArray arr = reader.readArray();
            if (arr.isEmpty()) {
                throw new SyncException("PR/MR already exists but could not be found via API");
            }
            return arr.getJsonObject(0).toString();
        }
    }
}
