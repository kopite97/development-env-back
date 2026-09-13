package com.kopite.devspace.project.application.exception;

public class InvalidProjectCursorException extends RuntimeException {
    public InvalidProjectCursorException() { super("Cursor is invalid for this request"); }
}
