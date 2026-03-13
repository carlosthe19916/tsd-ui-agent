package org.acme.services.sync;

import org.acme.models.jpa.entity.ProjectEntity;
import org.acme.models.jpa.entity.SourceType;

import java.util.List;

public interface IssueFetcher {

    SourceType getType();

    List<ExternalIssue> fetchIssues(ProjectEntity project);
}
