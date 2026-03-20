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
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "plan")
public class PlanEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "plan_sequence")
    public Long id;

    @Column(name = "execution_plan", columnDefinition = "TEXT")
    public String plan;

    @Column(name = "requirement", columnDefinition = "TEXT")
    public String requirement;

    @Column(name = "is_requirement_in_progress")
    public boolean isRequirementInProgress = false;

    @Column(name = "requirement_error", columnDefinition = "TEXT")
    public String requirementError;

    @Column(name = "is_execution_plan_in_progress")
    public boolean isExecutionPlanInProgress = false;

    @Column(name = "execution_plan_error", columnDefinition = "TEXT")
    public String executionPlanError;

    @Column(name = "execution_plan_completed_at")
    public Instant executionPlanCompletedAt;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_id")
    public GitEntity git;

    @Column(name = "workspace_id")
    public String workspaceId;

    @Column(name = "claude_session_id")
    public String claudeSessionId;

    @Column(name = "is_plan_generation_in_progress")
    public boolean isPlanGenerationInProgress = false;

    @Column(name = "plan_generation_error", columnDefinition = "TEXT")
    public String planGenerationError;

    @Column(name = "is_change_request_in_progress")
    public boolean isChangeRequestInProgress = false;

    @Column(name = "change_request_error", columnDefinition = "TEXT")
    public String changeRequestError;

    @Column(name = "change_request_url")
    public String changeRequestUrl;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanEntity that = (PlanEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
