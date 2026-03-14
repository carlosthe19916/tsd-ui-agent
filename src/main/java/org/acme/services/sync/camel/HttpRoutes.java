package org.acme.services.sync.camel;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.services.sync.SyncException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;

@ApplicationScoped
public class HttpRoutes extends RouteBuilder {

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

        from("direct:http-get")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD("${header.CamelHttpUrl}?throwExceptionOnFailure=true");

        from("direct:http-post")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .toD("${header.CamelHttpUrl}?throwExceptionOnFailure=true");
    }
}
