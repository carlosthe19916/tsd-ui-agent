package org.acme.services.sync;

import java.time.Instant;
import java.util.List;

public class ExternalIssue {

    public String externalId;
    public String url;
    public String title;
    public String description;
    public String externalStatus;
    public List<String> labels;

    public Instant createdAt;
    public Instant updatedAt;
}
