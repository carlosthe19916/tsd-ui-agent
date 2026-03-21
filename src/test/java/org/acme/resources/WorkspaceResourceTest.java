package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.services.git.GitManager;
import org.acme.services.workspace.WorkspaceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorkspaceResourceTest {

    @InjectMock
    GitManager gitManager;

    @InjectMock
    WorkspaceManager workspaceManager;

    @BeforeEach
    void setup() {
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
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

    private int createWorkspace(int gitId) {
        WorkspaceDto dto = new WorkspaceDto();
        int id = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .extract().path("id");
        awaitCloneCompletion(gitId, id);
        return id;
    }

    private void awaitCloneCompletion(int gitId, int id) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                        .then()
                        .statusCode(200)
                        .body("isCloneInProgress", is(false))
        );
    }

    @Test
    void testCreateWorkspace() {
        int gitId = createGit("https://github.com/ws/create.git");

        given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("isCloneInProgress", is(true))
                .body("git.url", is("https://github.com/ws/create.git"));
    }

    @Test
    void testCreateWorkspaceCloneCompletes() {
        int gitId = createGit("https://github.com/ws/clone-complete.git");

        int id = given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .body("isCloneInProgress", is(true))
                .extract().path("id");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                        .then()
                        .statusCode(200)
                        .body("isCloneInProgress", is(false))
                        .body("localPath", notNullValue())
        );
    }

    @Test
    void testCreateWorkspaceCloneError() {
        when(gitManager.cloneRepository("https://github.com/ws/clone-error.git", ""))
                .thenThrow(new RuntimeException("clone failed"));

        int gitId = createGit("https://github.com/ws/clone-error.git");

        int id = given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .extract().path("id");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                        .then()
                        .statusCode(200)
                        .body("isCloneInProgress", is(false))
                        .body("cloneError", notNullValue())
        );
    }

    @Test
    void testListWorkspacesByGitId() {
        int gitId = createGit("https://github.com/ws/filter.git");
        createWorkspace(gitId);

        given()
                .when().get("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        given()
                .when().get("/gits/{gitId}/workspaces", 999999)
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    void testGetWorkspace() {
        int gitId = createGit("https://github.com/ws/get.git");
        int id = createWorkspace(gitId);

        given()
                .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("git.url", is("https://github.com/ws/get.git"));
    }

    @Test
    void testGetWorkspaceNotFound() {
        int gitId = createGit("https://github.com/ws/get-nf.git");

        given()
                .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteWorkspace() {
        int gitId = createGit("https://github.com/ws/delete.git");
        int id = createWorkspace(gitId);

        given()
                .when().delete("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                .then()
                .statusCode(204);

        given()
                .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteWorkspaceNotFound() {
        int gitId = createGit("https://github.com/ws/delete-nf.git");

        given()
                .when().delete("/gits/{gitId}/workspaces/{wsId}", gitId, 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateWorkspaceWithNoCloneError() {
        int gitId = createGit("https://github.com/ws/no-error.git");

        int id = createWorkspace(gitId);

        given()
                .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                .then()
                .statusCode(200)
                .body("cloneError", nullValue());
    }
}
