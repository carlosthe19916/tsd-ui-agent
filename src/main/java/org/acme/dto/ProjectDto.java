package org.acme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.SourceType;

public class ProjectDto {

    public Long id;

    @NotNull
    public String name;

    public String description;

    @NotNull
    public String url;

    public String query;

    @NotNull
    public SourceType type;

    @NotNull
    @Valid
    public GitDto git;

    @NotNull
    @Valid
    public CredentialDto credential;
}
