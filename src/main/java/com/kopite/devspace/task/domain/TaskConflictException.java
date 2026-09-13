package com.kopite.devspace.task.domain;

import lombok.Getter;

@Getter
public class TaskConflictException extends RuntimeException {
    private final String code;
    public TaskConflictException(String code) {
        super(code);
        this.code = code;
    }
}
