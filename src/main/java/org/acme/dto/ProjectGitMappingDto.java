package org.acme.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ProjectGitMappingDto {

    public Long id;

    public Long projectId;

    @NotNull
    public Long gitId;

    @NotNull
    public String space;

    public List<String> labels;
}
