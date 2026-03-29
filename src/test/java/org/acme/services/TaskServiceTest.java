package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.TaskDto;
import org.acme.services.git.GitManager;
import org.acme.services.sync.SyncManager;
import org.acme.services.workspace.WorkspaceManager;
import org.acme.services.workspace.WorkspaceManagerResolver;
import org.acme.services.workspace.filesystem.FilesystemWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class TaskServiceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    GitManager gitManager;

    @InjectMock
    WorkspaceManagerResolver workspaceManagerResolver;

    @InjectMock
    ChangeRequestService changeRequestService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
        when(gitManager.cloneRepository(anyString(), anyString()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        when(gitManager.cloneRepository(anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("/tmp/tsd-agent-ui-test/repo/default");
        doNothing().when(gitManager).setRemoteUrl(anyString(), anyString());
        doNothing().when(gitManager).addForkRemote(anyString(), anyString());
        doNothing().when(changeRequestService).triggerChangeRequest(any());
        doNothing().when(gitManager).deleteClonedDirectory(anyString());
        WorkspaceManager mockManager = org.mockito.Mockito.mock(WorkspaceManager.class);
        when(mockManager.provision(any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(mockManager.provision(any(), any()))
                .thenAnswer(invocation -> new FilesystemWorkspace("/tmp/tsd-agent-ui-test/repo/trees/plan-worktree"));
        when(workspaceManagerResolver.resolve((org.acme.models.jpa.entity.ExecutionMode) any()))
                .thenReturn(mockManager);
        when(workspaceManagerResolver.resolve((org.acme.services.workspace.ExecutionMode) any()))
                .thenReturn(mockManager);
    }

    @Test
    void testCreateManualTaskSetsCorrectDefaults() {
        TaskDto dto = new TaskDto();
        dto.title = "Service test task " + System.nanoTime();
        dto.description = "A detailed description";

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", is(dto.title))
                .body("description", is("A detailed description"))
                .body("status", is("OPEN"))
                .body("type", is("MANUAL"))
                .body("externalId", startsWith("manual-"))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue())
                .body("project", nullValue());
    }

    @Test
    void testCreateManualTaskWithNullDescription() {
        TaskDto dto = new TaskDto();
        dto.title = "No description task " + System.nanoTime();

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .body("description", nullValue())
                .body("status", is("OPEN"))
                .body("type", is("MANUAL"));
    }

    @Test
    void testCreateMultipleManualTasksHaveUniqueExternalIds() {
        TaskDto dto1 = new TaskDto();
        dto1.title = "Unique ID test 1";

        TaskDto dto2 = new TaskDto();
        dto2.title = "Unique ID test 2";

        String extId1 = given()
                .contentType(ContentType.JSON)
                .body(dto1)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("externalId");

        String extId2 = given()
                .contentType(ContentType.JSON)
                .body(dto2)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("externalId");

        org.junit.jupiter.api.Assertions.assertNotEquals(extId1, extId2);
    }

    @Test
    void testCreatedTaskAppearsInListWithCorrectFields() {
        String uniqueTitle = "Listable task " + System.nanoTime();

        TaskDto dto = new TaskDto();
        dto.title = uniqueTitle;
        dto.description = "Should appear in list";

        int taskId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .queryParam("filterText", uniqueTitle)
                .when().get("/tasks")
                .then()
                .statusCode(200)
                .body("meta.count", is(1))
                .body("data[0].id", is(taskId))
                .body("data[0].title", is(uniqueTitle))
                .body("data[0].description", is("Should appear in list"))
                .body("data[0].type", is("MANUAL"))
                .body("data[0].status", is("OPEN"));
    }

    @Test
    void testManualTaskHasNoProject() {
        TaskDto dto = new TaskDto();
        dto.title = "No project task " + System.nanoTime();

        int taskId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/tasks/{id}", taskId)
                .then()
                .statusCode(200)
                .body("project", nullValue());
    }

    @Test
    void testManualTaskTimestampsAreConsistent() {
        TaskDto dto = new TaskDto();
        dto.title = "Timestamp task";

        String createdAt = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("createdAt");

        String updatedAt = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("updatedAt");

        org.junit.jupiter.api.Assertions.assertNotNull(createdAt);
        org.junit.jupiter.api.Assertions.assertNotNull(updatedAt);
    }
}
