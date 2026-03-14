package org.acme.services.sync.camel.processor;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class GitHubUrlProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        String apiUrl = exchange.getIn().getHeader("apiUrl", String.class);
        String token = exchange.getIn().getHeader("token", String.class);
        String query = exchange.getIn().getHeader("query", String.class);

        exchange.getIn().setHeader("Authorization", "Bearer " + token);
        exchange.getIn().setHeader("Accept", "application/vnd.github+json");

        boolean isSearch = query != null && !query.isBlank();
        exchange.setProperty("isSearch", isSearch);
        exchange.setProperty("apiUrl", apiUrl);

        if (isSearch) {
            URI uri = URI.create(apiUrl);
            String apiBase = uri.getScheme() + "://" + uri.getAuthority();
            String ownerRepo = extractOwnerRepo(uri.getPath());
            exchange.setProperty("ownerRepo", ownerRepo);
            String searchUrl = apiBase + "/search/issues?q=" + URLEncoder.encode("repo:" + ownerRepo + " " + query, StandardCharsets.UTF_8);
            exchange.getIn().setHeader("CamelHttpUrl", searchUrl);
        } else {
            exchange.setProperty("pageNumber", 1);
            exchange.getIn().setHeader("CamelHttpUrl", apiUrl + "/issues?state=all&per_page=100&page=1");
        }
    }

    public static String extractOwnerRepo(String path) {
        // path is like /repos/owner/repo or /repos/owner/repo/
        String after = path.replaceFirst("^/repos/", "");
        after = after.replaceFirst("/$", "");
        return after;
    }
}
