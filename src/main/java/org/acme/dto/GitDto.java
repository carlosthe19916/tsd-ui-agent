package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.acme.models.jpa.entity.GitVendorType;

public class GitDto {

    public Long id;

    @NotNull
    @Pattern(regexp = "^https://.*", message = "Must be an HTTPS Git URL")
    public String url;

    public String branch;

    @Pattern(regexp = "^https://.*", message = "Must be an HTTPS Git URL")
    public String forkUrl;

    public GitVendorType vendorType;

    public CredentialDto credential;
}
