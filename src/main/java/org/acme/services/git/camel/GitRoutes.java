package org.acme.services.git.camel;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.services.git.GitException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.util.List;

@ApplicationScoped
public class GitRoutes extends RouteBuilder {

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    throw new GitException("Git operation failed: " + cause.getMessage(), cause);
                });

        from("direct:git-clone")
                .process(exchange -> {
                    String localPath = exchange.getIn().getHeader("localPath", String.class);
                    String remotePath = exchange.getIn().getHeader("remotePath", String.class);
                    exchange.getIn().setHeader("CamelExecCommandArgs", List.of("clone", remotePath, localPath));
                })
                .to("exec:git");

        from("direct:git-worktree-add")
                .process(exchange -> {
                    String worktreeDir = exchange.getIn().getHeader("worktreeDir", String.class);
                    String sourceBranch = exchange.getIn().getHeader("sourceBranch", String.class);
                    exchange.getIn().setHeader("CamelExecCommandArgs", List.of("worktree", "add", worktreeDir, sourceBranch));
                    exchange.getIn().setHeader("CamelExecCommandWorkingDir", exchange.getIn().getHeader("workingDir", String.class));
                })
                .to("exec:git");

        from("direct:git-remote-set-url")
                .process(exchange -> {
                    String remotePath = exchange.getIn().getHeader("remotePath", String.class);
                    exchange.getIn().setHeader("CamelExecCommandArgs", List.of("remote", "set-url", "origin", remotePath));
                    exchange.getIn().setHeader("CamelExecCommandWorkingDir", exchange.getIn().getHeader("workingDir", String.class));
                })
                .to("exec:git");

        from("direct:git-worktree-remove")
                .process(exchange -> {
                    String worktreeDir = exchange.getIn().getHeader("worktreeDir", String.class);
                    exchange.getIn().setHeader("CamelExecCommandArgs", List.of("worktree", "remove", worktreeDir));
                    exchange.getIn().setHeader("CamelExecCommandWorkingDir", exchange.getIn().getHeader("workingDir", String.class));
                })
                .to("exec:git");
    }
}
