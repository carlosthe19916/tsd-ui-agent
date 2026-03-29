package org.acme.mapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectMapperTest {

    @Inject
    ProjectMapper projectMapper;

    @Test
    @Transactional
    void testToDtoBasicFields() {
        CredentialEntity credential = new CredentialEntity();
        credential.id = 1L;
        credential.name = "my-cred";
        credential.token = "secret-token";

        ProjectEntity entity = new ProjectEntity();
        entity.id = 1L;
        entity.name = "My Project";
        entity.apiUrl = "https://github.com/test/repo";
        entity.query = "is:open";
        entity.type = SourceType.GITHUB;
        entity.credential = credential;
        entity.syncStatus = SyncStatus.SYNCHRONIZED;
        entity.lastSyncAt = Instant.parse("2025-06-15T12:00:00Z");

        ProjectDto dto = projectMapper.toDto(entity);

        assertEquals(1L, dto.id);
        assertEquals("My Project", dto.name);
        assertEquals("https://github.com/test/repo", dto.apiUrl);
        assertEquals("is:open", dto.query);
        assertEquals(SourceType.GITHUB, dto.type);
        assertEquals(SyncStatus.SYNCHRONIZED, dto.syncStatus);
        assertEquals(Instant.parse("2025-06-15T12:00:00Z"), dto.lastSyncAt);
    }

    @Test
    @Transactional
    void testToDtoCredentialTokenNotExposed() {
        CredentialEntity credential = new CredentialEntity();
        credential.id = 2L;
        credential.name = "token-cred";
        credential.token = "super-secret-token-value";

        ProjectEntity entity = new ProjectEntity();
        entity.id = 2L;
        entity.name = "Token Test";
        entity.apiUrl = "https://example.com";
        entity.type = SourceType.JIRA;
        entity.credential = credential;

        ProjectDto dto = projectMapper.toDto(entity);

        assertNotNull(dto.credential);
        assertEquals(2L, dto.credential.id);
        assertEquals("token-cred", dto.credential.name);
        assertNull(dto.credential.token);
    }

    @Test
    @Transactional
    void testToDtoWithJiraType() {
        CredentialEntity credential = new CredentialEntity();
        credential.id = 3L;
        credential.name = "jira-cred";
        credential.token = "jira-token";

        ProjectEntity entity = new ProjectEntity();
        entity.id = 3L;
        entity.name = "Jira Project";
        entity.apiUrl = "https://jira.example.com";
        entity.query = "project = TEST AND status = Open";
        entity.type = SourceType.JIRA;
        entity.credential = credential;
        entity.syncStatus = SyncStatus.NOT_SYNCHRONIZED;

        ProjectDto dto = projectMapper.toDto(entity);

        assertEquals(SourceType.JIRA, dto.type);
        assertEquals("project = TEST AND status = Open", dto.query);
        assertEquals(SyncStatus.NOT_SYNCHRONIZED, dto.syncStatus);
    }

    @Test
    @Transactional
    void testToDtoWithNullQuery() {
        CredentialEntity credential = new CredentialEntity();
        credential.id = 4L;
        credential.name = "null-query-cred";
        credential.token = "token";

        ProjectEntity entity = new ProjectEntity();
        entity.id = 4L;
        entity.name = "No Query";
        entity.apiUrl = "https://github.com/test/repo";
        entity.type = SourceType.GITHUB;
        entity.credential = credential;

        ProjectDto dto = projectMapper.toDto(entity);

        assertNull(dto.query);
    }

    @Test
    @Transactional
    void testToDtoWithNullLastSyncAt() {
        CredentialEntity credential = new CredentialEntity();
        credential.id = 5L;
        credential.name = "no-sync-cred";
        credential.token = "token";

        ProjectEntity entity = new ProjectEntity();
        entity.id = 5L;
        entity.name = "Never Synced";
        entity.apiUrl = "https://github.com/test/repo";
        entity.type = SourceType.GITHUB;
        entity.credential = credential;
        entity.lastSyncAt = null;

        ProjectDto dto = projectMapper.toDto(entity);

        assertNull(dto.lastSyncAt);
    }

    @Test
    @Transactional
    void testToDtoWithAllSyncStatuses() {
        for (SyncStatus status : SyncStatus.values()) {
            CredentialEntity credential = new CredentialEntity();
            credential.id = 10L + status.ordinal();
            credential.name = "sync-" + status.name();
            credential.token = "token";

            ProjectEntity entity = new ProjectEntity();
            entity.id = 10L + status.ordinal();
            entity.name = "Sync " + status.name();
            entity.apiUrl = "https://example.com";
            entity.type = SourceType.GITHUB;
            entity.credential = credential;
            entity.syncStatus = status;

            ProjectDto dto = projectMapper.toDto(entity);
            assertEquals(status, dto.syncStatus);
        }
    }
}
