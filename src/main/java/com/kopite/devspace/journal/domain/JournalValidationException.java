package com.kopite.devspace.journal.domain;

import lombok.Getter;

@Getter
public class JournalValidationException extends IllegalArgumentException {
    private final String field;
    public JournalValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
