package org.acme.services;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.acme.dto.CredentialDto;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@QuarkusTest
class ProjectServiceTest {

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

    private int createCredential(String name) {
        CredentialDto cred = new CredentialDto();
        cred.name = name;
        cred.token = "test-token-" + name;
        return given()
                .contentType(ContentType.JSON)
                .body(cred)
                .when().post("/credentials")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    void testCreateProjectWithValidCredential() {
        int credId = createCredential("proj-svc-cred-" + System.nanoTime());

        ProjectDto dto = new ProjectDto();
        dto.name = "Test Project " + System.nanoTime();
        dto.apiUrl = "https://github.com/test/repo";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is(dto.name))
                .body("apiUrl", is("https://github.com/test/repo"))
                .body("type", is("GITHUB"))
                .body("credential.id", is(credId))
                .body("syncStatus", is("NOT_SYNCHRONIZED"));
    }

    @Test
    void testCreateProjectWithInvalidCredential() {
        ProjectDto dto = new ProjectDto();
        dto.name = "Bad cred project";
        dto.apiUrl = "https://github.com/test/repo";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = 999999L;
        dto.credential = credDto;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdateProject() {
        int credId = createCredential("proj-update-cred-" + System.nanoTime());

        ProjectDto dto = new ProjectDto();
        dto.name = "Original Project " + System.nanoTime();
        dto.apiUrl = "https://github.com/test/original";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        int projectId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        dto.name = "Updated Project " + System.nanoTime();
        dto.apiUrl = "https://github.com/test/updated";
        dto.type = SourceType.GITHUB;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/projects/{id}", projectId)
                .then()
                .statusCode(200)
                .body("name", is(dto.name))
                .body("apiUrl", is("https://github.com/test/updated"))
                .body("type", is("GITHUB"));
    }

    @Test
    void testDeleteProject() {
        int credId = createCredential("proj-del-cred-" + System.nanoTime());

        ProjectDto dto = new ProjectDto();
        dto.name = "Delete Project " + System.nanoTime();
        dto.apiUrl = "https://github.com/test/delete";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        int projectId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().delete("/projects/{id}", projectId)
                .then()
                .statusCode(204);

        given()
                .when().get("/projects/{id}", projectId)
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateProjectWithJiraType() {
        int credId = createCredential("jira-cred-" + System.nanoTime());

        ProjectDto dto = new ProjectDto();
        dto.name = "Jira Project " + System.nanoTime();
        dto.apiUrl = "https://jira.example.com";
        dto.query = "project = TEST";
        dto.type = SourceType.JIRA;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .body("type", is("JIRA"))
                .body("query", is("project = TEST"));
    }

    @Test
    void testCreateProjectCredentialTokenNotExposed() {
        int credId = createCredential("token-check-cred-" + System.nanoTime());

        ProjectDto dto = new ProjectDto();
        dto.name = "Token Check Project " + System.nanoTime();
        dto.apiUrl = "https://github.com/test/token";
        dto.type = SourceType.GITHUB;
        CredentialDto credDto = new CredentialDto();
        credDto.id = (long) credId;
        dto.credential = credDto;

        int projectId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/projects")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when().get("/projects/{id}", projectId)
                .then()
                .statusCode(200)
                .body("credential.id", is(credId))
                .body("credential.token", org.hamcrest.CoreMatchers.nullValue());
    }
}
