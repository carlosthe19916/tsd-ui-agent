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

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "task", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"external_id", "project_id"})
})
public class TaskEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "task_sequence")
    public Long id;

    @NotNull
    @Column(name = "external_id")
    public String externalId;

    @Column(name = "url")
    public String url;

    @NotNull
    @Column(name = "title")
    public String title;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    public TaskStatus status;

    @Column(name = "assignee")
    public String assignee;

    @Column(name = "labels")
    public String labels;

    @Column(name = "priority")
    public String priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    public SourceType type;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    public ProjectEntity project;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskEntity that = (TaskEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
