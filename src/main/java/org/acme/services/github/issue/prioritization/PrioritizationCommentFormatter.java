package org.acme.services.github.issue.prioritization;

public class PrioritizationCommentFormatter {

    public static final String AI_PRIORITIZATION_MARKER = "<!-- ai-prioritization-result -->";

    public static String format(PrioritizationResult result) {
        var sb = new StringBuilder();
        sb.append(AI_PRIORITIZATION_MARKER).append("\n");
        sb.append("## AI Priority Suggestion\n\n");

        sb.append("**Priority:** `priority/").append(result.suggestedPriority()).append("`");
        sb.append(" (").append((int) (result.confidence() * 100)).append("% confidence)").append("\n\n");

        sb.append("<details>\n<summary>Reasoning</summary>\n\n");
        sb.append(result.reasoning()).append("\n\n");
        sb.append("</details>\n\n");

        sb.append("---\n");
        sb.append("*Maintainers can override with `/priority` commands.*");

        return sb.toString();
    }
}
