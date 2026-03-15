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
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.addWorktree(anyString(), anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/trees/my-worktree");
        doNothing().when(gitManager).removeWorktree(anyString(), anyString());
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
        doNothing().when(gitManager).setForkRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).removeForkRemote(anyString());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
    }

    private static GitDto git(String url) {
        return git(url, null);
    }

    private static GitDto git(String url, String branch) {
        return git(url, branch, null);
    }

    private static GitDto git(String url, String branch, String forkUrl) {
        GitDto dto = new GitDto();
        dto.url = url;
        dto.branch = branch;
        dto.forkUrl = forkUrl;
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
    void testCreateGitWithBranch() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/test/repo.git", "develop"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is("https://github.com/test/repo.git"))
                .body("branch", is("develop"));
    }

    @Test
    void testCreateGitWithoutBranch() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/test/repo2.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is("https://github.com/test/repo2.git"))
                .body("branch", is(""));
    }

    @Test
    void testDuplicateUrlAndNoBranch() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/no-branch.git"))
                .when().post("/gits")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/no-branch.git"))
                .when().post("/gits")
                .then()
                .statusCode(409);
    }

    @Test
    void testDuplicateUrlAndSameBranch() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/same-branch.git", "main"))
                .when().post("/gits")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/same-branch.git", "main"))
                .when().post("/gits")
                .then()
                .statusCode(409);
    }

    @Test
    void testSameUrlDifferentBranches() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/diff-branch.git", "main"))
                .when().post("/gits")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/dup/diff-branch.git", "develop"))
                .when().post("/gits")
                .then()
                .statusCode(201);
    }

    @Test
    void testCreateGitWithForkUrl() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/fork/repo.git", null, "https://github.com/myfork/repo.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is("https://github.com/fork/repo.git"))
                .body("forkUrl", is("https://github.com/myfork/repo.git"));
    }

    @Test
    void testCreateGitWithoutForkUrl() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/nofork/repo.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("forkUrl", org.hamcrest.CoreMatchers.nullValue());
    }

    @Test
    void testUpdateGitAddForkUrl() {
        int id = createGit("https://github.com/addfork/repo.git");

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/addfork/repo.git", null, "https://github.com/myfork/addfork.git"))
                .when().put("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("forkUrl", is("https://github.com/myfork/addfork.git"));
    }

    @Test
    void testUpdateGitChangeForkUrl() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/changefork/repo.git", null, "https://github.com/old/fork.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/changefork/repo.git", null, "https://github.com/new/fork.git"))
                .when().put("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("forkUrl", is("https://github.com/new/fork.git"));
    }

    @Test
    void testUpdateGitRemoveForkUrl() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/removefork/repo.git", null, "https://github.com/myfork/remove.git"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/removefork/repo.git", null, null))
                .when().put("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("forkUrl", org.hamcrest.CoreMatchers.nullValue());
    }

    @Test
    void testDeleteGitNotFound() {
        given()
                .when().delete("/gits/{id}", 9999)
                .then()
                .statusCode(404);
    }
}
