package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class ProjectResourceTest {

    private static int createCredential() {
        CredentialDto cred = new CredentialDto();
        cred.name = "test-cred";
        cred.token = "test-token";
        return given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private static ProjectDto project(String name, String url, SourceType type) {
        ProjectDto dto = new ProjectDto();
        dto.name = name;
        dto.apiUrl = url;
        dto.type = type;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) createCredential();
        dto.credential = credDto;
        return dto;
    }

    @Test
    void testCreateProject() {
        ProjectDto dto = project("my-project", "https://example.com", SourceType.GITHUB);
        dto.query = "label=bug";

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is("my-project"))
                .body("apiUrl", is("https://example.com"))
                .body("query", is("label=bug"))
                .body("type", is("GITHUB"))
                .body("credential.id", notNullValue());
    }

    @Test
    void testCreateProjectValidationFails() {
        ProjectDto dto = new ProjectDto();

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(400);
    }

    @Test
    void testCreateProjectMissingCredentialFails() {
        ProjectDto dto = new ProjectDto();
        dto.name = "no-cred";
        dto.apiUrl = "https://example.com";
        dto.type = SourceType.GITHUB;
        // credential is null

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(400);
    }

    @Test
    void testCreateProjectNonExistentCredentialFails() {
        ProjectDto dto = new ProjectDto();
        dto.name = "bad-cred-ref";
        dto.apiUrl = "https://example.com";
        dto.type = SourceType.GITHUB;
        CredentialDto badCred = new CredentialDto();
        badCred.id = 999999L;
        dto.credential = badCred;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(404);
    }

    @Test
    void testListProjects() {
        given()
                .contentType(ContentType.JSON)
                .body(project("list-project", "https://example.com", SourceType.GITHUB))
                .when().post("/projects")
                .then()
                .statusCode(201);

        given()
                .when().get("/projects")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetProject() {
        int credId = createCredential();
        ProjectDto dto = new ProjectDto();
        dto.name = "get-project";
        dto.apiUrl = "https://example.com";
        dto.type = SourceType.GITHUB;
        CredentialDto getCred = new CredentialDto();
        getCred.id = (long) credId;
        dto.credential = getCred;

        int id = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/projects/{id}", id)
                .then()
                .statusCode(200)
                .body("name", is("get-project"))
                .body("credential.id", is(credId));
    }

    @Test
    void testGetProjectNotFound() {
        given()
                .when().get("/projects/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdateProject() {
        var createResponse = given()
                .contentType(ContentType.JSON)
                .body(project("before-update", "https://example.com", SourceType.GITHUB))
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract();

        int id = createResponse.path("id");
        int credId = createResponse.path("credential.id");

        ProjectDto updated = project("after-update", "https://updated.com", SourceType.JIRA);
        updated.query = "project=TEST";

        given()
                .contentType(ContentType.JSON)
                .body(updated)
                .when().put("/projects/{id}", id)
                .then()
                .statusCode(200)
                .body("name", is("after-update"))
                .body("apiUrl", is("https://updated.com"))
                .body("type", is("JIRA"))
                .body("credential.id", is(credId));
    }

    @Test
    void testUpdateProjectNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(project("no-such-project", "https://example.com", SourceType.GITHUB))
                .when().put("/projects/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteProject() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(project("to-delete", "https://example.com", SourceType.GITHUB))
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/projects/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/projects/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteProjectNotFound() {
        given()
                .when().delete("/projects/{id}", 9999)
                .then()
                .statusCode(404);
    }
}
