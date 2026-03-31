package org.acme.services.changerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullRequestTemplateServiceTest {

    PullRequestTemplateService service;

    static final String TASK_TITLE = "Fix login bug";
    static final String TASK_URL = "https://github.com/owner/repo/issues/42";
    static final String REQUIREMENT = "Users cannot log in when using SSO.";

    @BeforeEach
    void setUp() {
        service = new PullRequestTemplateService();
    }

    @Test
    void testRenderWithDescriptionSection() {
        String template = "## Description\n\n## Checklist\n\n- [ ] Tests pass";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(result.contains("## Description"));
        assertTrue(result.contains(REQUIREMENT));
        assertTrue(result.contains("## Checklist"));
        assertTrue(result.contains("- [ ] Tests pass"));
    }

    @Test
    void testRenderWithRelatedIssuesSection() {
        String template = "## Description\n\n## Related Issues\n\n";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(result.contains("## Related Issues"));
        assertTrue(result.contains("Fixes: " + TASK_URL));
        assertTrue(result.contains(REQUIREMENT));
    }

    @Test
    void testRenderWithBothSections() {
        String template = "## Description\n\n## Related Issues\n\n## Checklist\n\n- [ ] Done";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(result.contains(REQUIREMENT));
        assertTrue(result.contains("Fixes: " + TASK_URL));
        assertTrue(result.contains("- [ ] Done"));
    }

    @Test
    void testRenderWithNoRecognizedSections() {
        String template = "## Custom Section\n\nSome custom content\n\n## Another\n\nMore content";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(result.startsWith("Fixes: " + TASK_URL));
        assertTrue(result.contains(REQUIREMENT));
        assertTrue(result.contains("---"));
        assertTrue(result.contains("## Custom Section"));
        assertTrue(result.contains("Some custom content"));
    }

    @Test
    void testRenderWithNullRequirement() {
        String template = "## Description\n\n## Related Issues\n\n";
        String result = service.render(template, TASK_TITLE, TASK_URL, null);

        assertTrue(result.contains("## Description"));
        assertTrue(result.contains("Fixes: " + TASK_URL));
    }

    @Test
    void testRenderWithNullTaskUrl() {
        String template = "## Description\n\n## Related Issues\n\n";
        String result = service.render(template, TASK_TITLE, null, REQUIREMENT);

        assertTrue(result.contains("## Description"));
        assertTrue(result.contains(REQUIREMENT));
        // Related Issues section should not contain "Fixes:" when URL is null
        assertTrue(!result.contains("Fixes:"));
    }

    @Test
    void testRenderStripsHtmlComments() {
        String template = "## Description\n\n<!-- Describe your changes here -->\n\n## Checklist\n\n- [ ] Tests";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(!result.contains("<!--"));
        assertTrue(!result.contains("-->"));
        assertTrue(result.contains(REQUIREMENT));
    }

    @Test
    void testRenderWithEmptyTemplate() {
        String result = service.render("", TASK_TITLE, TASK_URL, REQUIREMENT);

        assertEquals("Fixes: " + TASK_URL + "\n\n" + REQUIREMENT, result);
    }

    @Test
    void testRenderWithNullTemplate() {
        String result = service.render(null, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertEquals("Fixes: " + TASK_URL + "\n\n" + REQUIREMENT, result);
    }

    @Test
    void testRenderWithSummarySection() {
        String template = "## Summary\n\n## Testing\n\n- [ ] Unit tests added";
        String result = service.render(template, TASK_TITLE, TASK_URL, REQUIREMENT);

        assertTrue(result.contains("## Summary"));
        assertTrue(result.contains(REQUIREMENT));
        assertTrue(result.contains("## Testing"));
    }

    @Test
    void testBuildFallbackWithBothValues() {
        String result = PullRequestTemplateService.buildFallback(TASK_URL, REQUIREMENT);
        assertEquals("Fixes: " + TASK_URL + "\n\n" + REQUIREMENT, result);
    }

    @Test
    void testBuildFallbackWithNullUrl() {
        String result = PullRequestTemplateService.buildFallback(null, REQUIREMENT);
        assertEquals(REQUIREMENT, result);
    }

    @Test
    void testBuildFallbackWithNullRequirement() {
        String result = PullRequestTemplateService.buildFallback(TASK_URL, null);
        assertEquals("Fixes: " + TASK_URL + "\n\n", result);
    }
}
