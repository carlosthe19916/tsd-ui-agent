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

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "workspace_sequence")
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode")
    public ExecutionMode executionMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_id")
    public GitEntity git;

    // Only filesystem field. Where the "default" folder is cloned
    @Column(name = "local_path")
    public String localPath;

    @Column(name = "is_clone_in_progress")
    public boolean isCloneInProgress;

    @Column(name = "clone_error")
    public String cloneError;

    // if filesystem then the path to the git worktree, if container then container id
    @Column(name = "workspace_id")
    public String workspaceId;

    @Column(name = "claude_session_id")
    public String claudeSessionId;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

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
