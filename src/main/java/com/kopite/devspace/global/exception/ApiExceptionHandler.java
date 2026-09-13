package com.kopite.devspace.global.exception;
import com.kopite.devspace.project.application.exception.InvalidProjectCursorException;
import com.kopite.devspace.project.application.exception.ProjectIdempotencyConflictException;
import com.kopite.devspace.project.application.exception.ProjectNotFoundException;

import com.kopite.devspace.global.response.ApiError;
import com.kopite.devspace.project.domain.ProjectRevisionConflictException;
import com.kopite.devspace.project.domain.ProjectValidationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.kopite.devspace.link.domain.LinkValidationException.class)
    ResponseEntity<ApiError> invalidLink(com.kopite.devspace.link.domain.LinkValidationException exception) {
        return error(400,"VALIDATION_ERROR","Request validation failed",Map.of(exception.getField(),exception.getMessage()));
    }
    @ExceptionHandler(com.kopite.devspace.link.application.exception.LinkNotFoundException.class)
    ResponseEntity<ApiError> linkNotFound() { return notFound(); }
    @ExceptionHandler(com.kopite.devspace.link.domain.LinkConflictException.class)
    ResponseEntity<ApiError> linkConflict(com.kopite.devspace.link.domain.LinkConflictException exception) {
        return error(409,exception.getCode(),"Link operation conflicts with current state",Map.of());
    }
    @ExceptionHandler(com.kopite.devspace.link.application.exception.LinkQuotaException.class)
    ResponseEntity<ApiError> linkQuota(com.kopite.devspace.link.application.exception.LinkQuotaException exception) { return error(429,"QUOTA_EXCEEDED",exception.getMessage(),Map.of()); }
    @ExceptionHandler(com.kopite.devspace.milestone.domain.MilestoneValidationException.class)
    ResponseEntity<ApiError> invalidMilestone(com.kopite.devspace.milestone.domain.MilestoneValidationException exception) {
        return error(400,"VALIDATION_ERROR","Request validation failed",Map.of(exception.getField(),exception.getMessage()));
    }
    @ExceptionHandler(com.kopite.devspace.milestone.application.exception.MilestoneNotFoundException.class)
    ResponseEntity<ApiError> milestoneNotFound() { return notFound(); }
    @ExceptionHandler(com.kopite.devspace.milestone.domain.MilestoneConflictException.class)
    ResponseEntity<ApiError> milestoneConflict(com.kopite.devspace.milestone.domain.MilestoneConflictException exception) {
        return error(409,exception.getCode(),"Milestone operation conflicts with current state",Map.of());
    }
    @ExceptionHandler(com.kopite.devspace.journal.domain.JournalValidationException.class)
    ResponseEntity<ApiError> invalidJournal(com.kopite.devspace.journal.domain.JournalValidationException exception) {
        return error(400,"VALIDATION_ERROR","Request validation failed",Map.of(exception.getField(),exception.getMessage()));
    }
    @ExceptionHandler(com.kopite.devspace.journal.application.exception.JournalNotFoundException.class)
    ResponseEntity<ApiError> journalNotFound() { return notFound(); }
    @ExceptionHandler(com.kopite.devspace.journal.domain.JournalConflictException.class)
    ResponseEntity<ApiError> journalConflict(com.kopite.devspace.journal.domain.JournalConflictException exception) {
        return error(409,exception.getCode(),"Journal operation conflicts with current state",Map.of());
    }
    @ExceptionHandler(com.kopite.devspace.milestone.application.exception.InvalidMilestoneCursorException.class)
    ResponseEntity<ApiError> milestoneCursor() { return cursor(); }
    @ExceptionHandler(com.kopite.devspace.journal.application.exception.InvalidJournalCursorException.class)
    ResponseEntity<ApiError> journalCursor() { return cursor(); }
    @ExceptionHandler(com.kopite.devspace.task.application.exception.InvalidTaskCursorException.class)
    ResponseEntity<ApiError> taskCursor() { return cursor(); }
    @ExceptionHandler(com.kopite.devspace.task.domain.TaskValidationException.class)
    ResponseEntity<ApiError> invalidTask(com.kopite.devspace.task.domain.TaskValidationException exception) {
        return error(400, "VALIDATION_ERROR", "Request validation failed", Map.of(exception.getField(), exception.getMessage()));
    }

    @ExceptionHandler(com.kopite.devspace.task.application.exception.TaskNotFoundException.class)
    ResponseEntity<ApiError> taskNotFound() { return notFound(); }

    @ExceptionHandler(com.kopite.devspace.task.domain.TaskConflictException.class)
    ResponseEntity<ApiError> taskConflict(com.kopite.devspace.task.domain.TaskConflictException exception) {
        return error(409, exception.getCode(), "Task operation conflicts with current state", Map.of());
    }
    @ExceptionHandler(ProjectValidationException.class)
    ResponseEntity<ApiError> invalid(ProjectValidationException exception) {
        return error(400, "VALIDATION_ERROR", "Request validation failed", Map.of(exception.getField(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidFields(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(400, "VALIDATION_ERROR", "Request validation failed", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if(cause instanceof com.kopite.devspace.link.domain.LinkValidationException validation) return invalidLink(validation);
            if(cause instanceof com.kopite.devspace.milestone.domain.MilestoneValidationException validation) return invalidMilestone(validation);
            if(cause instanceof com.kopite.devspace.journal.domain.JournalValidationException validation) return invalidJournal(validation);
            if (cause instanceof com.kopite.devspace.task.domain.TaskValidationException validation) return invalidTask(validation);
            if (cause instanceof ProjectValidationException validation) return invalid(validation);
        }
        return error(400, "VALIDATION_ERROR", "Malformed request body", Map.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> invalidParameter(Exception exception) {
        return error(400, "VALIDATION_ERROR", "Invalid or missing request parameter", Map.of());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiError> notFound() { return error(404, "RESOURCE_NOT_FOUND", "Resource not found", Map.of()); }

    @ExceptionHandler(InvalidProjectCursorException.class)
    ResponseEntity<ApiError> cursor() { return error(400, "INVALID_CURSOR", "Cursor is invalid for this request", Map.of()); }

    @ExceptionHandler(ProjectRevisionConflictException.class)
    ResponseEntity<ApiError> revision() { return error(409, "REVISION_CONFLICT", "Reload the resource before updating", Map.of()); }

    @ExceptionHandler(ProjectIdempotencyConflictException.class)
    ResponseEntity<ApiError> idempotency() { return error(409, "IDEMPOTENCY_KEY_REUSED", "Key was already used with another request", Map.of()); }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiError> routeNotFound() { return notFound(); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method() { return error(405, "METHOD_NOT_ALLOWED", "HTTP method is not supported", Map.of()); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType() { return error(415, "UNSUPPORTED_MEDIA_TYPE", "Content type is not supported", Map.of()); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected() { return error(500, "INTERNAL_ERROR", "Request could not be completed", Map.of()); }

    private ResponseEntity<ApiError> error(int status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiError.of(code, message, fields));
    }
}
