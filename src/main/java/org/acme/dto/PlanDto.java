package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import org.acme.models.jpa.entity.PlanStatus;
import org.acme.models.jpa.entity.PlanType;

import java.time.Instant;

public class PlanDto {

    public Long id;

    public String content;

    @NotNull
    public PlanStatus status;

    @NotNull
    public PlanType type;

    public Instant createdAt;

    public Instant updatedAt;
}
