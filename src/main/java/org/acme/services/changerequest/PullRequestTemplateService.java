package org.acme.services.changerequest;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class PullRequestTemplateService {

    private static final Set<String> DESCRIPTION_HEADINGS = Set.of(
            "description", "summary", "changes", "overview", "what"
    );

    private static final Set<String> RELATED_ISSUES_HEADINGS = Set.of(
            "related issues", "references", "linked issues", "issues", "related"
    );

    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    public String render(String template, String taskTitle, String taskUrl, String requirement) {
        if (template == null || template.isBlank()) {
            return buildFallback(taskUrl, requirement);
        }

        String cleaned = HTML_COMMENT_PATTERN.matcher(template).replaceAll("").trim();
        if (cleaned.isBlank()) {
            return buildFallback(taskUrl, requirement);
        }

        List<Section> sections = parseSections(cleaned);

        boolean hasRecognizedSection = false;
        for (Section section : sections) {
            String headingLower = section.heading.toLowerCase();
            if (DESCRIPTION_HEADINGS.contains(headingLower)) {
                hasRecognizedSection = true;
                String content = requirement != null ? requirement : "";
                section.body = content + (section.body.isBlank() ? "" : "\n\n" + section.body);
            } else if (RELATED_ISSUES_HEADINGS.contains(headingLower)) {
                hasRecognizedSection = true;
                if (taskUrl != null && !taskUrl.isBlank()) {
                    String link = "Fixes: " + taskUrl;
                    section.body = link + (section.body.isBlank() ? "" : "\n\n" + section.body);
                }
            }
        }

        if (!hasRecognizedSection) {
            String prefix = buildFallback(taskUrl, requirement);
            return prefix + "\n\n---\n\n" + cleaned;
        }

        return renderSections(sections);
    }

    public static String buildFallback(String taskUrl, String requirement) {
        String description = requirement != null ? requirement : "";
        if (taskUrl != null && !taskUrl.isBlank()) {
            description = "Fixes: " + taskUrl + "\n\n" + description;
        }
        return description;
    }

    private List<Section> parseSections(String text) {
        List<Section> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        String currentHeading = "";
        StringBuilder currentBody = new StringBuilder();
        boolean firstSection = true;

        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (!firstSection || !currentBody.isEmpty()) {
                    sections.add(new Section(currentHeading, currentBody.toString().trim()));
                }
                currentHeading = line.substring(3).trim();
                currentBody = new StringBuilder();
                firstSection = false;
            } else {
                if (!currentBody.isEmpty()) {
                    currentBody.append("\n");
                }
                currentBody.append(line);
            }
        }
        sections.add(new Section(currentHeading, currentBody.toString().trim()));
        return sections;
    }

    private String renderSections(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (Section section : sections) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            if (!section.heading.isEmpty()) {
                sb.append("## ").append(section.heading).append("\n\n");
            }
            sb.append(section.body);
        }
        return sb.toString().trim();
    }

    private static class Section {
        String heading;
        String body;

        Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }
}
