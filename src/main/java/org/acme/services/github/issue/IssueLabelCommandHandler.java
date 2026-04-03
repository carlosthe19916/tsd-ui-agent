package org.acme.services.github.issue;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.annotations.Command;
import io.quarkiverse.githubapp.command.airline.Permission;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPermissionType;

import java.io.IOException;
import java.util.List;

public class IssueLabelCommandHandler {

    private static final Logger LOG = Logger.getLogger(IssueLabelCommandHandler.class);

    static void addLabel(GHEventPayload.IssueComment payload, String prefix, List<String> values,
            LabelConfig labelConfig) throws IOException {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            String label = prefix + "/" + value.trim();
            if (labelConfig.findByName(label) != null) {
                payload.getIssue().addLabels(label);
                LOG.infof("Issue #%d: added label %s", payload.getIssue().getNumber(), label);
            } else {
                LOG.warnf("Issue #%d: unknown label '%s'", payload.getIssue().getNumber(), label);
            }
        }
    }

    static void removeLabel(GHEventPayload.IssueComment payload, String prefix, List<String> values)
            throws IOException {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            String label = prefix + "/" + value.trim();
            try {
                payload.getIssue().removeLabel(label);
                LOG.infof("Issue #%d: removed label %s", payload.getIssue().getNumber(), label);
            } catch (IOException e) {
                LOG.debugf("Issue #%d: could not remove label %s: %s",
                        payload.getIssue().getNumber(), label, e.getMessage());
            }
        }
    }

    // /kind <value>
    @Cli(name = "/kind", defaultCommand = KindCli.KindCmd.class)
    public static class KindCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "kind")
        @Permission(GHPermissionType.WRITE)
        public static class KindCmd implements Commands {
            @Arguments
            List<String> values;
            @Inject
            LabelConfig labelConfig;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                addLabel(payload, "kind", values, labelConfig);
            }
        }
    }

    // /remove-kind <value>
    @Cli(name = "/remove-kind", defaultCommand = RemoveKindCli.RemoveKindCmd.class)
    public static class RemoveKindCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "remove-kind")
        @Permission(GHPermissionType.WRITE)
        public static class RemoveKindCmd implements Commands {
            @Arguments
            List<String> values;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                removeLabel(payload, "kind", values);
            }
        }
    }

    // /priority <value>
    @Cli(name = "/priority", defaultCommand = PriorityCli.PriorityCmd.class)
    public static class PriorityCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "priority")
        @Permission(GHPermissionType.WRITE)
        public static class PriorityCmd implements Commands {
            @Arguments
            List<String> values;
            @Inject
            LabelConfig labelConfig;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                addLabel(payload, "priority", values, labelConfig);
            }
        }
    }

    // /remove-priority <value>
    @Cli(name = "/remove-priority", defaultCommand = RemovePriorityCli.RemovePriorityCmd.class)
    public static class RemovePriorityCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "remove-priority")
        @Permission(GHPermissionType.WRITE)
        public static class RemovePriorityCmd implements Commands {
            @Arguments
            List<String> values;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                removeLabel(payload, "priority", values);
            }
        }
    }

    // /triage <value>
    @Cli(name = "/triage", defaultCommand = TriageCli.TriageCmd.class)
    public static class TriageCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "triage")
        @Permission(GHPermissionType.WRITE)
        public static class TriageCmd implements Commands {
            @Arguments
            List<String> values;
            @Inject
            LabelConfig labelConfig;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                addLabel(payload, "triage", values, labelConfig);
            }
        }
    }

    // /remove-triage <value>
    @Cli(name = "/remove-triage", defaultCommand = RemoveTriageCli.RemoveTriageCmd.class)
    public static class RemoveTriageCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "remove-triage")
        @Permission(GHPermissionType.WRITE)
        public static class RemoveTriageCmd implements Commands {
            @Arguments
            List<String> values;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                removeLabel(payload, "triage", values);
            }
        }
    }

    // /area <value>
    @Cli(name = "/area", defaultCommand = AreaCli.AreaCmd.class)
    public static class AreaCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "area")
        @Permission(GHPermissionType.WRITE)
        public static class AreaCmd implements Commands {
            @Arguments
            List<String> values;
            @Inject
            LabelConfig labelConfig;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                addLabel(payload, "area", values, labelConfig);
            }
        }
    }

    // /remove-area <value>
    @Cli(name = "/remove-area", defaultCommand = RemoveAreaCli.RemoveAreaCmd.class)
    public static class RemoveAreaCli {
        interface Commands {
            void run(GHEventPayload.IssueComment payload) throws IOException;
        }

        @Command(name = "remove-area")
        @Permission(GHPermissionType.WRITE)
        public static class RemoveAreaCmd implements Commands {
            @Arguments
            List<String> values;

            @Override
            public void run(GHEventPayload.IssueComment payload) throws IOException {
                removeLabel(payload, "area", values);
            }
        }
    }
}
