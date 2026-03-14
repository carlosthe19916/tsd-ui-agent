package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@ApplicationScoped
public class JiraUrlProcessor implements Processor {

    static final int MAX_RESULTS = 50;

    @Override
    public void process(Exchange exchange) {
        String url = exchange.getIn().getHeader("apiUrl", String.class);
        String token = exchange.getIn().getHeader("token", String.class);
        String query = exchange.getIn().getHeader("query", String.class);

        String jql = (query != null && !query.isBlank()) ? query : "project is not EMPTY order by updated DESC";
        exchange.getIn().setHeader("Authorization", jiraAuth(token));
        exchange.getIn().setHeader("Accept", "application/json");
        exchange.setProperty("baseUrl", url);
        exchange.setProperty("jql", jql);
        exchange.setProperty("startAt", 0);

        exchange.getIn().setHeader("CamelHttpUrl", url + "/rest/api/3/search/jql");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "jql="
                + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&maxResults=" + MAX_RESULTS);
    }

    public static String jiraAuth(String token) {
        if (token.contains(":")) {
            return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        }
        return "Bearer " + token;
    }
}
