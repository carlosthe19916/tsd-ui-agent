package org.acme.dto;

import jakarta.validation.constraints.NotNull;

public class GitDto {

    @NotNull
    public String url;

    public String branch;
}
