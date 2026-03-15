package org.acme.services.discovery.source;

import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.TaskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JiraMcpSourceTest {

    @Test
    void testSupportsJira() {
        JiraMcpSource source = new JiraMcpSource();
        source.enabled = true;

        TaskEntity jiraTask = new TaskEntity();
        jiraTask.type = SourceType.JIRA;
        assertTrue(source.supports(jiraTask));

        TaskEntity githubTask = new TaskEntity();
        githubTask.type = SourceType.GITHUB;
        assertFalse(source.supports(githubTask));
    }

    @Test
    void testSupportsDisabledReturnsFalse() {
        JiraMcpSource source = new JiraMcpSource();
        source.enabled = false;

        TaskEntity task = new TaskEntity();
        task.type = SourceType.JIRA;
        assertFalse(source.supports(task));
    }

    @Test
    void testPriority() {
        JiraMcpSource source = new JiraMcpSource();
        assertEquals(10, source.priority());
    }
}
