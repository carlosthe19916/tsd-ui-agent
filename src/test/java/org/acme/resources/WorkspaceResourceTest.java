package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.filesystem.FilesystemWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorkspaceResourceTest {

    @InjectMock
    WorkspaceManager workspaceManager;

    @BeforeEach
    void setup() {
        when(workspaceManager.provision(any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/ws-test"));
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
        int id = given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .extract().path("id");
        awaitProvisioningCompletion(gitId, id);
        return id;
    }

    private void awaitProvisioningCompletion(int gitId, int id) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                        .then()
                        .statusCode(200)
                        .body("isProvisioningInProgress", is(false))
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
                .body("isProvisioningInProgress", is(true))
                .body("git.url", is("https://github.com/ws/create.git"));
    }

    @Test
    void testCreateWorkspaceProvisioningCompletes() {
        int gitId = createGit("https://github.com/ws/provision-complete.git");

        int id = given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .body("isProvisioningInProgress", is(true))
                .extract().path("id");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                        .then()
                        .statusCode(200)
                        .body("isProvisioningInProgress", is(false))
                        .body("workspaceId", notNullValue())
        );
    }

    @Test
    void testCreateWorkspaceProvisioningError() {
        when(workspaceManager.provision(any()))
                .thenThrow(new RuntimeException("provision failed"));

        int gitId = createGit("https://github.com/ws/provision-error.git");

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
                        .body("isProvisioningInProgress", is(false))
                        .body("provisioningError", notNullValue())
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
    void testCreateWorkspaceWithNoProvisioningError() {
        int gitId = createGit("https://github.com/ws/no-error.git");
        int id = createWorkspace(gitId);

        given()
                .when().get("/gits/{gitId}/workspaces/{wsId}", gitId, id)
                .then()
                .statusCode(200)
                .body("provisioningError", nullValue());
    }
}
