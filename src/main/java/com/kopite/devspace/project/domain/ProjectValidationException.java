package com.kopite.devspace.project.domain;

import lombok.Getter;

@Getter
public class ProjectValidationException extends IllegalArgumentException {
    private final String field;

    public ProjectValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
