package com.kopite.devspace.link.domain;

import lombok.Getter;

@Getter
public class LinkConflictException extends RuntimeException {
    private final String code;
    public LinkConflictException(String code) {
        super(code);
        this.code = code;
    }
}
