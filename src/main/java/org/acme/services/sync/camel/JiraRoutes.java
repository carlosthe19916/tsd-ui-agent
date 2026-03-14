package org.acme.services.sync.camel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.services.sync.SyncException;
import org.acme.services.sync.camel.processor.JiraIssueMapper;
import org.acme.services.sync.camel.processor.JiraPageResponse;
import org.acme.services.sync.camel.processor.JiraPaginationProcessor;
import org.acme.services.sync.camel.processor.JiraUrlProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import java.util.ArrayList;

@ApplicationScoped
public class JiraRoutes extends RouteBuilder {

    @Inject
    JiraUrlProcessor urlProcessor;

    @Inject
    JiraPaginationProcessor paginationProcessor;

    @Inject
    JiraIssueMapper issueMapper;

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    throw new SyncException("Jira sync failed: " + cause.getMessage(), cause);
                });

        from("direct:jira-fetch-issues")
                .process(urlProcessor)
                .setProperty("allIssues", ArrayList::new)
                .setProperty("hasMorePages", constant(true))
                .loopDoWhile(exchangeProperty("hasMorePages"))
                    .to("direct:http-get")
                    .unmarshal().json(JiraPageResponse.class)
                    .process(paginationProcessor)
                .end()
                .setBody(exchangeProperty("allIssues"))
                .process(issueMapper);

        from("direct:jira-test-connection")
                .process(exchange -> {
                    String url = exchange.getIn().getHeader("apiUrl", String.class);
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }
                    String token = exchange.getIn().getHeader("token", String.class);
                    exchange.getIn().setHeader("CamelHttpUrl", url + "/rest/api/3/myself");
                    exchange.getIn().setHeader("Authorization", JiraUrlProcessor.basicAuth(token));
                    exchange.getIn().setHeader("Accept", "application/json");
                })
                .to("direct:http-get");

        from("direct:jira-test-query")
                .process(exchange -> {
                    String url = exchange.getIn().getHeader("apiUrl", String.class);
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }
                    String token = exchange.getIn().getHeader("token", String.class);
                    String query = exchange.getIn().getHeader("query", String.class);
                    String jql = (query != null && !query.isBlank()) ? query : "project is not EMPTY order by updated DESC";

                    exchange.getIn().setHeader("CamelHttpUrl", url + "/rest/api/3/search/jql");
                    exchange.getIn().setHeader(Exchange.HTTP_QUERY, "jql="
                            + java.net.URLEncoder.encode(jql, java.nio.charset.StandardCharsets.UTF_8)
                            + "&maxResults=1");
                    exchange.getIn().setHeader("Authorization", JiraUrlProcessor.basicAuth(token));
                    exchange.getIn().setHeader("Accept", "application/json");
                })
                .to("direct:http-get");
    }
}
