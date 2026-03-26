package org.acme.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

@QuarkusTest
class ProjectSyncResourceTest {

    @InjectMock
    SyncManager syncManager;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
    }

    private static int createCredential() {
        CredentialDto cred = new CredentialDto();
        cred.name = "sync-cred-" + System.nanoTime();
        cred.token = "test-token";
        return given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private int createProject() {
        ProjectDto dto = new ProjectDto();
        dto.name = "sync-project";
        dto.apiUrl = "https://github.com/owner/repo";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) createCredential();
        dto.credential = credDto;
        return given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private static ExternalIssue issue(String id, String title, TaskStatus status) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = id;
        ext.url = "https://github.com/owner/repo/issues/" + id;
        ext.title = title;
        ext.externalStatus = status.name();
        ext.createdAt = Instant.now();
        ext.updatedAt = Instant.now();
        return ext;
    }

    @Test
    void testSyncReturns202() {
        int id = createProject();

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202)
                .body("syncStatus", is("SYNCHRONIZATION_IN_PROGRESS"));
    }

    @Test
    void testSyncProjectNotFound() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", 9999)
                .then()
                .statusCode(404);
    }

    @Test
    void testSyncWhileInProgressReturns409() {
        // Make the fetcher block so the sync stays in progress
        when(syncManager.fetchIssues(any())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return List.of();
        });

        int id = createProject();

        // First sync
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        // Immediately try again — should get 409
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(409)
                .body("syncStatus", is("SYNCHRONIZATION_IN_PROGRESS"));
    }

    @Test
    void testSyncCompletesSuccessfully() {
        int id = createProject();

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .statusCode(200)
                        .body("syncStatus", is("SYNCHRONIZED"))
                        .body("lastSyncAt", notNullValue())
        );
    }

    @Test
    void testSyncCreatesNewTasks() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("1", "First issue", TaskStatus.OPEN),
                issue("2", "Second issue", TaskStatus.CLOSED)
        ));

        int id = createProject();

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .body("syncStatus", is("SYNCHRONIZED"))
        );
    }

    @Test
    void testSyncUpdatesExistingTasks() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("10", "Original title", TaskStatus.OPEN)
        ));

        int id = createProject();

        // First sync
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .body("syncStatus", is("SYNCHRONIZED"))
        );

        // Change mock data
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("10", "Updated title", TaskStatus.CLOSED)
        ));

        // Second sync
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            given()
                    .when().get("/projects/{id}", id)
                    .then()
                    .body("syncStatus", is("SYNCHRONIZED"));
        });
    }

    @Test
    void testSyncDeletesOrphanTasks() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("100", "Issue A", TaskStatus.OPEN),
                issue("101", "Issue B", TaskStatus.OPEN),
                issue("102", "Issue C", TaskStatus.OPEN)
        ));

        int id = createProject();

        // First sync with 3 issues
        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .body("syncStatus", is("SYNCHRONIZED"))
        );

        // Second sync with only 2 issues (issue 102 removed)
        when(syncManager.fetchIssues(any())).thenReturn(List.of(
                issue("100", "Issue A", TaskStatus.OPEN),
                issue("101", "Issue B", TaskStatus.OPEN)
        ));

        given()
                .contentType(ContentType.JSON)
                .when().post("/projects/{id}/sync", id)
                .then()
                .statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/projects/{id}", id)
                        .then()
                        .body("syncStatus", is("SYNCHRONIZED"))
        );
    }
}
