package org.acme.services.sync.camel;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.services.sync.SyncException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HttpRoutes extends RouteBuilder {

    @ConfigProperty(name = "tsd-agent.http.trust-all-ssl", defaultValue = "false")
    boolean trustAllSsl;

    @Override
    public void configure() {
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String message = "HTTP request failed: " + cause.getMessage();
                    if (cause instanceof HttpOperationFailedException httpEx) {
                        String body = httpEx.getResponseBody();
                        if (body != null && !body.isBlank()) {
                            message = "HTTP request failed (status " + httpEx.getStatusCode() + "): " + body;
                        }
                    }
                    throw new SyncException(message, cause);
                });

        String sslParam = trustAllSsl ? "&sslContextParameters=#trustAllSsl" : "";

        from("direct:http-get")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader(Exchange.HTTP_URI, header(Exchange.HTTP_URL))
                .toD("${header.CamelHttpUrl}?throwExceptionOnFailure=true" + sslParam);

        from("direct:http-post")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.HTTP_URI, header(Exchange.HTTP_URL))
                .toD("${header.CamelHttpUrl}?throwExceptionOnFailure=true" + sslParam);
    }
}
