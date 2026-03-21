package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.acme.dto.WorkspaceDto;
import org.acme.services.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class WorkspaceResourceTest {

    @InjectMock
    WorkspaceManager workspaceManager;

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
        return given()
                .contentType(ContentType.JSON)
                .body(new WorkspaceDto())
                .when().post("/gits/{gitId}/workspaces", gitId)
                .then()
                .statusCode(201)
                .extract().path("id");
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
                .body("isCloneInProgress", is(false))
                .body("git.url", is("https://github.com/ws/create.git"));
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
