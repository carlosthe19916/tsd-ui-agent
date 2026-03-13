package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.GitPlatform;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.GitHubIssueFetcher;
import org.acme.services.sync.JiraIssueFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class TaskResourceTest {

    @InjectMock
    GitHubIssueFetcher gitHubIssueFetcher;

    @InjectMock
    JiraIssueFetcher jiraIssueFetcher;

    @BeforeEach
    void setup() {
        when(gitHubIssueFetcher.getType()).thenReturn(SourceType.GITHUB);
        when(jiraIssueFetcher.getType()).thenReturn(SourceType.JIRA);
        when(gitHubIssueFetcher.fetchIssues(any())).thenReturn(List.of());
        when(jiraIssueFetcher.fetchIssues(any())).thenReturn(List.of());
    }

    private int createProjectAndSync(SourceType type, List<ExternalIssue> issues) {
        if (type == SourceType.GITHUB) {
            when(gitHubIssueFetcher.fetchIssues(any())).thenReturn(issues);
        } else {
            when(jiraIssueFetcher.fetchIssues(any())).thenReturn(issues);
        }

        GitDto git = new GitDto();
        git.name = "task-git-" + System.nanoTime();
        git.url = "https://github.com/test/repo";
        git.platform = GitPlatform.GITHUB;

        CredentialDto cred = new CredentialDto();
        cred.name = "task-cred-" + System.nanoTime();
        cred.type = type;
        cred.token = "test-token";

        ProjectDto dto = new ProjectDto();
        dto.name = "task-project-" + System.nanoTime();
        dto.url = "https://github.com/owner/repo";
        dto.type = type;
        dto.git = git;
        dto.credential = cred;

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
}
