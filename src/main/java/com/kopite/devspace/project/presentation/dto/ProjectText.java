package com.kopite.devspace.project.presentation.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProjectText.Validator.class)
public @interface ProjectText {
    String message() default "has an invalid UTF-16 length or is blank";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int max();
    boolean required() default false;
    boolean trim() default false;

    class Validator implements ConstraintValidator<ProjectText, String> {
        private ProjectText rule;
        @Override public void initialize(ProjectText rule) { this.rule = rule; }
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return !rule.required();
            String normalized = rule.trim() ? value.trim() : value;
            return normalized.length() <= rule.max() && (!rule.required() || !normalized.isBlank());
        }
    }
}
