package org.acme.services.github.issue.implement;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.annotations.Command;
import io.quarkiverse.githubapp.command.airline.Permission;
import jakarta.inject.Inject;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPermissionType;

import java.io.IOException;

public class ImplementCommandHandler {

    @Cli(name = "/implement", defaultCommand = ImplementCli.ImplementCmd.class)
    public static class ImplementCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "implement")
        @Permission(GHPermissionType.WRITE)
        public static class ImplementCmd implements Commands {
            @Inject
            IssueImplementationService service;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                service.implement(payload);
            }
        }
    }
}
