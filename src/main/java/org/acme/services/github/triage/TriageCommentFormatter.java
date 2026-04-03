package org.acme.services.github.triage;

public class TriageCommentFormatter {

    public static final String AI_TRIAGE_MARKER = "<!-- ai-triage-result -->";

    public static String format(TriageResult result) {
        var sb = new StringBuilder();
        sb.append(AI_TRIAGE_MARKER).append("\n");
        sb.append("## AI Triage Suggestion\n\n");

        // Triage decision
        sb.append("**Triage:** `triage/").append(result.suggestedTriage()).append("`");
        sb.append(" (").append((int) (result.confidence() * 100)).append("% confidence)").append("\n\n");

        // Duplicate info
        if (result.isDuplicate() && result.duplicateOfIssueNumber() != null) {
            sb.append("> **Possible duplicate** of #").append(result.duplicateOfIssueNumber()).append("\n\n");
        }

        // Missing information
        if (result.missingInformation() != null && !result.missingInformation().isBlank()) {
            sb.append("### Missing Information\n\n");
            sb.append(result.missingInformation()).append("\n\n");
        }

        // Bug report completeness
        if ("needs-information".equals(result.suggestedTriage())) {
            sb.append("<details>\n<summary>Bug report completeness</summary>\n\n");
            sb.append("- [").append(result.hasReproSteps() ? "x" : " ").append("] Reproduction steps\n");
            sb.append("- [").append(result.hasExpectedBehavior() ? "x" : " ").append("] Expected behavior\n");
            sb.append("- [").append(result.hasActualBehavior() ? "x" : " ").append("] Actual behavior\n");
            sb.append("\n</details>\n\n");
        }

        // Reasoning
        sb.append("<details>\n<summary>Reasoning</summary>\n\n");
        sb.append(result.reasoning()).append("\n\n");
        sb.append("</details>\n\n");

        // Footer
        sb.append("---\n");
        sb.append("*Maintainers can override with `/triage` commands.*");

        return sb.toString();
    }
}
