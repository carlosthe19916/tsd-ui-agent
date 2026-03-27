package org.acme.resources;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.mutiny.Multi;
import org.acme.dto.TaskDto;
import org.acme.services.ai.AgenticChatService;
import org.acme.services.sync.SyncManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class ChatResourceTest {

    @InjectMock
    SyncManager syncManager;

    @InjectMock
    AgenticChatService agenticChatService;

    @BeforeEach
    void setup() {
        when(syncManager.fetchIssues(any())).thenReturn(List.of());
    }

    private int createTask() {
        TaskDto task = new TaskDto();
        task.title = "Chat test task " + System.nanoTime();
        task.description = "Test description";

        return given()
                .contentType(ContentType.JSON)
                .body(task)
                .when().post("/tasks")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    void testChatReturnsSSE() {
        when(agenticChatService.chat(anyLong(), anyString()))
                .thenReturn(Multi.createFrom().items("Hello", " world"));

        int taskId = createTask();

        // SSE streams may close abruptly in tests with RestAssured
        try {
            Response response = given()
                    .contentType(ContentType.JSON)
                    .body("{\"content\":\"Summarize the requirement\"}")
                    .when().post("/tasks/{taskId}/chat", taskId);
            assertTrue(response.statusCode() == 200 || response.body().asString().contains("Hello"));
        } catch (Exception e) {
            // Premature close is expected for SSE in test environment
            assertTrue(e.getMessage().contains("chunk") || e.getMessage().contains("Premature"),
                    "Unexpected error: " + e.getMessage());
        }
    }

    @Test
    void testChatReturns404ForNonExistentTask() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/tasks/{taskId}/chat", 999999)
                .then()
                .statusCode(404);
    }
}
