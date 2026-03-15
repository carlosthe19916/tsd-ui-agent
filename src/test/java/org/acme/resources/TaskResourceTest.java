package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.PlanDto;
import org.acme.dto.ProjectDto;
import org.acme.dto.TaskContextDto;
import org.acme.models.jpa.entity.ContextType;
import org.acme.models.jpa.entity.PlanStatus;
import org.acme.models.jpa.entity.PlanType;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.git.GitManager;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
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
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
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

    // Context sub-resource tests

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

    private static TaskContextDto taskContext(String name, ContextType type) {
        TaskContextDto dto = new TaskContextDto();
        dto.name = name;
        dto.type = type;
        return dto;
    }

    @Test
    void testCreateContext() {
        int taskId = createTaskAndReturnId();

        TaskContextDto ctx = taskContext("my-context", ContextType.MARKDOWN);
        ctx.description = "A test context";
        ctx.content = "# Hello";

        given()
                .contentType(ContentType.JSON)
                .body(ctx)
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is("my-context"))
                .body("description", is("A test context"))
                .body("type", is("MARKDOWN"))
                .body("content", is("# Hello"));
    }

    @Test
    void testListContexts() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(taskContext("ctx-1", ContextType.MARKDOWN))
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(taskContext("ctx-2", ContextType.GIT_REPOSITORY))
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201);

        given()
                .when().get("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(200)
                .body("size()", is(2));
    }

    @Test
    void testGetContext() {
        int taskId = createTaskAndReturnId();

        int contextId = given()
                .contentType(ContentType.JSON)
                .body(taskContext("get-ctx", ContextType.MARKDOWN))
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/tasks/{taskId}/context/{contextId}", taskId, contextId)
                .then()
                .statusCode(200)
                .body("name", is("get-ctx"))
                .body("type", is("MARKDOWN"));
    }

    @Test
    void testUpdateContext() {
        int taskId = createTaskAndReturnId();

        int contextId = given()
                .contentType(ContentType.JSON)
                .body(taskContext("before-update", ContextType.MARKDOWN))
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201)
                .extract().path("id");

        TaskContextDto updated = taskContext("after-update", ContextType.GIT_REPOSITORY);
        updated.repositoryUrl = "https://github.com/test/repo";
        updated.branch = "main";

        given()
                .contentType(ContentType.JSON)
                .body(updated)
                .when().put("/tasks/{taskId}/context/{contextId}", taskId, contextId)
                .then()
                .statusCode(200)
                .body("name", is("after-update"))
                .body("type", is("GIT_REPOSITORY"))
                .body("repositoryUrl", is("https://github.com/test/repo"))
                .body("branch", is("main"));
    }

    @Test
    void testDeleteContext() {
        int taskId = createTaskAndReturnId();

        int contextId = given()
                .contentType(ContentType.JSON)
                .body(taskContext("to-delete", ContextType.MARKDOWN))
                .when().post("/tasks/{taskId}/context", taskId)
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/tasks/{taskId}/context/{contextId}", taskId, contextId)
                .then()
                .statusCode(204);

        given()
                .when().get("/tasks/{taskId}/context/{contextId}", taskId, contextId)
                .then()
                .statusCode(404);
    }

    @Test
    void testGetContextWrongTask() {
        int taskId1 = createTaskAndReturnId();
        int taskId2 = createTaskAndReturnId();

        int contextId = given()
                .contentType(ContentType.JSON)
                .body(taskContext("wrong-task-ctx", ContextType.MARKDOWN))
                .when().post("/tasks/{taskId}/context", taskId1)
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/tasks/{taskId}/context/{contextId}", taskId2, contextId)
                .then()
                .statusCode(404);
    }

    // Plan sub-resource tests

    private static PlanDto planDto(PlanStatus status, PlanType type, String content) {
        PlanDto dto = new PlanDto();
        dto.status = status;
        dto.type = type;
        dto.content = content;
        return dto;
    }

    @Test
    void testCreatePlan() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# My Plan");

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", is("IN_PROGRESS"))
                .body("type", is("MANUAL"))
                .body("content", is("# My Plan"))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    void testGetPlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.APPROVED, PlanType.AUTO, "# Auto Plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .when().get("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("status", is("APPROVED"))
                .body("type", is("AUTO"))
                .body("content", is("# Auto Plan"));
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
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Draft"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.APPROVED, PlanType.SEMI_MANUAL, "# Final"))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("status", is("APPROVED"))
                .body("type", is("SEMI_MANUAL"))
                .body("content", is("# Final"));
    }

    @Test
    void testDeletePlan() {
        int taskId = createTaskAndReturnId();

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# To delete"))
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
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# First"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.APPROVED, PlanType.AUTO, "# Second"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(409);
    }

    // Plan requirement & git tests

    private int createGit(String url) {
        GitDto gitDto = new GitDto();
        gitDto.url = url;
        return given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private static PlanDto planDto(PlanStatus status, PlanType type, String content, String requirement, Long gitId) {
        PlanDto dto = new PlanDto();
        dto.status = status;
        dto.type = type;
        dto.content = content;
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

        PlanDto plan = planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Content", "Must support Java 25", null);

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

        PlanDto plan = planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Content", null, (long) gitId);

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

        PlanDto plan = planDto(PlanStatus.APPROVED, PlanType.SEMI_MANUAL, "# Both", "Requirement text", (long) gitId);

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
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Draft", "Old requirement", null))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Draft", "New requirement", null))
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
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Plan", null, (long) gitId1))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId1));

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Plan", null, (long) gitId2))
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
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Plan", null, (long) gitId))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git.id", is(gitId));

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Plan", null, null))
                .when().put("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(200)
                .body("git", nullValue());
    }

    @Test
    void testCreatePlanWithInvalidGitId() {
        int taskId = createTaskAndReturnId();

        PlanDto plan = planDto(PlanStatus.IN_PROGRESS, PlanType.MANUAL, "# Plan", null, 999999L);

        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("git", nullValue());
    }

    @Test
    void testTaskListIncludesPlanFields() {
        int taskId = createTaskAndReturnId();
        int gitId = createGit("https://github.com/test/plan-list");

        given()
                .contentType(ContentType.JSON)
                .body(planDto(PlanStatus.APPROVED, PlanType.MANUAL, "# Listed", "List requirement", (long) gitId))
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
