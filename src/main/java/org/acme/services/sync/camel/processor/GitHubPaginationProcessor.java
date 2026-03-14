package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GitHubPaginationProcessor implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        List<GitHubIssue> allIssues = exchange.getProperty("allIssues", List.class);
        boolean isSearch = exchange.getProperty("isSearch", false, Boolean.class);

        if (isSearch) {
            GitHubSearchResult result = exchange.getIn().getBody(GitHubSearchResult.class);
            if (result.items() != null) {
                allIssues.addAll(result.items());
            }
            exchange.setProperty("hasMorePages", false);
        } else {
            GitHubIssue[] page = exchange.getIn().getBody(GitHubIssue[].class);
            Collections.addAll(allIssues, page);

            boolean hasMore = page.length >= 100;
            exchange.setProperty("hasMorePages", hasMore);

            if (hasMore) {
                int pageNumber = exchange.getProperty("pageNumber", Integer.class) + 1;
                exchange.setProperty("pageNumber", pageNumber);
                String apiUrl = exchange.getProperty("apiUrl", String.class);
                exchange.getIn().setHeader("CamelHttpUrl", apiUrl
                        + "/issues?state=all&per_page=100&page=" + pageNumber);
            }
        }
    }
}
