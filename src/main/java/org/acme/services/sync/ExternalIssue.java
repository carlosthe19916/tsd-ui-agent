package org.acme.services.sync;

import java.time.Instant;

public class ExternalIssue {

    public String externalId;
    public String url;
    public String title;
    public String description;
    public String externalStatus;

    public Instant createdAt;
    public Instant updatedAt;
}
