package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.GitVendorType;

public class GitDto {

    public Long id;

    @NotNull
    public String url;

    public String branch;

    public String forkUrl;

    public GitVendorType vendorType;

    public CredentialDto credential;

    public String localPath;

    public boolean isCloneInProgress;

    public String cloneError;
}
