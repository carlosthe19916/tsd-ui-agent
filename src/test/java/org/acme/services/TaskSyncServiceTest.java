package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.git.GitManager;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.acme.services.workspace.filesystem.FilesystemWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class TaskSyncServiceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    GitManager gitManager;

    @InjectMock
    WorkspaceManagerResolver workspaceManagerResolver;

    @InjectMock
    ChangeRequestService changeRequestService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
        doNothing().when(changeRequestService).triggerChangeRequest(any());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
        WorkspaceManager mockManager = org.mockito.Mockito.mock(WorkspaceManager.class);
        when(mockManager.provision(any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(mockManager.provision(any(), any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(workspaceManagerResolver.resolve((org.acme.models.jpa.entity.ExecutionMode) any()))
                .thenReturn(mockManager);
        when(workspaceManagerResolver.resolve((org.acme.services.workspace.ExecutionMode) any()))
                .thenReturn(mockManager);
    }

    private static ExternalIssue issue(String externalId, String title, String description) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
        ext.description = description;
        ext.externalStatus = TaskStatus.OPEN.name();
        ext.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        ext.updatedAt = Instant.parse("2025-06-01T00:00:00Z");
        return ext;
    }

    private static ExternalIssue issue(String externalId, String title) {
        return issue(externalId, title, null);
    }

    private int createProjectAndSync(List<ExternalIssue> issues) {
        when(syncManager.fetchIssues(any())).thenReturn(issues);

        CredentialDto cred = new CredentialDto();
        cred.name = "sync-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto dto = new ProjectDto();
        dto.name = "sync-project-" + System.nanoTime();
        dto.apiUrl = "https://github.com/owner/repo";
        dto.type = SourceType.GITHUB;
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

    @Test
    void testSyncCreatesTasksFromIssues() {
        int projectId = createProjectAndSync(List.of(
                issue("sync-1", "First issue"),
                issue("sync-2", "Second issue")
        ));

        given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(2));
    }

    @Test
    void testSyncUpdatesExistingTasks() {
        List<ExternalIssue> initialIssues = List.of(
                issue("update-1", "Original title")
        );
        int projectId = createProjectAndSync(initialIssues);

        // Re-sync with updated title
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("update-1", "Updated title")
        ));

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", projectId)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .queryParam("projectId", projectId)
                        .queryParam("filterText", "Updated title")
                        .when().get("/tasks")
                        .then()
                        .body("meta.count", is(1))
                        .body("data[0].title", is("Updated title"))
        );
    }

    @Test
    void testSyncRemovesOrphanedTasks() {
        int projectId = createProjectAndSync(List.of(
                issue("orphan-1", "Keep this"),
                issue("orphan-2", "Remove this")
        ));

        // Re-sync with only one issue
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("orphan-1", "Keep this")
        ));

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", projectId)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .queryParam("projectId", projectId)
                        .when().get("/tasks")
                        .then()
                        .body("meta.count", is(1))
                        .body("data[0].externalId", is("orphan-1"))
        );
    }

    @Test
    void testSyncSetsProjectStatusToSynchronized() {
        int projectId = createProjectAndSync(List.of(
                issue("status-1", "Status test")
        ));

        given()
                .when().get("/projects/{id}", projectId)
                .then()
                .statusCode(200)
                .body("syncStatus", is("SYNCHRONIZED"))
                .body("lastSyncAt", notNullValue());
    }

    @Test
    void testSyncWithLabels() {
        ExternalIssue issueWithLabels = issue("label-1", "Labeled issue");
        issueWithLabels.labels = List.of("bug", "priority-high");

        int projectId = createProjectAndSync(List.of(issueWithLabels));

        given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("data[0].labels", hasItems("bug", "priority-high"));
    }

    @Test
    void testSyncIdempotency() {
        List<ExternalIssue> issues = List.of(
                issue("idempotent-1", "Idempotent task")
        );

        int projectId = createProjectAndSync(issues);

        // Sync again with same data
        when(syncManager.fetchIssues(any())).thenReturn(issues);

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", projectId)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .queryParam("projectId", projectId)
                        .when().get("/tasks")
                        .then()
                        .body("meta.count", is(1))
        );
    }
}
