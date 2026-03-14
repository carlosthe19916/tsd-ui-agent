package org.acme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.SourceType;
import org.acme.models.jpa.entity.SyncStatus;

import org.acme.validation.ValidProjectQuery;

import java.time.Instant;

@ValidProjectQuery
public class ProjectDto {

    public Long id;

    @NotNull
    public String name;

    @NotNull
    public String apiUrl;

    public String query;

    @NotNull
    public SourceType type;

    @NotNull
    @Valid
    public GitDto git;

    @NotNull
    public CredentialDto credential;

    public SyncStatus syncStatus;

    public Instant lastSyncAt;
}
