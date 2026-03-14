package org.acme.services.sync.camel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.sync.SyncException;
import org.acme.services.sync.camel.processor.GitHubIssue;
import org.acme.services.sync.camel.processor.GitHubIssueMapper;
import org.acme.services.sync.camel.processor.GitHubPaginationProcessor;
import org.acme.services.sync.camel.processor.GitHubSearchResult;
import org.acme.services.sync.camel.processor.GitHubUrlProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.util.ArrayList;

@ApplicationScoped
public class GitHubRoutes extends RouteBuilder {

    @Inject
    GitHubUrlProcessor urlProcessor;

    @Inject
    GitHubPaginationProcessor paginationProcessor;

    @Inject
    GitHubIssueMapper issueMapper;

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    throw new SyncException("GitHub sync failed: " + cause.getMessage(), cause);
                });

        from("direct:github-fetch-issues")
                .process(urlProcessor)
                .setProperty("allIssues", ArrayList::new)
                .setProperty("hasMorePages", constant(true))
                .loopDoWhile(exchangeProperty("hasMorePages"))
                    .to("direct:http-get")
                    .choice()
                        .when(exchangeProperty("isSearch"))
                            .unmarshal().json(GitHubSearchResult.class)
                        .endChoice()
                        .otherwise()
                            .unmarshal().json(GitHubIssue[].class)
                        .endChoice()
                    .end()
                    .process(paginationProcessor)
                .end()
                .setBody(exchangeProperty("allIssues"))
                .process(issueMapper);

        from("direct:github-test-connection")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    exchange.getIn().setHeader("CamelHttpUrl", apiUrl);
                    exchange.getIn().setHeader("Authorization", "Bearer " + token);
                    exchange.getIn().setHeader("Accept", "application/vnd.github+json");
                })
                .to("direct:http-get");

        from("direct:github-test-query")
                .process(exchange -> {
                    String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
                    String token = exchange.getIn().getHeader("token", String.class);
                    String query = exchange.getIn().getHeader("query", String.class);

                    exchange.getIn().setHeader("Authorization", "Bearer " + token);
                    exchange.getIn().setHeader("Accept", "application/vnd.github+json");

                    if (query != null && !query.isBlank()) {
                        java.net.URI uri = java.net.URI.create(apiUrl);
                        String apiBase = uri.getScheme() + "://" + uri.getAuthority();
                        String ownerRepo = GitHubUrlProcessor.extractOwnerRepo(uri.getPath());
                        String searchUrl = apiBase + "/search/issues?q="
                                + java.net.URLEncoder.encode("repo:" + ownerRepo + " " + query, java.nio.charset.StandardCharsets.UTF_8)
                                + "&per_page=1";
                        exchange.getIn().setHeader("CamelHttpUrl", searchUrl);
                    } else {
                        exchange.getIn().setHeader("CamelHttpUrl", apiUrl + "/issues?state=all&per_page=1");
                    }
                })
                .to("direct:http-get");
    }
}
