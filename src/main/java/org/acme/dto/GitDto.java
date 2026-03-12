package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.GitPlatform;

public class GitDto {

    public Long id;

    @NotNull
    public String name;

    @NotNull
    public String url;

    @NotNull
    public GitPlatform platform;
}
