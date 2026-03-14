package org.acme.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Entity
@Table(name = "git")
public class GitEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "git_sequence")
    public Long id;

    @NotNull
    @Column(name = "url")
    public String url;

    @Column(name = "branch")
    public String branch;

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
