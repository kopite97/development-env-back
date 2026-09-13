package com.kopite.devspace.task.domain;

import lombok.Getter;

@Getter
public class TaskValidationException extends IllegalArgumentException {
    private final String field;
    public TaskValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
