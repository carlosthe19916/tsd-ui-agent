package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.GitDto;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class GitResourceTest {

    private static GitDto git(String url, String branch) {
        GitDto dto = new GitDto();
        dto.url = url;
        dto.branch = branch;
        return dto;
    }

    @Test
    void testCreateGit() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/test/repo.git", "main"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", is("https://github.com/test/repo.git"))
                .body("branch", is("main"));
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
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/list/repo.git", null))
                .when().post("/gits")
                .then()
                .statusCode(201);

        given()
                .when().get("/gits")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetGit() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/get/repo.git", "develop"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("url", is("https://github.com/get/repo.git"))
                .body("branch", is("develop"));
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
        int id = given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/before/repo.git", "main"))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/after/repo.git", "develop"))
                .when().put("/gits/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("url", is("https://github.com/after/repo.git"))
                .body("branch", is("develop"));
    }

    @Test
    void testUpdateGitNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/nope/repo.git", "main"))
                .when().put("/gits/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteGit() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(git("https://github.com/delete/repo.git", null))
                .when().post("/gits")
                .then()
                .statusCode(201)
                .extract().path("id");

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
