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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class TaskResourceTest {

    @InjectMock
    SyncManager syncManager;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
    }

    private int createProjectAndSync(SourceType type, List<ExternalIssue> issues) {
        when(syncManager.fetchIssues(any())).thenReturn(issues);

        GitDto git = new GitDto();
        git.url = "https://github.com/test/repo";

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
        dto.git = git;
        dto.credentialId = (long) credId;

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

    private static ExternalIssue issue(String externalId, String title, TaskStatus status,
                                       String assignee, String priority) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
        ext.status = status;
        ext.assignee = assignee;
        ext.priority = priority;
        ext.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        ext.updatedAt = Instant.parse("2025-06-01T00:00:00Z");
        return ext;
    }

    @Test
    void testListAll() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("list-1", "List task", TaskStatus.OPEN, null, null)
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
                issue("pag-1", "Pag A", TaskStatus.OPEN, null, null),
                issue("pag-2", "Pag B", TaskStatus.OPEN, null, null),
                issue("pag-3", "Pag C", TaskStatus.OPEN, null, null)
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
                issue("fp-1", "Filter proj", TaskStatus.OPEN, null, null)
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
                issue("fs-1", "Status open", TaskStatus.OPEN, null, null),
                issue("fs-2", "Status closed", TaskStatus.CLOSED, null, null)
        ));

        given()
                .queryParam("filterText", "Status")
                .queryParam("status", "OPEN")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1))
                .body("data[0].status", is("OPEN"));
    }

    @Test
    void testFilterByType() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("ft-1", "Type github task", TaskStatus.OPEN, null, null)
        ));

        given()
                .queryParam("filterText", "Type github task")
                .queryParam("type", "GITHUB")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1));
    }

    @Test
    void testFilterByAssignee() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("fa-1", "Assignee task", TaskStatus.OPEN, "alice", null),
                issue("fa-2", "Unassigned task", TaskStatus.OPEN, null, null)
        ));

        given()
                .queryParam("filterText", "task")
                .queryParam("assignee", "alice")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1))
                .body("data[0].assignee", is("alice"));
    }

    @Test
    void testFilterByPriority() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("fpri-1", "Priority high task", TaskStatus.OPEN, null, "high"),
                issue("fpri-2", "Priority low task", TaskStatus.OPEN, null, "low")
        ));

        given()
                .queryParam("filterText", "Priority")
                .queryParam("priority", "high")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1))
                .body("data[0].priority", is("high"));
    }

    @Test
    void testFilterText() {
        createProjectAndSync(SourceType.GITHUB, List.of(
                issue("ftext-1", "UniqueSearchTerm Alpha", TaskStatus.OPEN, null, null),
                issue("ftext-2", "Something else", TaskStatus.OPEN, null, null)
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
                issue("sort-1", "Zebra sort task", TaskStatus.OPEN, null, null),
                issue("sort-2", "Alpha sort task", TaskStatus.OPEN, null, null)
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
                issue("combo-1", "Combo match", TaskStatus.OPEN, "bob", "medium"),
                issue("combo-2", "Combo match", TaskStatus.CLOSED, "bob", "medium"),
                issue("combo-3", "Combo match", TaskStatus.OPEN, "carol", "medium")
        ));

        given()
                .queryParam("filterText", "Combo match")
                .queryParam("status", "OPEN")
                .queryParam("assignee", "bob")
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1));
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
                issue("ctx-" + System.nanoTime(), "Context test task", TaskStatus.OPEN, null, null)
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
}
