package org.acme.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Entity
@Table(name = "git", uniqueConstraints = @UniqueConstraint(columnNames = {"url", "branch"}))
public class GitEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "git_sequence")
    public Long id;

    @NotNull
    @Column(name = "url")
    public String url;

    @Column(name = "branch", nullable = false)
    public String branch = "";

    @Column(name = "fork_url")
    public String forkUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor_type")
    public GitVendorType vendorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id")
    public CredentialEntity credential;

    @Column(name = "is_provisioning_in_progress")
    public boolean isProvisioningInProgress;

    @Column(name = "provisioning_error", columnDefinition = "TEXT")
    public String provisioningError;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitEntity that = (GitEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
