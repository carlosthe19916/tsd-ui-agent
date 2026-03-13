package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.ContextType;

public class TaskContextDto {

    public Long id;

    @NotNull
    public String name;

    public String description;

    @NotNull
    public ContextType type;

    public String content;

    public String repositoryUrl;

    public String branch;
}
