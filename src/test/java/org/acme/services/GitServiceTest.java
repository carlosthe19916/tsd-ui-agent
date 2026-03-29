package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class GitServiceTest {

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

    @Test
    void testCreateGitWithUrl() {
        String url = "https://github.com/test/git-svc-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is(url))
                .body("branch", is(""))
                .body("isProvisioningInProgress", is(true));
    }

    @Test
    void testCreateGitWithBranch() {
        String url = "https://github.com/test/git-branch-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;
        gitDto.branch = "develop";

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("url", is(url))
                .body("branch", is("develop"));
    }

    @Test
    void testCreateGitWithCredential() {
        int credId = createCredential("git-svc-cred-" + System.nanoTime());
        String url = "https://github.com/test/git-cred-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        gitDto.credential = credDto;

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("credential.id", is(credId))
                .body("credential.token", nullValue());
    }

    @Test
    void testDuplicateUrlBranchConflict() {
        String url = "https://github.com/test/git-dup-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(409);
    }

    @Test
    void testUpdateGit() {
        String url = "https://github.com/test/git-update-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;

        int gitId = given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        String updatedUrl = "https://github.com/test/git-updated-" + System.nanoTime();
        GitDto updateDto = new GitDto();
        updateDto.url = updatedUrl;

        given()
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().put("/gits/{id}", gitId)
                .then()
                .statusCode(200)
                .body("url", is(updatedUrl));
    }

    @Test
    void testDeleteGit() {
        String url = "https://github.com/test/git-delete-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;

        int gitId = given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/gits/{id}", gitId)
                .then()
                .statusCode(204);

        given()
                .when().get("/gits/{id}", gitId)
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateGitWithForkUrl() {
        String url = "https://github.com/test/git-fork-" + System.nanoTime();
        GitDto gitDto = new GitDto();
        gitDto.url = url;
        gitDto.forkUrl = "https://github.com/fork/repo";

        given()
                .contentType(ContentType.JSON)
                .body(gitDto)
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("forkUrl", is("https://github.com/fork/repo"));
    }
}
