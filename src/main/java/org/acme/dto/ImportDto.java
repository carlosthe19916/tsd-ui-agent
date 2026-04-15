package org.acme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.GitVendorType;
import org.acme.models.jpa.entity.SourceType;

import java.util.List;

public class ImportDto {

    @Valid
    public List<CredentialImport> credentials;

    @Valid
    public List<GitImport> gits;

    @Valid
    public List<ProjectImport> projects;

    public static class CredentialImport {
        @NotNull
        public String name;

        @NotNull
        public String token;
    }

    public static class GitImport {
        @NotNull
        public String url;

        public String branch;

        public String forkUrl;

        public GitVendorType vendorType;

        @NotNull
        public String credential;
    }

    public static class ProjectImport {
        @NotNull
        public String name;

        @NotNull
        public SourceType type;

        @NotNull
        public String apiUrl;

        @NotNull
        public String credential;

        public String query;

        public boolean sync;

        @Valid
        public List<GitMappingImport> gitMappings;
    }

    public static class GitMappingImport {
        @NotNull
        public String gitUrl;

        @NotNull
        public String space;

        public List<String> labels;
    }
}
