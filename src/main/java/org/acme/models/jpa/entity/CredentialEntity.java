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
@Table(name = "credential")
public class CredentialEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "credential_sequence")
    public Long id;

    @NotNull
    @Column(name = "name")
    public String name;

    @NotNull
    @Column(name = "token")
    public String token;

    @Version
    public int version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CredentialEntity that = (CredentialEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
