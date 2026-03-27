package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.services.workspace.WorkspaceManagerResolver;
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
    WorkspaceManagerResolver workspaceManagerResolver;

    @BeforeEach
    void setup() {
        org.acme.services.workspace.WorkspaceManager mockManager = org.mockito.Mockito.mock(org.acme.services.workspace.WorkspaceManager.class);
        when(mockManager.provision(any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/ws-test"));
        when(mockManager.provision(any(), any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/ws-test"));
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

    private WorkspaceDto workspaceDtoForGit(int gitId) {
        WorkspaceDto dto = new WorkspaceDto();
        dto.git = new GitDto();
        dto.git.id = (long) gitId;
        return dto;
    }

    private int createWorkspace(int gitId) {
        int id = given()
                .contentType(ContentType.JSON)
                .body(workspaceDtoForGit(gitId))
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .extract().path("id");
        awaitProvisioningCompletion(id);
        return id;
    }

    private void awaitProvisioningCompletion(int id) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/workspaces/{id}", id)
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
                .body(workspaceDtoForGit(gitId))
                .when().post("/workspaces")
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
                .body(workspaceDtoForGit(gitId))
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .body("isProvisioningInProgress", is(true))
                .extract().path("id");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/workspaces/{id}", id)
                        .then()
                        .statusCode(200)
                        .body("isProvisioningInProgress", is(false))
                        .body("workspaceId", notNullValue())
        );
    }

    @Test
    void testCreateWorkspaceProvisioningError() {
        org.acme.services.workspace.WorkspaceManager errorManager = org.mockito.Mockito.mock(org.acme.services.workspace.WorkspaceManager.class);
        when(errorManager.provision(any()))
                .thenThrow(new RuntimeException("provision failed"));
        when(errorManager.provision(any(), any()))
                .thenThrow(new RuntimeException("provision failed"));
        when(workspaceManagerResolver.resolve((org.acme.models.jpa.entity.ExecutionMode) any()))
                .thenReturn(errorManager);
        when(workspaceManagerResolver.resolve((org.acme.services.workspace.ExecutionMode) any()))
                .thenReturn(errorManager);

        int gitId = createGit("https://github.com/ws/provision-error.git");

        int id = given()
                .contentType(ContentType.JSON)
                .body(workspaceDtoForGit(gitId))
                .when().post("/workspaces")
                .then()
                .statusCode(201)
                .extract().path("id");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/workspaces/{id}", id)
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
                .queryParam("gitId", gitId)
                .when().get("/workspaces")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        given()
                .queryParam("gitId", 999999)
                .when().get("/workspaces")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    void testGetWorkspace() {
        int gitId = createGit("https://github.com/ws/get.git");
        int id = createWorkspace(gitId);

        given()
                .when().get("/workspaces/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("git.url", is("https://github.com/ws/get.git"));
    }

    @Test
    void testGetWorkspaceNotFound() {
        given()
                .when().get("/workspaces/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteWorkspace() {
        int gitId = createGit("https://github.com/ws/delete.git");
        int id = createWorkspace(gitId);

        given()
                .when().delete("/workspaces/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/workspaces/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteWorkspaceNotFound() {
        given()
                .when().delete("/workspaces/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateWorkspaceWithNoProvisioningError() {
        int gitId = createGit("https://github.com/ws/no-error.git");
        int id = createWorkspace(gitId);

        given()
                .when().get("/workspaces/{id}", id)
                .then()
                .statusCode(200)
                .body("provisioningError", nullValue());
    }
}
