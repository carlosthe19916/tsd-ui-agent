package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class CredentialResourceTest {

    private static CredentialDto credential(String name, String token) {
        CredentialDto dto = new CredentialDto();
        dto.name = name;
        dto.token = token;
        return dto;
    }

    @Test
    void testCreateCredential() {
        given()
                .contentType(ContentType.JSON)
                .body(credential("my-cred", "my-token"))
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is("my-cred"))
                .body("token", nullValue());
    }

    @Test
    void testCreateCredentialValidationFails() {
        CredentialDto dto = new CredentialDto();
        // missing required fields

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/credentials")
                .then()
                .statusCode(400);
    }

    @Test
    void testListCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body(credential("list-cred", "list-token"))
                .when().post("/credentials")
                .then()
                .statusCode(201);

        given()
                .when().get("/credentials")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGetCredential() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(credential("get-cred", "get-token"))
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/credentials/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("name", is("get-cred"))
                .body("token", nullValue());
    }

    @Test
    void testGetCredentialNotFound() {
        given()
                .when().get("/credentials/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdateCredential() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(credential("before-update", "old-token"))
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(credential("after-update", "new-token"))
                .when().put("/credentials/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("name", is("after-update"))
                .body("token", nullValue());
    }

    @Test
    void testUpdateCredentialNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(credential("no-such-cred", "token"))
                .when().put("/credentials/{id}", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteCredential() {
        int id = given()
                .contentType(ContentType.JSON)
                .body(credential("to-delete", "delete-token"))
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/credentials/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/credentials/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void testDeleteCredentialNotFound() {
        given()
                .when().delete("/credentials/{id}", 9999)
                .then()
                .statusCode(404);
    }
}
