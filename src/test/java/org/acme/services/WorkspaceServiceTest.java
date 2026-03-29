package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.services.git.GitManager;
import org.acme.services.sync.SyncManager;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.acme.services.workspace.filesystem.FilesystemWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorkspaceServiceTest {

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
        when(mockManager.getWorkspace(anyString()))
                .thenAnswer(invocation -> java.util.Optional.of(new FilesystemWorkspace(invocation.getArgument(0))));
        when(workspaceManagerResolver.resolve((org.acme.models.jpa.entity.ExecutionMode) any()))
                .thenReturn(mockManager);
        when(workspaceManagerResolver.resolve((org.acme.services.workspace.ExecutionMode) any()))
                .thenReturn(mockManager);
    }

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

    @Test
    void testCreateWorkspace() {
        int gitId = createGit("https://github.com/test/ws-svc-" + System.nanoTime());

        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;

        given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("git.id", is(gitId))
                .body("isProvisioningInProgress", is(true))
                .body("executionMode", is("FILESYSTEM"));
    }

    @Test
    void testCreateWorkspaceWithDockerMode() {
        int gitId = createGit("https://github.com/test/ws-docker-" + System.nanoTime());

        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;
        wsDto.executionMode = "DOCKER";

        given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .body("executionMode", is("DOCKER"));
    }

    @Test
    void testDeleteWorkspace() {
        int gitId = createGit("https://github.com/test/ws-del-" + System.nanoTime());

        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;

        int wsId = given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/workspaces/{id}", wsId)
                .then()
                .statusCode(204);
    }

    @Test
    void testListWorkspacesByGitId() {
        int gitId = createGit("https://github.com/test/ws-list-" + System.nanoTime());

        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;

        given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201);

        given()
                .queryParam("gitId", gitId)
                .when().get("/workspaces")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testCreateWorkspaceWithInvalidGit() {
        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = 999999L;

        given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(404);
    }

    @Test
    void testWorkspaceDefaultsToFilesystem() {
        int gitId = createGit("https://github.com/test/ws-default-" + System.nanoTime());

        WorkspaceDto wsDto = new WorkspaceDto();
        wsDto.git = new GitDto();
        wsDto.git.id = (long) gitId;
        // no executionMode set

        given()
                .contentType(ContentType.JSON)
                .body(wsDto)
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .body("executionMode", is("FILESYSTEM"));
    }
}
