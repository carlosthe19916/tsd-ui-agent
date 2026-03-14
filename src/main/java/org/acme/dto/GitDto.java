package org.acme.dto;

import jakarta.validation.constraints.NotNull;

public class GitDto {

    public Long id;

    @NotNull
    public String url;
}
