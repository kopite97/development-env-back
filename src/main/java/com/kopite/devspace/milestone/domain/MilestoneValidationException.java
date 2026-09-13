package com.kopite.devspace.milestone.domain;

import lombok.Getter;

@Getter
public class MilestoneValidationException extends IllegalArgumentException {
    private final String field;
    public MilestoneValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
