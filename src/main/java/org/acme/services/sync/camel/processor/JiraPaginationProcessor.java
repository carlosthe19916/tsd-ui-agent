package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.acme.services.sync.camel.processor.JiraUrlProcessor.MAX_RESULTS;

@ApplicationScoped
public class JiraPaginationProcessor implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        JiraPageResponse response = exchange.getIn().getBody(JiraPageResponse.class);
        List<JiraIssue> allIssues = exchange.getProperty("allIssues", List.class);

        if (response.issues() != null) {
            allIssues.addAll(response.issues());
        }

        boolean hasMore = !response.isLast();
        exchange.setProperty("hasMorePages", hasMore);

        if (hasMore) {
            String nextPageToken = response.nextPageToken();
            String baseUrl = exchange.getProperty("baseUrl", String.class);
            String jql = exchange.getProperty("jql", String.class);
            exchange.getIn().setHeader("CamelHttpUrl", baseUrl + "/rest/api/3/search/jql");
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, "jql="
                    + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                    + "&maxResults=" + MAX_RESULTS
                    + "&nextPageToken=" + nextPageToken
                    + "&fields=" + JiraUrlProcessor.FIELDS);
        }
    }
}
