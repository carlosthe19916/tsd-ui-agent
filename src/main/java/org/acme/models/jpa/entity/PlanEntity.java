package org.acme.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "plan")
public class PlanEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "plan_sequence")
    public Long id;

    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    public PlanStatus status = PlanStatus.IN_PROGRESS;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    public PlanType type;

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
        PlanEntity that = (PlanEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
