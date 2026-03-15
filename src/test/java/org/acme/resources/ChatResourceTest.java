package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Multi;
import org.acme.dto.CredentialDto;
import org.acme.dto.PlanDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.PlanStatus;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.services.ai.ChatAiService;
import org.acme.services.sync.ExternalIssue;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class ChatResourceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    ChatAiService chatAiService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
    }

    private int createTaskWithPlan() {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = "chat-" + System.nanoTime();
        ext.url = "https://github.com/owner/repo/issues/1";
        ext.title = "Chat test task";
        ext.externalStatus = TaskStatus.OPEN.name();
        ext.createdAt = Instant.now();
        ext.updatedAt = Instant.now();

        when(syncManager.fetchIssues(any())).thenReturn(List.of(ext));

        CredentialDto cred = new CredentialDto();
        cred.name = "chat-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto project = new ProjectDto();
        project.name = "chat-project-" + System.nanoTime();
        project.apiUrl = "https://github.com/owner/repo";
        project.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        project.credential = credDto;

        int projectId = given()
                .contentType(ContentType.JSON)
                .body(project)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON).when().post("/projects/{id}/sync", projectId).then().statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given().when().get("/projects/{id}", projectId).then()
                        .body("syncStatus", is("SYNCHRONIZED")));

        int taskId = given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");

        PlanDto plan = new PlanDto();
        plan.status = PlanStatus.IN_PROGRESS;
        given()
                .contentType(ContentType.JSON)
                .body(plan)
                .when().post("/tasks/{taskId}/plan", taskId)
                .then()
                .statusCode(201);

        return taskId;
    }

    @Test
    void testChatReturnsSSE() {
        when(chatAiService.chat(anyLong(), anyString()))
                .thenReturn(Multi.createFrom().items("Hello", " world"));

        int taskId = createTaskWithPlan();

        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Summarize the requirement\"}")
                .when().post("/tasks/{taskId}/plan/chat", taskId)
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"));
    }

    @Test
    void testChatReturns404WithoutPlan() {
        ExternalIssue ext = new ExternalIssue();
        ext.externalId = "chat-noplan-" + System.nanoTime();
        ext.url = "https://github.com/owner/repo/issues/2";
        ext.title = "No plan task";
        ext.externalStatus = TaskStatus.OPEN.name();
        ext.createdAt = Instant.now();
        ext.updatedAt = Instant.now();

        when(syncManager.fetchIssues(any())).thenReturn(List.of(ext));

        CredentialDto cred = new CredentialDto();
        cred.name = "chat-noplan-cred-" + System.nanoTime();
        cred.token = "test-token";

        int credId = given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");

        ProjectDto project = new ProjectDto();
        project.name = "chat-noplan-project-" + System.nanoTime();
        project.apiUrl = "https://github.com/owner/repo";
        project.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        project.credential = credDto;

        int projectId = given()
                .contentType(ContentType.JSON)
                .body(project)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON).when().post("/projects/{id}/sync", projectId).then().statusCode(202);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                given().when().get("/projects/{id}", projectId).then()
                        .body("syncStatus", is("SYNCHRONIZED")));

        int taskId = given()
                .queryParam("projectId", projectId)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .extract().path("data[0].id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/tasks/{taskId}/plan/chat", taskId)
                .then()
                .statusCode(404);
    }
}
