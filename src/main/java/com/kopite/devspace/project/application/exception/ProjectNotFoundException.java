package com.kopite.devspace.project.application.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException() { super("Resource not found"); }
}
