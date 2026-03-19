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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Entity
@Table(name = "project_git_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "git_id", "space"}))
public class ProjectGitMappingEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "project_git_mapping_sequence")
    public Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    public ProjectEntity project;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "git_id", nullable = false)
    public GitEntity git;

    @NotNull
    @Column(name = "space")
    public String space;

    @Column(name = "labels")
    public String labels;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectGitMappingEntity that = (ProjectGitMappingEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
