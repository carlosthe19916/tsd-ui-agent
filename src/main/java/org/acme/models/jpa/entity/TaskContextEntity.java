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

import java.util.Objects;

@Entity
@Table(name = "task_context")
public class TaskContextEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "task_context_sequence")
    public Long id;

    @NotNull
    @Column(name = "name")
    public String name;

    @Column(name = "description")
    public String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    public ContextType type;

    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    @Column(name = "repository_url")
    public String repositoryUrl;

    @Column(name = "branch")
    public String branch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    public TaskEntity task;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskContextEntity that = (TaskContextEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
