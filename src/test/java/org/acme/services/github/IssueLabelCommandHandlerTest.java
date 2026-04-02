package org.acme.services.github;

import io.quarkiverse.githubapp.testing.GitHubAppTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHUser;
import org.mockito.Mockito;

import java.io.IOException;

import static io.quarkiverse.githubapp.testing.GitHubAppTesting.given;

@QuarkusTest
@GitHubAppTest
class IssueLabelCommandHandlerTest {

    @Test
    void kindCommandAddsLabel() throws IOException {
        given().github(mocks -> {
                    Mockito.when(mocks.repository("test-org/test-repo")
                            .hasPermission(Mockito.any(GHUser.class), Mockito.eq(GHPermissionType.WRITE)))
                            .thenReturn(true);
                })
                .when().payloadFromClasspath("/github/issue-comment-kind-bug.json")
                .event(GHEvent.ISSUE_COMMENT)
                .then().github(mocks -> {
                    Mockito.verify(mocks.issue(100))
                            .addLabels("kind/bug");
                });
    }
}
