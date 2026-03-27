package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.PlanDto;
import org.acme.dto.ProjectDto;
import org.acme.dto.TaskDto;
import org.acme.dto.WorkspaceDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.ai.RequirementSummarizerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class ChangeRequestServiceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    RequirementSummarizerService aiService;

    @InjectMock
    GitManager gitManager;

    @InjectMock
    WorkspaceManagerResolver workspaceManagerResolver;

    @InjectMock
    ChangeRequestService changeRequestService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
        when(syncManager.fetchComments(any())).thenReturn(List.of());
        when(syncManager.fetchLabels(any())).thenReturn(List.of());
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
        WorkspaceManager mockManager = org.mockito.Mockito.mock(WorkspaceManager.class);
        when(mockManager.provision(any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(mockManager.provision(any(), any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(mockManager.getWorkspace(anyString()))
                .thenAnswer(invocation -> java.util.Optional.of(new FilesystemWorkspace(invocation.getArgument(0))));
        when(workspaceManagerResolver.resolve((org.acme.models.jpa.entity.ExecutionMode) any()))
                .thenReturn(mockManager);
        when(workspaceManagerResolver.resolve((org.acme.services.workspace.ExecutionMode) any()))
                .thenReturn(mockManager);
        doNothing().when(changeRequestService).triggerChangeRequest(any());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
    }

    private int createProjectAndSync(List<ExternalIssue> issues) {
        when(syncManager.fetchIssues(any())).thenReturn(issues);

        CredentialDto cred = new CredentialDto();
        cred.name = "cr-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto dto = new ProjectDto();
        dto.name = "cr-project-" + System.nanoTime();
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

        given().contentType(ContentType.JSON).when().post("/projects/{id}/sync", id).then().statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given().when().get("/projects/{id}", id).then()
                        .body("syncStatus", is("SYNCHRONIZED")));

        return id;
    }

    private static ExternalIssue issue(String externalId, String title) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
        ext.description = "Test description";
        ext.externalStatus = TaskStatus.OPEN.name();
        ext.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        ext.updatedAt = Instant.parse("2025-06-01T00:00:00Z");
        return ext;
    }

    private int getTaskId(int projectId) {
        return given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");
    }

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

    private int createWorkspace(int gitId) {
        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;
        return given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private static PlanDto planDto(String plan) {
        PlanDto dto = new PlanDto();
        dto.plan = plan;
        return dto;
    }

    private void setTaskWorkspace(int taskId, int workspaceId) {
        TaskDto taskDto = new TaskDto();
        WorkspaceDto ws = new WorkspaceDto();
        ws.id = (long) workspaceId;
        taskDto.workspace = ws;
        given()
                .contentType(ContentType.JSON)
                .body(taskDto)
                .when().patch("/tasks/{taskId}", taskId)
                .then()
                .statusCode(200);
    }

    @Test
    void testChangeRequestRequiresExecutionCompletion() {
        int projectId = createProjectAndSync(List.of(issue("cr-noexec-1", "CR no exec task")));
        int taskId = getTaskId(projectId);
        int credId = createCredential("cr-noexec-cred");
        int gitId = createGit("https://github.com/test/cr-noexec", (long) credId);
        int wsId = createWorkspace(gitId);

        setTaskWorkspace(taskId, wsId);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Test plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        // Change request should fail because execution hasn't completed
        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testChangeRequestRequiresWorkspace() {
        int projectId = createProjectAndSync(List.of(issue("cr-nows-1", "CR no workspace task")));
        int taskId = getTaskId(projectId);

        PlanDto plan = new PlanDto();
        plan.plan = "# Test plan";

        given()
                .contentType(ContentType.JSON)
                .body(plan)
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
    void testChangeRequestRequiresCredential() {
        int projectId = createProjectAndSync(List.of(issue("cr-notoken-1", "CR no token task")));
        int taskId = getTaskId(projectId);
        int gitId = createGit("https://github.com/test/cr-notoken");
        int wsId = createWorkspace(gitId);

        setTaskWorkspace(taskId, wsId);

        given()
                .contentType(ContentType.JSON)
                .body(planDto("# Test plan"))
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        // Change request should fail because git has no token
        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(400);
    }

    @Test
    void testChangeRequestRequiresPlan() {
        int projectId = createProjectAndSync(List.of(issue("cr-noplan-1", "CR no plan task")));
        int taskId = getTaskId(projectId);

        given()
                .contentType(ContentType.JSON)
                .when().post("/tasks/{taskId}/plan/change-request", taskId)
                .then()
                .statusCode(404);
    }
}
