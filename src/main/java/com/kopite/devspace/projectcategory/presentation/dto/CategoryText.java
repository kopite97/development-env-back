package com.kopite.devspace.projectcategory.presentation.dto;
import com.kopite.devspace.projectcategory.domain.*;
import jakarta.validation.*;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT}) @Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=CategoryText.Validator.class)
public @interface CategoryText {
    String message() default "must be a nonblank valid Unicode name of at most 100 UTF-16 units after trim and NFC";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<CategoryText,String> {
        public boolean isValid(String value,ConstraintValidatorContext context) {
            try { new CategoryName(value);return true; } catch(CategoryValidationException ex) { return false; }
        }
    }
}
