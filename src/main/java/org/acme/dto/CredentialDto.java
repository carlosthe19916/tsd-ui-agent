package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.SourceType;

public class CredentialDto {

    public Long id;

    @NotNull
    public String name;

    @NotNull
    public SourceType type;

    @NotNull
    public String token;

    public String username;
}
