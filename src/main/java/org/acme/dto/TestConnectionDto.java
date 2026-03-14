package org.acme.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.SourceType;

public class TestConnectionDto {

    @NotNull
    public SourceType type;

    @NotBlank
    public String apiUrl;

    public String query;

    @NotNull
    public Long credentialId;
}
