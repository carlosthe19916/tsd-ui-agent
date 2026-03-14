package org.acme.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.acme.dto.ProjectDto;
import org.acme.models.jpa.entity.SourceType;

public class ValidProjectQueryValidator implements ConstraintValidator<ValidProjectQuery, ProjectDto> {

    @Override
    public boolean isValid(ProjectDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }
        if (dto.type == SourceType.JIRA) {
            return dto.query != null && !dto.query.isBlank();
        }
        return true;
    }
}
