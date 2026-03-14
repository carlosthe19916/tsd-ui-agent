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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "project")
public class ProjectEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "project_sequence")
    public Long id;

    @NotNull
    @Column(name = "name")
    public String name;

    @NotNull
    @Column(name = "api_url")
    public String apiUrl;

    @Column(name = "query")
    public String query;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    public SourceType type;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id", nullable = false)
    public CredentialEntity credential;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    public SyncStatus syncStatus = SyncStatus.NOT_SYNCHRONIZED;

    @Column(name = "last_sync_at")
    public Instant lastSyncAt;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectEntity that = (ProjectEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
