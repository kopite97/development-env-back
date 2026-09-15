package com.kopite.devspace.projectcategory.domain;

import lombok.Getter;

@Getter
public class CategoryValidationException extends RuntimeException {
    private final String field;
    public CategoryValidationException(String field, String message) { super(message); this.field = field; }
}
