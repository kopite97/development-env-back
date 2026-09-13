package com.kopite.devspace.link.domain;

import lombok.Getter;

@Getter
public class LinkValidationException extends IllegalArgumentException {
    private final String field;
    public LinkValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
