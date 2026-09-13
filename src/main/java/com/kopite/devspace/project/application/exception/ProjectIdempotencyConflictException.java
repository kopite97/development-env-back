package com.kopite.devspace.project.application.exception;

public class ProjectIdempotencyConflictException extends RuntimeException {
    public ProjectIdempotencyConflictException() { super("Idempotency key was already used with another request"); }
}
