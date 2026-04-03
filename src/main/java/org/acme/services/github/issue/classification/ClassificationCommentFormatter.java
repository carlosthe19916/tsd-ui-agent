package org.acme.services.github.issue.classification;

public class ClassificationCommentFormatter {

    public static final String AI_CLASSIFICATION_MARKER = "<!-- ai-classification-result -->";

    public static String format(ClassificationResult result) {
        var sb = new StringBuilder();
        sb.append(AI_CLASSIFICATION_MARKER).append("\n");
        sb.append("## AI Classification Suggestion\n\n");

        sb.append("**Kind:** `kind/").append(result.suggestedKind()).append("`");
        sb.append(" (").append((int) (result.confidence() * 100)).append("% confidence)").append("\n\n");

        sb.append("<details>\n<summary>Reasoning</summary>\n\n");
        sb.append(result.reasoning()).append("\n\n");
        sb.append("</details>\n\n");

        sb.append("---\n");
        sb.append("*Maintainers can override with `/kind` commands.*");

        return sb.toString();
    }
}
