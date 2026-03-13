package org.acme.models.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class GitEntity {

    @NotNull
    @Column(name = "git_url")
    public String url;

    @Column(name = "git_branch")
    public String branch;
}
