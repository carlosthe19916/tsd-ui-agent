package org.acme.services.github;

import io.quarkiverse.githubapp.testing.GitHubAppMockito;
import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.mockito.Mockito;

import java.io.IOException;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;

@QuarkusTest
@GitHubAppTest
class IssueLabelReconcilerTest {

    @Test
    void issueOpenedAddsNeedsLabelsAndTriageComment() throws IOException {
        given().github(mocks -> {
                    Mockito.doReturn(GitHubAppMockito.mockPagedIterable())
                            .when(mocks.issue(100)).listComments();
                })
                .when().payloadFromClasspath("/github/issue-opened.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    Mockito.verify(mocks.issue(100))
                            .addLabels("needs-triage", "needs-kind", "needs-priority");
                    Mockito.verify(mocks.issue(100))
                            .comment(Mockito.contains("This issue is currently awaiting triage"));
                });
    }

    @Test
    void issueLabeledWithKindRemovesNeedsKind() throws IOException {
        given().github(mocks -> {
                    Mockito.doReturn(GitHubAppMockito.mockPagedIterable())
                            .when(mocks.issue(100)).listComments();
                })
                .when().payloadFromClasspath("/github/issue-labeled-kind-bug.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    Mockito.verify(mocks.issue(100))
                            .removeLabel("needs-kind");
                });
    }

    @Test
    void issueLabeledWithTriageAcceptedRemovesNeedsTriage() throws IOException {
        given().github(mocks -> {
                    Mockito.doReturn(GitHubAppMockito.mockPagedIterable())
                            .when(mocks.issue(100)).listComments();
                })
                .when().payloadFromClasspath("/github/issue-labeled-triage-accepted.json")
                .event(GHEvent.ISSUES)
                .then().github(mocks -> {
                    Mockito.verify(mocks.issue(100))
                            .removeLabel("needs-triage");
                });
    }
}
