package com.kopite.devspace.milestone.presentation.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MilestoneText.Validator.class)
public @interface MilestoneText {
    String message() default "has an invalid UTF-16 length or is blank";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int max();
    boolean required() default false;
    boolean trim() default false;

    class Validator implements ConstraintValidator<MilestoneText, String> {
        private MilestoneText rule;
        @Override public void initialize(MilestoneText rule) { this.rule = rule; }
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return !rule.required();
            String normalized = rule.trim() ? value.trim() : value;
            return normalized.length() <= rule.max() && (!rule.required() || !normalized.isBlank());
        }
    }
}
