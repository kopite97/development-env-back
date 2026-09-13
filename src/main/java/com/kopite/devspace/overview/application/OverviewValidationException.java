package com.kopite.devspace.overview.application;
import lombok.Getter;
@Getter
public class OverviewValidationException extends IllegalArgumentException {
    private final String field;
    public OverviewValidationException(String field) {super("Invalid or unsupported query parameter");this.field=field;}
}
