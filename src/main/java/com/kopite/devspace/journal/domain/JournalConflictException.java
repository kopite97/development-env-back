package com.kopite.devspace.journal.domain;

import lombok.Getter;

@Getter
public class JournalConflictException extends RuntimeException {
    private final String code;
    public JournalConflictException(String code) {
        super(code);
        this.code = code;
    }
}
