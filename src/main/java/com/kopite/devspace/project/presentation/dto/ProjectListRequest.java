package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.application.query.ProjectListFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ProjectListRequest(String category,
                                 @Pattern(regexp = "active|archived|all") String status,
                                 String query, @Min(1) @Max(100) int limit, String cursor) {
    public ProjectListFilter filter() { return new ProjectListFilter(category, status, query, limit); }
}
