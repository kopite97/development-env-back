package com.kopite.devspace.projectcategory.domain;

import lombok.Getter;

@Getter
public class CategoryConflictException extends RuntimeException {
    private final String code;
    public CategoryConflictException(String code) { super(code); this.code = code; }
}
