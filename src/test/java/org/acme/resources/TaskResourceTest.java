package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.PlanDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.git.GitManager;
import org.acme.services.ai.RequirementSummarizerService;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.acme.services.ChangeRequestService;
import org.acme.services.WorktreeService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class TaskResourceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    GitManager gitManager;

    @InjectMock
    RequirementSummarizerService aiService;

    @InjectMock
    WorktreeService worktreeService;

    @InjectMock
    ChangeRequestService changeRequestService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
        when(aiService.summarize(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("## Summary\nDefault test requirement");
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
        when(gitManager.addWorktree(anyString(), anyString()))
                .thenAnswer(invocation -> "/tmp/tsd-agent-ui-test/repo/trees/" + invocation.getArgument(1));
        when(worktreeService.ensureWorktree(any()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree");
        doNothing().when(worktreeService).openVSCode(anyString());
        doNothing().when(worktreeService).openTerminal(anyString());
        when(worktreeService.openClaude(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn("test-session-id");
        doNothing().when(gitManager).addAll(anyString());
        doNothing().when(gitManager).commit(anyString(), anyString());
        doNothing().when(gitManager).push(anyString(), anyString(), anyString());
        when(gitManager.getCurrentBranch(anyString())).thenReturn("main");
        doNothing().when(changeRequestService).triggerChangeRequest(any());
    }

    private int createProjectAndSync(SourceType type, List<ExternalIssue> issues) {
        when(syncManager.fetchIssues(any())).thenReturn(issues);


        CredentialDto cred = new CredentialDto();
        cred.name = "task-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto dto = new ProjectDto();
        dto.name = "task-project-" + System.nanoTime();
        dto.apiUrl = "https://github.com/owner/repo";
        dto.type = type;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        int id = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .body("syncStatus", is("SYNCHRONIZED"))
        );

        return id;
    }

    private static ExternalIssue issue(String externalId, String title, TaskStatus status) {
        return issue(externalId, title, null, status);
    }

    private static ExternalIssue issue(String externalId, String title, String description, TaskStatus status) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
        ext.description = description;
        ext.externalStatus = status.name();
        ext.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        ext.updatedAt = Instant.parse("2025-06-01T00:00:00Z");
        return ext;
    }

    @Test
    void testListAll() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("list-1", "List task", TaskStatus.OPEN)
        ));

        given()
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.offset", is(0))
                .body("meta.limit", is(10))
                .body("data.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    void testPagination() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("pag-1", "Pag A", TaskStatus.OPEN),
                issue("pag-2", "Pag B", TaskStatus.OPEN),
                issue("pag-3", "Pag C", TaskStatus.OPEN)
        ));

        given()
                .queryParam("filterText", "Pag")
                .queryParam("limit", 2)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.limit", is(2))
                .body("meta.count", is(3))
                .body("data", hasSize(2));

        given()
                .queryParam("filterText", "Pag")
                .queryParam("offset", 2)
                .queryParam("limit", 2)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("data", hasSize(1));
    }

    @Test
    void testFilterByProjectId() {
        int projectId = createProjectAndSync(SourceType.GITHUB, List.of(
                issue("fp-1", "Filter proj", TaskStatus.OPEN)
        ));

        given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("data.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));

        given()
                .queryParam("projectId", 999999)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(0))
                .body("data", empty());
    }

    @Test
    void testFilterByStatus() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("fs-1", "Status open", TaskStatus.OPEN),
                issue("fs-2", "Status closed", TaskStatus.CLOSED)
        ));

        // All synced tasks default to TaskEntity.status=OPEN (externalStatus is a separate string field)
        given()
                .queryParam("filterText", "Status")
                .queryParam("status", "OPEN")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(2))
                .body("data[0].status", is("OPEN"));
    }

    @Test
    void testFilterText() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("ftext-1", "UniqueSearchTerm Alpha", TaskStatus.OPEN),
                issue("ftext-2", "Something else", TaskStatus.OPEN)
        ));

        given()
                .queryParam("filterText", "uniquesearchterm")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1))
                .body("data[0].title", is("UniqueSearchTerm Alpha"));
    }

    @Test
    void testSortBy() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("sort-1", "Zebra sort task", TaskStatus.OPEN),
                issue("sort-2", "Alpha sort task", TaskStatus.OPEN)
        ));

        given()
                .queryParam("filterText", "sort task")
                .queryParam("sort_by", "title:asc")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("data[0].title", is("Alpha sort task"))
                .body("data[1].title", is("Zebra sort task"));
    }

    @Test
    void testCombinedFilters() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("combo-1", "Combo match", TaskStatus.OPEN),
                issue("combo-2", "Combo match", TaskStatus.CLOSED),
                issue("combo-3", "Combo other", TaskStatus.OPEN)
        ));

        // All synced tasks default to TaskEntity.status=OPEN, so status filter matches both "Combo match" tasks
        given()
                .queryParam("filterText", "Combo match")
                .queryParam("status", "OPEN")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(2));
    }

    @Test
    void testEmptyResult() {
        given()
                .queryParam("filterText", "nonexistent_xyzzy_12345")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(0))
                .body("data", empty());
    }

    private int createTaskAndReturnId() {
        int projectId = createProjectAndSync(SourceType.GITHUB, List.of(
                issue("ctx-" + System.nanoTime(), "Context test task", TaskStatus.OPEN)
        ));

        return given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");
    }

    // Plan sub-resource tests

    private static PlanDto planDto(String plan) {
        PlanDto dto = new PlanDto();
        dto.plan = plan;
        return dto;
    }

    @Test
    void testCreatePlan() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto("# My Plan");

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("plan", is("# My Plan"))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    void testGetPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Auto Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Auto Plan"));
    }

    @Test
    void testGetPlanNotFound() {
        int taskId = createTaskAndReturnId();

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdatePlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Draft"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Final"))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Final"));
    }

    @Test
    void testCreateAndUpdatePlan() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto("# Step 1\nDo something");

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("plan", is("# Step 1\nDo something"));

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Step 1\nDo something"));

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Step 1\nUpdated plan"))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Step 1\nUpdated plan"));

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Step 1\nUpdated plan"));
    }

    @Test
    void testDeletePlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# To delete"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .when().delete("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(204);

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testCreatePlanConflict() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# First"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Second"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(409);
    }

    // Plan requirement & git tests

    private int createCredential(String name) {
        CredentialDto cred = new CredentialDto();
        cred.name = name;
        cred.token = "test-token-" + name;
        return given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private int createGit(String url) {
        return createGit(url, null);
    }

    private int createGit(String url, Long credentialId) {
        GitDto gitDto = new GitDto();
        gitDto.url = url;
        if (credentialId != null) {
            CredentialDto credDto = new CredentialDto();
            credDto.id = credentialId;
            gitDto.credential = credDto;
        }
        return given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private static PlanDto planDto(String plan, String requirement, Long gitId) {
        PlanDto dto = new PlanDto();
        dto.plan = plan;
        dto.requirement = requirement;
        if (gitId != null) {
            GitDto git = new GitDto();
            git.id = gitId;
            dto.git = git;
        }
        return dto;
    }

    @Test
    void testCreatePlanWithRequirement() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto("# Content", "Must support Java 25", null);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("requirement", is("Must support Java 25"));

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("requirement", is("Must support Java 25"));
    }

    @Test
    void testCreatePlanWithGit() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/plan-git");

        PlanDto plan = planDto("# Content", null, (long) gitId);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId))
                .body("git.url", is("https://github.com/test/plan-git"));

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git.id", is(gitId))
                .body("git.url", is("https://github.com/test/plan-git"));
    }

    @Test
    void testCreatePlanWithRequirementAndGit() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/plan-both");

        PlanDto plan = planDto("# Both", "Requirement text", (long) gitId);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("requirement", is("Requirement text"))
                .body("git.id", is(gitId))
                .body("git.url", is("https://github.com/test/plan-both"));
    }

    @Test
    void testUpdatePlanRequirement() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Draft", "Old requirement", null))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Draft", "New requirement", null))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("requirement", is("New requirement"));
    }

    @Test
    void testUpdatePlanGit() {
        int taskId = createTaskAndReturnId();
        int gitId1 = createGit("https://github.com/test/git-v1");
        int gitId2 = createGit("https://github.com/test/git-v2");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId1))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId1));

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId2))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git.id", is(gitId2))
                .body("git.url", is("https://github.com/test/git-v2"));
    }

    @Test
    void testUpdatePlanClearGit() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/git-clear");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId));

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, null))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git", nullValue());
    }

    @Test
    void testCreatePlanWithInvalidGitId() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto("# Plan", null, 999999L);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git", nullValue());
    }

    private int createTaskWithDescriptionAndReturnId(String description) {
        int projectId = createProjectAndSync(SourceType.GITHUB, List.of(
                issue("desc-" + System.nanoTime(), "Task with description", description, TaskStatus.OPEN)
        ));

        return given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");
    }

    @Test
    void testCreatePlanAutoPopulatesRequirementFromDescription() {
        int taskId = createTaskWithDescriptionAndReturnId("Detailed task description");

        PlanDto plan = planDto("# Content");

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("requirement", is("Detailed task description"))
                .body("isRequirementInProgress", is(true));

        // Wait for AI discovery to complete
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/tasks/{taskId}/plan", taskId)
                        .then()
                        .body("isRequirementInProgress", is(false))
                        .body("requirement", is("## Summary\nDefault test requirement")));
    }

    @Test
    void testCreatePlanAutoPopulatesRequirementFromTitleWhenNoDescription() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto("# Content");

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("requirement", is("Context test task"))
                .body("isRequirementInProgress", is(false));
    }

    @Test
    void testCreatePlanPreservesExplicitRequirement() {
        int taskId = createTaskWithDescriptionAndReturnId("Some description");

        PlanDto plan = planDto("# Content", "My explicit requirement", null);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("requirement", is("My explicit requirement"))
                .body("isRequirementInProgress", is(true));
    }

    // Worktree / Open VSCode / Open Terminal tests

    @Test
    void testOpenVSCodeWithoutPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-vscode", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testOpenVSCodeWithoutGit() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-vscode", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testOpenVSCodeSuccess() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/worktree-vscode");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-vscode", taskId)
                .then()
                .statusCode(204);
    }

    @Test
    void testOpenTerminalWithoutPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-terminal", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testOpenTerminalWithoutGit() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-terminal", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testOpenTerminalSuccess() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/worktree-terminal");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-terminal", taskId)
                .then()
                .statusCode(204);
    }

    @Test
    void testOpenClaudeWithoutPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-claude", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testOpenClaudeWithoutGit() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-claude", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testOpenClaudeSuccess() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/worktree-claude");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-claude", taskId)
                .then()
                .statusCode(204);
    }

    @Test
    void testOpenVSCodeForNonExistentTask() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-vscode", 999999)
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdatePlanClearsWorktreePathOnGitChange() {
        int taskId = createTaskAndReturnId();
        int gitId1 = createGit("https://github.com/test/worktree-change-1");
        int gitId2 = createGit("https://github.com/test/worktree-change-2");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId1))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId1));

        // Change git repo
        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId2))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git.id", is(gitId2))
                .body("worktreePath", nullValue());
    }

    // PATCH plan tests

    @Test
    void testPatchPlanOnly() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Draft"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        PlanDto patch = new PlanDto();
        patch.plan = "# Updated";

        given()
                .contentType(ContentType.JSON)
                .body(patch)
                .when().patch("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# Updated"));
    }

    @Test
    void testPatchPlanPreservesGitWhenNotSent() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/patch-git");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId));

        PlanDto patch = new PlanDto();
        patch.plan = "# New";

        given()
                .contentType(ContentType.JSON)
                .body(patch)
                .when().patch("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("plan", is("# New"))
                .body("git.id", is(gitId));
    }

    @Test
    void testPatchPlanNotFound() {
        int taskId = createTaskAndReturnId();

        PlanDto patch = new PlanDto();
        patch.plan = "# New";

        given()
                .contentType(ContentType.JSON)
                .body(patch)
                .when().patch("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testOpenClaudeGeneratesSessionId() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/claude-session");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("claudeSessionId", nullValue());

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-claude", taskId)
                .then()
                .statusCode(204);

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("claudeSessionId", is("test-session-id"));
    }

    @Test
    void testUpdatePlanClearsSessionIdOnGitChange() {
        int taskId = createTaskAndReturnId();
        int gitId1 = createGit("https://github.com/test/session-clear-1");
        int gitId2 = createGit("https://github.com/test/session-clear-2");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId1))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        // Open Claude to generate session ID
        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/open-claude", taskId)
                .then()
                .statusCode(204);

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("claudeSessionId", is("test-session-id"));

        // Change git repo — should clear session ID
        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId2))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git.id", is(gitId2))
                .body("claudeSessionId", nullValue());
    }

    // Change Request endpoint tests

    @Test
    void testChangeRequestWithoutPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(404);
    }

    @Test
    void testChangeRequestWithoutGit() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testChangeRequestWithoutCredential() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/cr-no-token");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testPlanGitCredentialDoesNotExposeToken() {
        int credId = createCredential("plan-git-cred");
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/token-field", (long) credId);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.credential.id", is(credId))
                .body("git.credential.name", is("plan-git-cred"))
                .body("git.credential.token", nullValue());

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git.credential.id", is(credId))
                .body("git.credential.name", is("plan-git-cred"))
                .body("git.credential.token", nullValue());
    }

    @Test
    void testChangeRequestWithoutExecution() {
        int credId = createCredential("cr-no-exec-cred");
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/cr-no-exec", (long) credId);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testChangeRequestForNonExistentTask() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", 999999)
                .then()
                .statusCode(404);
    }

    @Test
    void testTaskListIncludesPlanFields() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/plan-list");

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Listed", "List requirement", (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .queryParam("filterText", "Context test task")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("data.find { it.id == " + taskId + " }.plan.requirement", is("List requirement"))
                .body("data.find { it.id == " + taskId + " }.plan.git.url", is("https://github.com/test/plan-list"));
    }
}
