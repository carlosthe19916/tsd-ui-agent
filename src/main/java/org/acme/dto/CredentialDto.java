package org.acme.dto;

import jakarta.validation.constraints.NotNull;

public class CredentialDto {

    public Long id;

    @NotNull
    public String name;

    public String token;
}
