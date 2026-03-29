package org.acme.resources;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;

public class SPARoutingFilter {

    @RouteFilter(400)
    void spaRouting(RoutingContext rc) {
        String path = rc.normalizedPath();

        if (path.equals("/")
                || path.startsWith("/api")
                || path.startsWith("/ws/")
                || path.startsWith("/q/")
                || hasFileExtension(path)) {
            rc.next();
            return;
        }

        rc.reroute("/index.html");
    }

    private boolean hasFileExtension(String path) {
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.contains(".");
    }
}
