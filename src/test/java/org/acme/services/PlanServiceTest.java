package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.PlanDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class PlanServiceTest {

    @InjectMock
    SyncManager syncManager;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
    }

    private int createProjectAndSync(List<ExternalIssue> issues) {
        when(syncManager.fetchIssues(any())).thenReturn(issues);

        CredentialDto cred = new CredentialDto();
        cred.name = "disc-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto dto = new ProjectDto();
        dto.name = "disc-project-" + System.nanoTime();
        dto.apiUrl = "https://github.com/owner/repo";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        int id = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON).when().post("/projects/{id}/sync", id).then().statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given().when().get("/projects/{id}", id).then()
                        .body("syncStatus", is("SYNCHRONIZED")));

        return id;
    }

    private static ExternalIssue issue(String externalId, String title) {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = externalId;
        ext.url = "https://github.com/owner/repo/issues/" + externalId;
        ext.title = title;
        ext.description = "Test description";
        ext.externalStatus = TaskStatus.OPEN.name();
        ext.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        ext.updatedAt = Instant.parse("2025-06-01T00:00:00Z");
        return ext;
    }

    private int getTaskId(int projectId) {
        return given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");
    }

    @Test
    void testDiscoveryCompletesSuccessfully() {
        int projectId = createProjectAndSync(List.of(issue("disc-1", "Discovery task")));
        int taskId = getTaskId(projectId);

        // Create plan
        PlanDto plan = new PlanDto();
        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("isRequirementInProgress", is(false));

        // Trigger enrichment via separate endpoint
        given()
                .when().post("/tasks/{taskId}/plan/enrich-requirement", taskId)
                .then()
                .statusCode(202)
                .body("isRequirementInProgress", is(true));

        // Poll for completion — requirement is set from task title
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/tasks/{taskId}/plan", taskId)
                        .then()
                        .body("isRequirementInProgress", is(false))
                        .body("requirement", is("Discovery task\n\nTest description")));
    }

    @Test
    void testEnrichmentCompletesWithTitleOnly() {
        int projectId = createProjectAndSync(List.of(issue("disc-err-1", "Title only task")));
        int taskId = getTaskId(projectId);

        // Create plan first
        PlanDto plan = new PlanDto();
        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("isRequirementInProgress", is(false));

        given()
                .when().post("/tasks/{taskId}/plan/enrich-requirement", taskId)
                .then()
                .statusCode(202)
                .body("isRequirementInProgress", is(true));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/tasks/{taskId}/plan", taskId)
                        .then()
                        .body("isRequirementInProgress", is(false))
                        .body("requirement", is("Title only task\n\nTest description")));
    }

    @Test
    void testEnrichmentSetsRequirementFromTitleAndDescription() {
        int projectId = createProjectAndSync(List.of(issue("disc-conf-1", "Conflict task")));
        int taskId = getTaskId(projectId);

        // Create plan
        PlanDto plan = new PlanDto();
        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201)
                .body("isRequirementInProgress", is(false));

        // Trigger enrichment via separate endpoint
        given()
                .when().post("/tasks/{taskId}/plan/enrich-requirement", taskId)
                .then()
                .statusCode(202)
                .body("isRequirementInProgress", is(true));

        // Wait for enrichment to complete — requirement set from title + description
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given()
                        .when().get("/tasks/{taskId}/plan", taskId)
                        .then()
                        .body("isRequirementInProgress", is(false))
                        .body("requirement", is("Conflict task\n\nTest description")));
    }
}
