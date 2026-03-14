package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.acme.services.git.GitManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class GitResourceTest {

    @InjectMock
    GitManager gitManager;

    @BeforeEach
    void setup() {
        when(gitManager.cloneRepository(anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.addWorktree(anyString(), anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/trees/my-worktree");
        doNothing().when(gitManager).removeWorktree(anyString(), anyString());
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
    }

    private static GitDto git(String url) {
        GitDto dto = new GitDto();
        dto.url = url;
        return dto;
    }

    private int createGit(String url) {
        return given()
                .contentType(ContentType.JSON)
                .body(git(url))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    void testCreateGit() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/test/repo.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is("https://github.com/test/repo.git"));
    }

    @Test
    void testCreateGitValidationFails() {
        GitDto dto = new GitDto();
        // missing required url

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/gits")
                .then()
                .statusCode(400);
    }

    @Test
    void testListGits() {
        createGit("https://github.com/list/repo.git");

        given()
                .when().get("/gits")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetGit() {
        int id = createGit("https://github.com/get/repo.git");

        given()
                .when().get("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("url", is("https://github.com/get/repo.git"));
    }

    @Test
    void testGetGitNotFound() {
        given()
                .when().get("/gits/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdateGit() {
        int id = createGit("https://github.com/before/repo.git");

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/after/repo.git"))
                .when().put("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("url", is("https://github.com/after/repo.git"));
    }

    @Test
    void testUpdateGitNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/nope/repo.git"))
                .when().put("/gits/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteGit() {
        int id = createGit("https://github.com/delete/repo.git");

        given()
                .when().delete("/gits/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/gits/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteGitNotFound() {
        given()
                .when().delete("/gits/{id}", 9999)
                .then()
                .statusCode(404);
    }
}
