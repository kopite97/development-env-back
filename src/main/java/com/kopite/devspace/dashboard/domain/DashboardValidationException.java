package com.kopite.devspace.dashboard.domain;
import lombok.Getter;
@Getter
public class DashboardValidationException extends IllegalArgumentException {
    private final String field;
    public DashboardValidationException(String field, String message) { super(message); this.field=field; }
}
