package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.TestConnectionDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.services.sync.SyncException;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
class ProjectTestConnectionTest {

    @InjectMock
    SyncManager syncManager;

    @BeforeEach
    void setup() {
        doNothing().when(syncManager).testConnection(any(), anyString(), any(), anyString());
    }

    private int createCredential() {
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

    @Test
    void testConnectionSuccess() {
        int credId = createCredential();

        TestConnectionDto dto = new TestConnectionDto();
        dto.type = SourceType.GITHUB;
        dto.apiUrl = "https://github.com/owner/repo";
        dto.credentialId = (long) credId;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects/test-connection")
                .then()
                .statusCode(200)
                .body("status", is("ok"));
    }

    @Test
    void testConnectionFailure() {
        doThrow(new SyncException("Invalid token"))
                .when(syncManager).testConnection(any(), anyString(), any(), anyString());

        int credId = createCredential();

        TestConnectionDto dto = new TestConnectionDto();
        dto.type = SourceType.GITHUB;
        dto.apiUrl = "https://github.com/owner/repo";
        dto.credentialId = (long) credId;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects/test-connection")
                .then()
                .statusCode(422)
                .body("status", is("error"))
                .body("message", is("Invalid token"));
    }

    @Test
    void testConnectionCredentialNotFound() {
        TestConnectionDto dto = new TestConnectionDto();
        dto.type = SourceType.GITHUB;
        dto.apiUrl = "https://github.com/owner/repo";
        dto.credentialId = 99999L;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects/test-connection")
                .then()
                .statusCode(404);
    }

    @Test
    void testConnectionValidationMissingFields() {
        TestConnectionDto dto = new TestConnectionDto();
        // Missing all required fields

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects/test-connection")
                .then()
                .statusCode(400);
    }
}
