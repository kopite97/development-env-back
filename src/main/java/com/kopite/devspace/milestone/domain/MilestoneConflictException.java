package com.kopite.devspace.milestone.domain;

import lombok.Getter;

@Getter
public class MilestoneConflictException extends RuntimeException {
    private final String code;
    public MilestoneConflictException(String code) {
        super(code);
        this.code = code;
    }
}
