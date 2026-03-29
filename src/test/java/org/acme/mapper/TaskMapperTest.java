package org.acme.mapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.dto.TaskDto;
import org.acme.models.jpa.entity.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TaskMapperTest {

    @Inject
    TaskMapper taskMapper;

    @Test
    @Transactional
    void testToDtoBasicFields() {
        TaskEntity entity = new TaskEntity();
        entity.id = 1L;
        entity.externalId = "ext-123";
        entity.url = "https://github.com/test/repo/issues/1";
        entity.title = "Test Task";
        entity.description = "A description";
        entity.status = TaskStatus.OPEN;
        entity.externalStatus = "open";
        entity.type = SourceType.GITHUB;
        entity.createdAt = Instant.parse("2025-01-01T00:00:00Z");
        entity.updatedAt = Instant.parse("2025-06-01T00:00:00Z");

        TaskDto dto = taskMapper.toDto(entity);

        assertEquals(1L, dto.id);
        assertEquals("ext-123", dto.externalId);
        assertEquals("https://github.com/test/repo/issues/1", dto.url);
        assertEquals("Test Task", dto.title);
        assertEquals("A description", dto.description);
        assertEquals(TaskStatus.OPEN, dto.status);
        assertEquals("open", dto.externalStatus);
        assertEquals(SourceType.GITHUB, dto.type);
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), dto.createdAt);
        assertEquals(Instant.parse("2025-06-01T00:00:00Z"), dto.updatedAt);
    }

    @Test
    @Transactional
    void testToDtoWithLabels() {
        TaskEntity entity = new TaskEntity();
        entity.id = 2L;
        entity.externalId = "ext-labels";
        entity.title = "Labeled";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.GITHUB;
        entity.labels = "bug,enhancement,priority-high";

        TaskDto dto = taskMapper.toDto(entity);

        assertNotNull(dto.labels);
        assertEquals(3, dto.labels.size());
        assertEquals(List.of("bug", "enhancement", "priority-high"), dto.labels);
    }

    @Test
    @Transactional
    void testToDtoWithNullLabels() {
        TaskEntity entity = new TaskEntity();
        entity.id = 3L;
        entity.externalId = "ext-no-labels";
        entity.title = "No Labels";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.MANUAL;
        entity.labels = null;

        TaskDto dto = taskMapper.toDto(entity);

        assertNotNull(dto.labels);
        assertTrue(dto.labels.isEmpty());
    }

    @Test
    @Transactional
    void testToDtoWithNullProject() {
        TaskEntity entity = new TaskEntity();
        entity.id = 4L;
        entity.externalId = "ext-no-proj";
        entity.title = "No Project";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.MANUAL;
        entity.project = null;

        TaskDto dto = taskMapper.toDto(entity);

        assertNull(dto.project);
    }

    @Test
    @Transactional
    void testToDtoWithNullPlan() {
        TaskEntity entity = new TaskEntity();
        entity.id = 5L;
        entity.externalId = "ext-no-plan";
        entity.title = "No Plan";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.MANUAL;
        entity.plan = null;

        TaskDto dto = taskMapper.toDto(entity);

        assertNull(dto.plan);
    }

    @Test
    @Transactional
    void testToDtoWithNullWorkspace() {
        TaskEntity entity = new TaskEntity();
        entity.id = 6L;
        entity.externalId = "ext-no-ws";
        entity.title = "No Workspace";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.MANUAL;
        entity.workspace = null;

        TaskDto dto = taskMapper.toDto(entity);

        assertNull(dto.workspace);
    }

    @Test
    @Transactional
    void testToDtoWithSingleLabel() {
        TaskEntity entity = new TaskEntity();
        entity.id = 7L;
        entity.externalId = "ext-single-label";
        entity.title = "Single Label";
        entity.status = TaskStatus.OPEN;
        entity.type = SourceType.GITHUB;
        entity.labels = "only-one";

        TaskDto dto = taskMapper.toDto(entity);

        assertEquals(1, dto.labels.size());
        assertEquals("only-one", dto.labels.get(0));
    }

    @Test
    @Transactional
    void testToDtoMapsAllStatuses() {
        for (TaskStatus status : TaskStatus.values()) {
            TaskEntity entity = new TaskEntity();
            entity.id = 100L + status.ordinal();
            entity.externalId = "ext-status-" + status.name();
            entity.title = "Status " + status.name();
            entity.status = status;
            entity.type = SourceType.MANUAL;

            TaskDto dto = taskMapper.toDto(entity);
            assertEquals(status, dto.status);
        }
    }
}
