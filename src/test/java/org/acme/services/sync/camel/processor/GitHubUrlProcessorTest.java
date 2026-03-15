package org.acme.services.sync.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubUrlProcessorTest {

    private final GitHubUrlProcessor processor = new GitHubUrlProcessor();

    @Test
    void searchPathSeparatesQueryFromUrl() {
        Exchange exchange = newExchange();
        exchange.getIn().setHeader("apiUrl", "https://api.github.com/repos/owner/repo");
        exchange.getIn().setHeader("token", "test-token");
        exchange.getIn().setHeader("query", "is:issue");

        processor.process(exchange);

        assertEquals("https://api.github.com/search/issues",
                exchange.getIn().getHeader("CamelHttpUrl"));
        String httpQuery = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        assertTrue(httpQuery.startsWith("q="), "HTTP_QUERY should start with q=");
        assertTrue(httpQuery.contains("repo%3Aowner%2Frepo"), "HTTP_QUERY should contain encoded repo");
        assertTrue(httpQuery.contains("is%3Aissue"), "HTTP_QUERY should contain encoded query");
        assertTrue(exchange.getProperty("isSearch", Boolean.class));
    }

    @Test
    void nonSearchPathSeparatesQueryFromUrl() {
        Exchange exchange = newExchange();
        exchange.getIn().setHeader("apiUrl", "https://api.github.com/repos/owner/repo");
        exchange.getIn().setHeader("token", "test-token");

        processor.process(exchange);

        assertEquals("https://api.github.com/repos/owner/repo/issues",
                exchange.getIn().getHeader("CamelHttpUrl"));
        assertEquals("state=all&per_page=100&page=1",
                exchange.getIn().getHeader(Exchange.HTTP_QUERY));
    }

    @Test
    void urlContainsNoQueryString() {
        Exchange exchange = newExchange();
        exchange.getIn().setHeader("apiUrl", "https://api.github.com/repos/owner/repo");
        exchange.getIn().setHeader("token", "test-token");
        exchange.getIn().setHeader("query", "is:issue label:bug");

        processor.process(exchange);

        String url = exchange.getIn().getHeader("CamelHttpUrl", String.class);
        assertNull(url == null ? null : (url.contains("?") ? url : null),
                "CamelHttpUrl must not contain query parameters");
    }

    private Exchange newExchange() {
        return new DefaultExchange(new DefaultCamelContext());
    }
}
