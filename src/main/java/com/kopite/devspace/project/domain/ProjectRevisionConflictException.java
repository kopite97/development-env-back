package com.kopite.devspace.project.domain;

public class ProjectRevisionConflictException extends RuntimeException {
    public ProjectRevisionConflictException() {
        super("Project revision no longer matches; reload before updating");
    }
}
