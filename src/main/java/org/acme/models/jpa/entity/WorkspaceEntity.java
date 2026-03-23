package org.acme.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "workspace_sequence")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_id")
    public GitEntity git;

    @Column(name = "is_provisioning_in_progress")
    public boolean isProvisioningInProgress;

    @Column(name = "provisioning_error", columnDefinition = "TEXT")
    public String provisioningError;

    // if filesystem then the path to the git worktree, if container then container id
    @Column(name = "workspace_id")
    public String workspaceId;

    @Column(name = "claude_session_id")
    public String claudeSessionId;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceEntity that = (WorkspaceEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
